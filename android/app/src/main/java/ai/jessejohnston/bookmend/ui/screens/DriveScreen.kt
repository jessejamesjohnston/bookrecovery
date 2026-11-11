package ai.jessejohnston.bookmend.ui.screens

import ai.jessejohnston.bookmend.R
import ai.jessejohnston.bookmend.ui.art.PhoneAndDrive
import ai.jessejohnston.bookmend.usb.DriveInspector
import ai.jessejohnston.bookmend.usb.DriveSize
import ai.jessejohnston.bookmend.usb.UsbDriveConnector
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/** A drive on screen: unchecked until permission allows reading its size. */
private sealed interface DriveCheck {
    data object NotChecked : DriveCheck
    data object Checking : DriveCheck
    data class Done(val size: DriveSize) : DriveCheck
}

/**
 * Plug in a drive, see what it is, and confirm the erase. Only USB storage is
 * listed, and the list follows plugging and unplugging live. Tapping a drive
 * asks Android for access, reads its size, and stops right there if it's too
 * small for the image. A drive that fits opens a sheet that names it and asks
 * for a deliberate hold before anything is erased.
 *
 * @param requiredBytes The image size; null when unknown (a local file, or erase only).
 * @param targetName The model the drive is for, shown in the confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DriveScreen(
    requiredBytes: Long?,
    targetName: String?,
    eraseOnly: Boolean,
    onConfirmed: (UsbDevice) -> Unit,
) {
    val context = LocalContext.current
    val usb = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    val inspector = remember { DriveInspector(UsbDriveConnector(usb, context)) }
    val scope = rememberCoroutineScope()

    fun storageDrives() = usb.deviceList.values.filter(DriveInspector::isStorage).sortedBy { it.deviceName }
    var drives by remember { mutableStateOf(storageDrives()) }
    val checks = remember { mutableStateMapOf<String, DriveCheck>() }
    var confirming by remember { mutableStateOf<UsbDevice?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var pendingPermission by remember { mutableStateOf<String?>(null) }

    fun check(device: UsbDevice) {
        checks[device.deviceName] = DriveCheck.Checking
        scope.launch {
            val size = inspector.capacity(device)
            checks[device.deviceName] = DriveCheck.Done(size)
            val fits = requiredBytes == null || size !is DriveSize.Known || size.bytes >= requiredBytes
            if (size !is DriveSize.Unusable && fits) confirming = device
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        drives = storageDrives()
                        checks.keys.retainAll(drives.map { it.deviceName }.toSet())
                        if (confirming != null && confirming !in drives) confirming = null
                    }
                    ACTION_USB_PERMISSION -> {
                        val name = pendingPermission
                        pendingPermission = null
                        val device = drives.firstOrNull { it.deviceName == name }
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                            check(device)
                        } else {
                            permissionDenied = true
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        // NOT_EXPORTED still receives system broadcasts; it only blocks other apps.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    fun select(device: UsbDevice) {
        if (usb.hasPermission(device)) {
            check(device)
        } else {
            pendingPermission = device.deviceName
            val intent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                else PendingIntent.FLAG_UPDATE_CURRENT
            )
            usb.requestPermission(device, intent)
        }
    }

    StepLayout {
        StepTitle(stringResource(if (eraseOnly) R.string.menu_erase_drive else R.string.drive_title), Modifier.padding(top = 8.dp))
        StepBody(stringResource(R.string.drive_body))
        requiredBytes?.let {
            Text(
                stringResource(R.string.drive_requirement, driveSizeFor(it)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        if (drives.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 24.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PhoneAndDrive(Modifier.fillMaxWidth().height(140.dp))
                Text(stringResource(R.string.drive_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.drive_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            drives.forEach { device ->
                DriveCard(
                    device = device,
                    check = checks[device.deviceName] ?: DriveCheck.NotChecked,
                    requiredBytes = requiredBytes,
                    onClick = { select(device) }
                )
            }
        }
    }

    confirming?.let { device ->
        val size = (checks[device.deviceName] as? DriveCheck.Done)?.size as? DriveSize.Known
        val name = driveName(device) + (size?.let { " (${formatBytes(it.bytes)})" } ?: "")
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { confirming = null }, sheetState = sheet) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.drive_confirm_title, name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }
                )
                if (targetName != null) {
                    Text(stringResource(R.string.drive_confirm_body, targetName), style = MaterialTheme.typography.bodyLarge)
                }
                HoldToConfirmButton(
                    label = stringResource(R.string.drive_confirm_hold),
                    holdingLabel = stringResource(R.string.drive_confirm_holding),
                    accessibilityLabel = stringResource(R.string.drive_confirm_a11y_action),
                    onConfirm = {
                        confirming = null
                        onConfirmed(device)
                    }
                )
                TextButton(onClick = { confirming = null }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.drive_cancel))
                }
            }
        }
    }

    if (permissionDenied) {
        AlertDialog(
            onDismissRequest = { permissionDenied = false },
            title = { Text(stringResource(R.string.permission_required_title)) },
            text = { Text(stringResource(R.string.permission_denied_usb)) },
            confirmButton = { Button(onClick = { permissionDenied = false }) { Text(stringResource(R.string.action_ok)) } }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DriveCard(device: UsbDevice, check: DriveCheck, requiredBytes: Long?, onClick: () -> Unit) {
    val size = (check as? DriveCheck.Done)?.size
    val tooSmall = size is DriveSize.Known && requiredBytes != null && size.bytes < requiredBytes
    val problem = tooSmall || size is DriveSize.Unusable
    OutlinedCard(
        onClick = onClick,
        enabled = check !is DriveCheck.Checking,
        border = BorderStroke(1.dp, if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                if (problem) Icons.Outlined.ErrorOutline else Icons.Outlined.Usb,
                contentDescription = null,
                tint = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(driveName(device), style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        check is DriveCheck.Checking -> stringResource(R.string.drive_checking)
                        tooSmall -> stringResource(R.string.drive_too_small, formatBytes((size as DriveSize.Known).bytes))
                        size is DriveSize.Known -> stringResource(R.string.drive_size, formatBytes(size.bytes))
                        size is DriveSize.Unusable -> size.message
                        else -> stringResource(R.string.drive_tap_to_check)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (check is DriveCheck.Checking) LoadingIndicator(Modifier.size(32.dp))
        }
    }
}

@Composable
private fun driveName(device: UsbDevice): String =
    listOfNotNull(device.manufacturerName?.trim(), device.productName?.trim())
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" ")
        .ifEmpty { stringResource(R.string.drive_unknown_name) }

private const val ACTION_USB_PERMISSION = "ai.jessejohnston.bookmend.USB_PERMISSION"
