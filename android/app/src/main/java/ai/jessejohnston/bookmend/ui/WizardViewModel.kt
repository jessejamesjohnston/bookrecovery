package ai.jessejohnston.bookmend.ui

import ai.jessejohnston.bookmend.data.CatalogImage
import ai.jessejohnston.bookmend.data.CatalogResult
import ai.jessejohnston.bookmend.data.DeviceFamily
import ai.jessejohnston.bookmend.data.HardwareId
import ai.jessejohnston.bookmend.data.RecoveryRepository
import android.hardware.usb.UsbDevice
import android.os.PersistableBundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The catalog as a screen sees it. */
sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val images: List<CatalogImage>) : CatalogState
    data object Unavailable : CatalogState
}

/** What the Chromebook hardware-ID search found. */
sealed interface IdentifyResult {
    data class Match(val modelName: String) : IdentifyResult
    data class Several(val modelNames: List<String>) : IdentifyResult
    data class NoMatch(val input: String) : IdentifyResult
}

/**
 * Holds the person's choices across the wizard: the device family, the image
 * (a catalog entry or a local file) and the drive. Everything lives in
 * [SavedStateHandle], so rotation, resizing and process death keep the
 * choices. A catalog image is saved by its key and found again in the
 * catalog, which is also how a choice travels to another device with
 * Continue On ([handoffExtras] / [restoreHandoff]).
 */
class WizardViewModel(
    private val saved: SavedStateHandle,
    private val repository: RecoveryRepository = RecoveryRepository.instance,
) : ViewModel() {

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    private val _image = MutableStateFlow<CatalogImage?>(null)
    /** The chosen catalog image; null while choosing, or when a local file is used. */
    val image: StateFlow<CatalogImage?> = _image.asStateFlow()

    val family: StateFlow<DeviceFamily?> = saved.getStateFlow<String?>(KEY_FAMILY, null)
        .map { name -> name?.let(DeviceFamily::valueOf) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, saved.get<String>(KEY_FAMILY)?.let(DeviceFamily::valueOf))

    /** A `content://` image picked with the file picker, when not using the catalog. */
    val localImage: StateFlow<String?> = saved.getStateFlow(KEY_LOCAL, null)

    val drive: StateFlow<UsbDevice?> = saved.getStateFlow(KEY_DRIVE, null)

    private val _handoffRoute = MutableStateFlow<String?>(null)
    /** Where to take the person once a handed-off choice is loaded; consume with [handoffShown]. */
    val handoffRoute: StateFlow<String?> = _handoffRoute.asStateFlow()
    private var handoffPending = false

    init {
        family.value?.let { load(it) }
    }

    fun chooseFamily(family: DeviceFamily) {
        if (saved.get<String>(KEY_FAMILY) != family.name) {
            saved[KEY_IMAGE] = null
            _image.value = null
        }
        saved[KEY_FAMILY] = family.name
        saved[KEY_LOCAL] = null
        load(family)
    }

    fun retryCatalog() {
        family.value?.let { load(it) }
    }

    /** Looks up a Chromebook by the hardware ID its recovery screen shows. */
    fun identify(input: String): IdentifyResult {
        val images = (catalog.value as? CatalogState.Ready)?.images.orEmpty()
        val names = HardwareId.match(input, images)
        return when (names.size) {
            0 -> IdentifyResult.NoMatch(input.trim())
            1 -> IdentifyResult.Match(names.single())
            else -> IdentifyResult.Several(names)
        }
    }

    fun chooseImage(image: CatalogImage) {
        _image.value = image
        saved[KEY_IMAGE] = image.key
        saved[KEY_LOCAL] = null
    }

    fun chooseLocalImage(uri: String) {
        _image.value = null
        saved[KEY_IMAGE] = null
        saved[KEY_LOCAL] = uri
    }

    fun chooseDrive(device: UsbDevice) {
        saved[KEY_DRIVE] = device
    }

    /** Everything a flash needs, or null if a choice is missing. */
    fun flashRequest(): FlashRequest? {
        val device = drive.value ?: return null
        val image = image.value
        val local = localImage.value
        return when {
            image != null -> FlashRequest(device, image.url, image.digest, image.imageBytes, image.family, image.name)
            local != null -> FlashRequest(device, local, null, null, family.value, null)
            else -> null
        }
    }

    /** Starts over without forgetting the catalog. */
    fun reset() {
        saved[KEY_IMAGE] = null
        saved[KEY_LOCAL] = null
        saved[KEY_DRIVE] = null
        _image.value = null
    }

    /**
     * The choices so far, for Continue On. Only catalog choices travel: a
     * local file and a USB drive belong to this device.
     */
    fun handoffExtras(): PersistableBundle = PersistableBundle().apply {
        family.value?.let { putString(KEY_FAMILY, it.name) }
        _image.value?.let { putString(KEY_IMAGE, it.key) }
    }

    /** Applies choices made on another device. Returns false if there were none. */
    fun restoreHandoff(familyName: String?, imageKey: String?): Boolean {
        val family = familyName?.let { runCatching { DeviceFamily.valueOf(it) }.getOrNull() } ?: return false
        reset()
        saved[KEY_FAMILY] = family.name
        saved[KEY_IMAGE] = imageKey
        handoffPending = true
        load(family)
        return true
    }

    fun handoffShown() {
        _handoffRoute.value = null
    }

    private fun load(family: DeviceFamily) {
        _catalog.value = CatalogState.Loading
        viewModelScope.launch {
            _catalog.value = when (val result = repository.load(family)) {
                is CatalogResult.Loaded -> {
                    val key = saved.get<String>(KEY_IMAGE)
                    if (key != null && _image.value?.key != key) {
                        _image.update { result.images.firstOrNull { it.key == key } }
                    }
                    if (handoffPending) {
                        handoffPending = false
                        _handoffRoute.value = Route.Models.withQuery(_image.value?.name)
                    }
                    CatalogState.Ready(result.images)
                }
                CatalogResult.Unavailable -> CatalogState.Unavailable
            }
        }
    }

    companion object {
        const val KEY_FAMILY = "bookmend.family"
        const val KEY_IMAGE = "bookmend.image"
        private const val KEY_LOCAL = "bookmend.local"
        private const val KEY_DRIVE = "bookmend.drive"
    }
}

/** What the flash screen needs, gathered from the wizard. */
data class FlashRequest(
    val device: UsbDevice,
    val url: String,
    val digest: ai.jessejohnston.bookmend.usb.ExpectedDigest?,
    val imageBytes: Long?,
    val family: DeviceFamily?,
    val modelName: String?,
)
