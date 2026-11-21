package com.rython.authenticator.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rython.authenticator.R
import com.rython.authenticator.data.Account
import com.rython.authenticator.core.TimeProvider
import com.rython.authenticator.data.Backup
import com.rython.authenticator.security.AppPasswords
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface PasswordTask {
    data object Export : PasswordTask
    data class Import(val bytes: ByteArray, val legacy: Boolean) : PasswordTask
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    accounts: List<Account>,
    lockEnabled: Boolean,
    biometricAvailable: Boolean,
    onLockChange: (Boolean) -> Unit,
    onImported: (List<Account>) -> Unit,
    onTransfer: () -> Unit,
    onClearAll: () -> Unit,
    onMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var task by remember { mutableStateOf<PasswordTask?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(AppLocale.current(context)) }
    var timeZone by remember { mutableStateOf(TimeProvider.selectedTimeZone(context)) }
    var useInternet by remember { mutableStateOf(TimeProvider.useInternetTime(context)) }
    var showZonePicker by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var changeSlot by remember { mutableStateOf(0) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingBytes
        pendingBytes = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null
                }.getOrDefault(false)
            }
            onMessage(
                context.getString(
                    if (ok) R.string.msg_backup_saved else R.string.msg_backup_save_failed
                )
            )
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                onMessage(context.getString(R.string.msg_backup_read_failed))
            } else {
                task = PasswordTask.Import(bytes, Backup.isLegacy(bytes))
            }
        }
    }

    val stamp = remember { SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.settings_language_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.settings_language_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { candidate ->
                    FilterChip(
                        selected = language == candidate,
                        onClick = {
                            if (language != candidate) {
                                AppLocale.save(context, candidate)
                                language = candidate
                                (context as? Activity)?.recreate()
                            }
                        },
                        label = { Text(candidate.nativeName) }
                    )
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.settings_time_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.settings_time_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_timezone_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (timeZone == null) stringResource(R.string.settings_timezone_device)
                        else timeZone!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { showZonePicker = true }) {
                    Text(stringResource(R.string.settings_timezone_pick))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_internet_time_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(R.string.settings_internet_time_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useInternet,
                    onCheckedChange = { v ->
                        useInternet = v
                        TimeProvider.setUseInternetTime(context, v)
                    }
                )
            }
            OutlinedButton(
                onClick = {
                    syncing = true
                    scope.launch {
                        val offset = withContext(Dispatchers.IO) { TimeProvider.syncInternetTime(context) }
                        syncing = false
                        if (offset == null) {
                            onMessage(context.getString(R.string.settings_internet_sync_fail))
                        } else {
                            useInternet = true
                            onMessage(context.getString(R.string.settings_internet_sync_ok, offset))
                        }
                    }
                },
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_internet_sync)) }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_biometric_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(
                            if (biometricAvailable) R.string.settings_biometric_on
                            else R.string.settings_biometric_unavailable
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = lockEnabled,
                    onCheckedChange = onLockChange,
                    enabled = biometricAvailable
                )
            }

            HorizontalDivider()
            Text(
                stringResource(R.string.settings_passwords_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                stringResource(R.string.settings_passwords_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { changeSlot = 1 },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_passwords_change_first)) }
            OutlinedButton(
                onClick = { changeSlot = 2 },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_passwords_change_second)) }

            HorizontalDivider()
            Text(stringResource(R.string.settings_transfer_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.settings_transfer_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onTransfer,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_transfer_button)) }

            HorizontalDivider()
            Text(stringResource(R.string.settings_backup_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.settings_backup_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { task = PasswordTask.Export },
                enabled = accounts.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_backup_create)) }
            OutlinedButton(
                onClick = { openDocument.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_backup_restore)) }

            HorizontalDivider()
            Text(stringResource(R.string.settings_about_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.settings_about_body, accounts.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()
            OutlinedButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.settings_clear_all),
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val currentTask = task
    if (currentTask != null) {
        val isExport = currentTask is PasswordTask.Export
        BackupPasswordDialog(
            title = stringResource(
                if (isExport) R.string.backup_dialog_title_export
                else R.string.backup_dialog_title_import
            ),
            confirmLabel = stringResource(
                if (isExport) R.string.backup_dialog_create else R.string.backup_dialog_restore
            ),
            requireRepeat = isExport,
            requireSecond = if (currentTask is PasswordTask.Import) !currentTask.legacy else true,
            legacyHint = currentTask is PasswordTask.Import && currentTask.legacy,
            onDismiss = { task = null },
            onConfirm = onConfirm@{ first, second ->
                task = null
                when (currentTask) {
                    PasswordTask.Export -> {
                        scope.launch {
                            val bytes = withContext(Dispatchers.Default) {
                                val valid = AppPasswords.verifyFirst(context, first) &&
                                    AppPasswords.verifySecond(context, second)
                                if (!valid) null
                                else runCatching { Backup.export(accounts, first.toCharArray(), second.toCharArray()) }
                                    .getOrNull()
                            }
                            if (bytes == null) {
                                onMessage(context.getString(R.string.msg_backup_save_failed))
                            } else {
                                pendingBytes = bytes
                                createDocument.launch("rython-backup-$stamp.rybak")
                            }
                        }
                    }

                    is PasswordTask.Import -> {
                        scope.launch {
                            val imported = withContext(Dispatchers.Default) {
                                runCatching {
                                    Backup.import(
                                        currentTask.bytes,
                                        first.toCharArray(),
                                        second.toCharArray()
                                    )
                                }
                            }
                            imported.onSuccess {
                                if (it.isEmpty()) onMessage(context.getString(R.string.msg_backup_empty))
                                else {
                                    onImported(it)
                                    onMessage(context.getString(R.string.msg_restored_count, it.size))
                                }
                            }.onFailure {
                                onMessage(context.getString(R.string.msg_restore_failed))
                            }
                        }
                    }
                }
            }
        )
    }

    if (showZonePicker) {
        AlertDialog(
            onDismissRequest = { showZonePicker = false },
            title = { Text(stringResource(R.string.settings_timezone_pick)) },
            text = {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    item {
                        TextButton(onClick = {
                            timeZone = null
                            TimeProvider.setSelectedTimeZone(context, null)
                            showZonePicker = false
                        }) {
                            Text(stringResource(R.string.settings_timezone_device))
                        }
                    }
                    items(TimeProvider.commonZoneIds()) { zone ->
                        TextButton(onClick = {
                            timeZone = zone
                            TimeProvider.setSelectedTimeZone(context, zone)
                            showZonePicker = false
                        }) {
                            Text(zone)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showZonePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (changeSlot != 0) {
        ChangePasswordsDialog(
            slot = changeSlot,
            onDismiss = { changeSlot = 0 },
            onChanged = {
                changeSlot = 0
                onMessage(context.getString(R.string.msg_passwords_changed))
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_title)) },
            text = { Text(stringResource(R.string.settings_clear_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearAll() }) {
                    Text(
                        stringResource(R.string.settings_clear_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    confirmLabel: String,
    requireRepeat: Boolean,
    requireSecond: Boolean,
    legacyHint: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var firstRepeat by remember { mutableStateOf("") }
    var secondRepeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showPasswords by remember { mutableStateOf(false) }

    val visibility = passwordTransformation(showPasswords)
    val visibilityToggle: @Composable () -> Unit = {
        PasswordVisibilityToggle(visible = showPasswords) { showPasswords = !showPasswords }
    }

    val tooShortOne = stringResource(R.string.backup_error_short, 1, AppPasswords.MIN_LENGTH)
    val tooShortTwo = stringResource(R.string.backup_error_short, 2, AppPasswords.MIN_LENGTH)
    val mismatch = stringResource(R.string.setup_mismatch)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it; error = null },
                    label = { Text(stringResource(R.string.backup_dialog_first)) },
                    singleLine = true,
                    visualTransformation = visibility,
                    trailingIcon = visibilityToggle,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (showPasswords) KeyboardType.Text else KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (requireRepeat && !showPasswords) {
                    OutlinedTextField(
                        value = firstRepeat,
                        onValueChange = { firstRepeat = it; error = null },
                        label = { Text(stringResource(R.string.setup_repeat)) },
                        singleLine = true,
                        visualTransformation = visibility,
                        trailingIcon = visibilityToggle,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (showPasswords) KeyboardType.Text else KeyboardType.Password
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (requireSecond) {
                    OutlinedTextField(
                        value = second,
                        onValueChange = { second = it; error = null },
                        label = { Text(stringResource(R.string.backup_dialog_second)) },
                        singleLine = true,
                        visualTransformation = visibility,
                        trailingIcon = visibilityToggle,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (showPasswords) KeyboardType.Text else KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (requireRepeat && !showPasswords) {
                        OutlinedTextField(
                            value = secondRepeat,
                            onValueChange = { secondRepeat = it; error = null },
                            label = { Text(stringResource(R.string.setup_repeat)) },
                            singleLine = true,
                            visualTransformation = visibility,
                            trailingIcon = visibilityToggle,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (showPasswords) KeyboardType.Text else KeyboardType.Password
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (legacyHint) {
                    Text(
                        stringResource(R.string.backup_dialog_legacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (error != null) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    first.length < AppPasswords.MIN_LENGTH -> error = tooShortOne
                    requireSecond && second.length < AppPasswords.MIN_LENGTH -> error = tooShortTwo
                    !showPasswords && requireRepeat && first != firstRepeat -> error = mismatch
                    !showPasswords && requireRepeat && requireSecond && second != secondRepeat -> error = mismatch
                    else -> onConfirm(first, second)
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun ChangePasswordsDialog(
    slot: Int,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isFirst = slot == 1
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newRepeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showPasswords by remember { mutableStateOf(false) }

    val tooShort = stringResource(R.string.setup_too_short, AppPasswords.MIN_LENGTH)
    val mismatch = stringResource(R.string.setup_mismatch)
    val wrong = stringResource(R.string.unlock_wrong)

    fun toggle() { showPasswords = !showPasswords }

    fun submit() {
        if (busy) return
        when {
            newPassword.length < AppPasswords.MIN_LENGTH -> error = tooShort
            !showPasswords && newPassword != newRepeat -> error = mismatch
            else -> {
                busy = true
                scope.launch {
                    val valid = withContext(Dispatchers.Default) {
                        if (isFirst) AppPasswords.verifyFirst(context, current)
                        else AppPasswords.verifySecond(context, current)
                    }
                    if (!valid) {
                        busy = false
                        error = wrong
                        return@launch
                    }
                    withContext(Dispatchers.Default) {
                        if (isFirst) AppPasswords.saveFirst(context, newPassword)
                        else AppPasswords.saveSecond(context, newPassword)
                    }
                    busy = false
                    onChanged()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        if (isFirst) R.string.settings_passwords_change_first
                        else R.string.settings_passwords_change_second
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PasswordDialogField(
                    value = current,
                    label = stringResource(
                        if (isFirst) R.string.change_passwords_current_first
                        else R.string.change_passwords_current_second
                    ),
                    visible = showPasswords,
                    onToggle = { toggle() },
                    onValueChange = { current = it; error = null }
                )
                PasswordDialogField(
                    value = newPassword,
                    label = stringResource(
                        if (isFirst) R.string.change_passwords_new_first
                        else R.string.change_passwords_new_second
                    ),
                    visible = showPasswords,
                    onToggle = { toggle() },
                    onValueChange = { newPassword = it; error = null },
                    imeAction = if (showPasswords) ImeAction.Done else ImeAction.Next,
                    onDone = { submit() }
                )
                if (!showPasswords) {
                    PasswordDialogField(
                        value = newRepeat,
                        label = stringResource(R.string.setup_repeat),
                        visible = showPasswords,
                        onToggle = { toggle() },
                        onValueChange = { newRepeat = it; error = null },
                        imeAction = ImeAction.Done,
                        onDone = { submit() }
                    )
                }
                Text(
                    stringResource(R.string.change_passwords_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (error != null) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !busy) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun PasswordDialogField(
    value: String,
    label: String,
    visible: Boolean,
    onToggle: () -> Unit,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = passwordTransformation(visible),
        trailingIcon = { PasswordVisibilityToggle(visible = visible, onToggle = onToggle) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (visible) KeyboardType.Text else KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth()
    )
}
