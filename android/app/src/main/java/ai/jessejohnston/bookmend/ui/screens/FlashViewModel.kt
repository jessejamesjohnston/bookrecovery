package ai.jessejohnston.bookmend.ui.screens

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.usb.FlashNotificationController
import ai.jessejohnston.bookmend.usb.FlashNotificationController.Phase
import ai.jessejohnston.bookmend.ui.FlashRequest
import ai.jessejohnston.bookmend.usb.UsbFlasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Snapshot of everything [FlashScreen] needs to render.
 *
 * @param stepText The headline status line (step description, success message, or error).
 * @param progress Progress of the current phase (write or verification), 0f..1f.
 * @param isErasing True while the pre-flash (or cancel-triggered) erase pass is running.
 * @param isVerifying True while the post-write read-back verification is running.
 * @param isFinished True once the flow reached a terminal state (success or error).
 * @param hasError True if the terminal state was an error.
 * @param canRetry True when the terminal error is a verification failure — the write
 *   itself worked, so offering a full re-flash is the actionable recovery.
 */
data class FlashUiState(
    val stepText: String = "",
    val progress: Float = 0f,
    val isErasing: Boolean = false,
    val isVerifying: Boolean = false,
    val isFinished: Boolean = false,
    val hasError: Boolean = false,
    val canRetry: Boolean = false,
    /** How far the finished flash was checked; null until it finishes well. */
    val verification: UsbFlasher.Verification? = null,
    /** Rounded seconds left in the current phase, once it can be estimated. */
    val secondsLeft: Long? = null,
)

/**
 * ViewModel owning the flash execution state machine:
 *
 *   flashing -> verifying -> success / error
 *                  |             |
 *                  |             +-- skip -> success (unverified; skip != fail)
 *                  +-- cancel -> erasing (reset) -> success
 *
 * It drives [UsbFlasher] on a background dispatcher, holds the UI state across
 * configuration changes, and delegates every notification / Foreground Service
 * concern to [FlashNotificationController]. [FlashScreen] is left as a pure
 * renderer of [FlashUiState].
 *
 * Verification is default-on: official images are checked twice (the download
 * against the manifest checksum, the drive against the written bytes); local
 * images get write verification only, since there is no authority to check
 * authenticity against.
 *
 * Scoped to the flash destination's NavBackStackEntry, so the running flash
 * survives rotation/resize — and [onCleared] cancels the flasher, so leaving
 * the screen no longer orphans a write loop that keeps running blind.
 *
 * @param request The drive, the image and its catalog checksum and size.
 * @param flasher Replaceable so tests can flash a simulated drive.
 */
class FlashViewModel(
    application: Application,
    private val request: FlashRequest,
    private val flasher: UsbFlasher = UsbFlasher(
        application.getSystemService(Context.USB_SERVICE) as UsbManager,
        application
    ),
    private val notifications: FlashNotificationController = FlashNotificationController(application),
    private val clock: () -> Long = System::currentTimeMillis,
) : AndroidViewModel(application) {

    private val device = request.device
    private val eta = EtaEstimator()

    private val _uiState = MutableStateFlow(
        FlashUiState(stepText = application.getString(R.string.flash_step_starting))
    )
    val uiState: StateFlow<FlashUiState> = _uiState.asStateFlow()

    private var started = false
    private var isCancelledReset = false

    /**
     * Tracks whether the app is currently backgrounded (reported by the screen),
     * so terminal notifications are only posted when the user isn't looking.
     * Volatile because the flash pipeline reads it from a background dispatcher.
     */
    @Volatile
    private var isBackgrounded = false

    /**
     * Kicks off the flow. Idempotent: recomposition, rotation, or returning to the
     * screen must not restart a running flash.
     */
    fun start() {
        if (started) return
        started = true

        notifications.createChannel()
        // Claim foreground priority once at the beginning. This prevents
        // ForegroundServiceStartNotAllowedException if the user leaves mid-flash.
        notifications.startKeepAlive()

        flash()
    }

    /**
     * Cancels the in-flight write, then really erases the drive's partition
     * structures so the user ends on an honestly "reset" USB stick rather than
     * a half-written image behind a fake success message. The erase itself
     * starts only after the flasher reports Cancelled — the write loop must
     * release the USB interface before anyone else can claim it.
     */
    fun cancelFlashAndReset() {
        flasher.cancel()
        isCancelledReset = true
        _uiState.update { it.copy(stepText = string(R.string.flash_step_erasing), progress = 0f, isErasing = true, isVerifying = false) }
    }

    /** Ends the verification pass early. Skipping is a success, not a failure. */
    fun skipVerification() {
        flasher.skipVerification()
    }

    /**
     * Full re-flash after a verification failure: the drive contents can't be
     * trusted, so the only honest recovery is writing (and verifying) again.
     */
    fun retryFlash() {
        eta.reset()
        flasher.reset()
        _uiState.update {
            FlashUiState(stepText = string(R.string.flash_step_starting))
        }
        notifications.startKeepAlive()
        flash()
    }

    /**
     * Called by the screen whenever the app's foreground-ness changes (lifecycle
     * state or window focus). Foregrounded: the in-app UI shows progress, so the
     * notification is dismissed. Backgrounded mid-run: post one immediately so the
     * status chip appears without waiting for the next progress tick.
     */
    fun onBackgroundedChanged(backgrounded: Boolean) {
        isBackgrounded = backgrounded
        val state = _uiState.value
        if (!backgrounded) {
            notifications.dismissProgress()
        } else if (!state.isFinished && !state.hasError) {
            notifications.postProgress(state.progress, state.toPhase())
        }
    }

    /**
     * The screen was popped from the back stack. Stop the flasher cooperatively so
     * the write loop doesn't keep running against a drive nobody is watching; the
     * flasher's finally block releases the USB interface.
     */
    override fun onCleared() {
        flasher.cancel()
    }

    private fun FlashUiState.toPhase(): Phase = when {
        isErasing -> Phase.ERASING
        isVerifying -> Phase.VERIFYING
        else -> Phase.FLASHING
    }

    /**
     * The reset path after a cancelled write: really zero the partition
     * structures so nothing tries to mount the half-written image, then land
     * on the reset summary.
     */
    private fun eraseForReset() {
        viewModelScope.launch {
            val result = flasher.eraseDevice(device) { p ->
                _uiState.update { it.copy(progress = p) }
                notifications.postProgress(p, Phase.ERASING)
            }
            notifications.stopKeepAlive()
            when (result) {
                is UsbFlasher.EraseResult.Error -> finishWithError(result.message, canRetry = false)
                is UsbFlasher.EraseResult.Done -> _uiState.update {
                    it.copy(stepText = string(R.string.flash_reset_success), progress = 1f, isFinished = true, hasError = false)
                }
            }
        }
    }

    private fun string(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    private fun flash() {
        viewModelScope.launch {
            val result = flasher.flashImageToUsb(
                device = device,
                url = request.url,
                expectedDownloadDigest = request.digest,
                expectedImageSize = request.imageBytes,
                onStep = { step -> _uiState.update { it.copy(stepText = step) } },
                onProgress = { p ->
                    val left = eta.update(clock(), p)
                    _uiState.update { it.copy(progress = p, isVerifying = false, secondsLeft = left) }
                    notifications.postProgress(p, Phase.FLASHING)
                },
                onVerifyProgress = { p ->
                    if (!_uiState.value.isVerifying) eta.reset()
                    val left = eta.update(clock(), p)
                    _uiState.update { it.copy(progress = p, isVerifying = true, secondsLeft = left) }
                    notifications.postProgress(p, Phase.VERIFYING)
                }
            )

            when (result) {
                is UsbFlasher.Result.Cancelled -> {
                    if (isCancelledReset) {
                        // The write loop has released the USB interface; now the
                        // reset-erase can claim it.
                        isCancelledReset = false
                        eraseForReset()
                    } else {
                        // Cancelled because the screen was left (onCleared).
                        notifications.stopKeepAlive()
                    }
                }
                is UsbFlasher.Result.Success -> {
                    val message = when (result.verification) {
                        UsbFlasher.Verification.AUTHENTIC -> string(R.string.flash_success_verified)
                        UsbFlasher.Verification.WRITE_VERIFIED -> string(R.string.flash_success_write_verified)
                        UsbFlasher.Verification.SKIPPED -> string(R.string.flash_success)
                    }
                    _uiState.update {
                        it.copy(stepText = message, progress = 1f, isVerifying = false, isFinished = true, verification = result.verification, secondsLeft = null)
                    }
                    notifications.stopKeepAlive()
                    if (isBackgrounded) {
                        notifications.postCompletion(string(R.string.notif_success_title), message, isError = false)
                    }
                }
                is UsbFlasher.Result.WriteError -> finishWithError(result.message, canRetry = false)
                is UsbFlasher.Result.VerificationError -> finishWithError(result.message, canRetry = true)
            }
        }
    }

    private fun finishWithError(message: String, canRetry: Boolean) {
        _uiState.update {
            it.copy(
                stepText = string(R.string.flash_error, message),
                isVerifying = false,
                isFinished = true,
                hasError = true,
                canRetry = canRetry
            )
        }
        notifications.stopKeepAlive()
        if (isBackgrounded) {
            notifications.postCompletion(string(R.string.notif_error_title), message, isError = true)
        }
    }
}
