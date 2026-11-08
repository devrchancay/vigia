package com.ramonchancay.vigia.feature.setup

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramonchancay.vigia.core.designsystem.permission.rememberNotificationPermissionState
import com.ramonchancay.vigia.core.designsystem.theme.VigiaTheme

internal const val SETUP_TOKEN_TAG = "setup_token"
internal const val SETUP_QR_TAG = "setup_qr"

@Composable
internal fun SetupRoute(onDone: () -> Unit, viewModel: SetupViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SetupEvent.Done -> onDone()
                SetupEvent.StopLockTask -> runCatching { activity?.stopLockTask() }
            }
        }
    }
    // Kiosk facts come from system settings the owner may have changed meanwhile.
    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(SetupAction.Refresh)
        onPauseOrDispose {}
    }
    // The server's foreground notification needs this on Android 13+; asking here explains why it appears.
    val notificationPermission = rememberNotificationPermissionState()
    LaunchedEffect(Unit) { notificationPermission.request() }
    SetupScreen(uiState = uiState, onAction = viewModel::onAction)
}

@Composable
internal fun SetupScreen(uiState: SetupUiState, onAction: (SetupAction) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(
            MaterialTheme.colorScheme.background
        ).safeDrawingPadding().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            SetupUiState.Loading -> CircularProgressIndicator()
            is SetupUiState.Ready -> SetupReady(uiState, onAction)
        }
    }
}

@Composable
private fun SetupReady(state: SetupUiState.Ready, onAction: (SetupAction) -> Unit) {
    var confirmRotation by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.pairingUri?.let { uri ->
            QrCode(content = uri, modifier = Modifier.fillMaxHeight().aspectRatio(1f).testTag(SETUP_QR_TAG))
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Text scrolls on small screens; the buttons below always stay visible.
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.setup_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Label(stringResource(R.string.setup_address))
                if (state.baseUrls.isEmpty()) {
                    Text(stringResource(R.string.setup_no_network), color = MaterialTheme.colorScheme.error)
                }
                state.baseUrls.forEach { Value(it) }
                Label(stringResource(R.string.setup_token))
                Value(state.token, modifier = Modifier.testTag(SETUP_TOKEN_TAG))
                KioskSection(state, onAction)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { onAction(SetupAction.Done) }) { Text(stringResource(R.string.setup_open_panel)) }
                OutlinedButton(onClick = { confirmRotation = true }) { Text(stringResource(R.string.setup_rotate)) }
            }
        }
    }
    if (confirmRotation) {
        AlertDialog(
            onDismissRequest = { confirmRotation = false },
            title = { Text(stringResource(R.string.setup_rotate_title)) },
            text = { Text(stringResource(R.string.setup_rotate_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRotation = false
                    onAction(SetupAction.RotateToken)
                }) { Text(stringResource(R.string.setup_rotate_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRotation = false }) { Text(stringResource(R.string.setup_cancel)) }
            }
        )
    }
}

@Composable
private fun KioskSection(state: SetupUiState.Ready, onAction: (SetupAction) -> Unit) {
    val context = LocalContext.current
    var pinFor by rememberSaveable { mutableStateOf<String?>(null) }
    Label(stringResource(R.string.kiosk_title))
    val kiosk = state.kiosk
    Text(
        stringResource(if (kiosk.isDeviceOwner) R.string.kiosk_owner_yes else R.string.kiosk_owner_no),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (!kiosk.isDefaultHome) {
        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) {
            Text(stringResource(R.string.kiosk_make_home))
        }
    } else {
        Text(stringResource(R.string.kiosk_is_home), style = MaterialTheme.typography.bodyMedium)
    }
    if (!state.pinRequired) {
        Text(
            stringResource(R.string.kiosk_no_pin),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (state.wrongPin) Text(stringResource(R.string.kiosk_wrong_pin), color = MaterialTheme.colorScheme.error)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (kiosk.isLockTaskActive) {
            OutlinedButton(onClick = {
                pinFor = EXIT
            }) { Text(stringResource(R.string.kiosk_exit)) }
        }
        if (kiosk.isDeviceOwner) {
            OutlinedButton(onClick = {
                pinFor = RELEASE
            }) { Text(stringResource(R.string.kiosk_release)) }
        }
    }
    pinFor?.let { action ->
        PinDialog(
            requirePin = state.pinRequired,
            onDismiss = { pinFor = null },
            onConfirm = { pin ->
                pinFor = null
                onAction(if (action == EXIT) SetupAction.ExitKiosk(pin) else SetupAction.ReleaseDeviceOwner(pin))
            }
        )
    }
}

private const val EXIT = "exit"
private const val RELEASE = "release"

@Composable
private fun PinDialog(requirePin: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kiosk_pin_title)) },
        text = {
            if (requirePin) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            } else {
                Text(stringResource(R.string.kiosk_no_pin))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pin) }) { Text(stringResource(R.string.kiosk_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.setup_cancel)) } }
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Value(text: String, modifier: Modifier = Modifier) {
    SelectionContainer {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            modifier = modifier
        )
    }
}

/** Dark modules on a white quiet zone, so phones scan it in both themes. */
@Composable
private fun QrCode(content: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) { PairingQr.matrix(content) }
    Canvas(modifier = modifier.background(Color.White)) {
        val cell = size.minDimension / matrix.size
        for (index in matrix.dark.indices) {
            if (matrix.dark[index]) {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset((index % matrix.size) * cell, (index / matrix.size) * cell),
                    // Slight overlap hides hairline seams between modules at fractional cell sizes.
                    size = Size(cell + 0.5f, cell + 0.5f)
                )
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun SetupPreview() {
    VigiaTheme(darkTheme = true) {
        SetupScreen(
            uiState = SetupUiState.Ready(token = "x".repeat(43), baseUrls = listOf("http://100.101.102.103:8787")),
            onAction = {}
        )
    }
}
