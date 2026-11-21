package com.rython.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rython.desktop.app.Prefs
import com.rython.desktop.core.TransferSession
import com.rython.desktop.core.TimeProvider
import com.rython.desktop.data.Account
import com.rython.desktop.data.Backup
import com.rython.desktop.security.Passwords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface BackupTask {
    data object Export : BackupTask
    data class Import(val bytes: ByteArray, val legacy: Boolean) : BackupTask
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    lang: Lang,
    accounts: List<Account>,
    onLangChange: (Lang) -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    onTransfer: () -> Unit,
    onImported: (List<Account>) -> Unit,
    onClearAll: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var task by remember { mutableStateOf<BackupTask?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var changeSlot by remember { mutableStateOf(0) }
    var timeZone by remember { mutableStateOf(TimeProvider.selectedTimeZone()) }
    var useInternet by remember { mutableStateOf(TimeProvider.useInternetTime()) }
    var showZonePicker by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<FilePickerMode?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }

    val stamp = remember { SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lang.t(K.settingsTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = lang.t(K.actionBack))
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
            Section(lang.t(K.settingsLanguageTitle), lang.t(K.settingsLanguageBody))
            LangChips(lang, onLangChange)

            HorizontalDivider()
            Section(lang.t(K.settingsTimeTitle), lang.t(K.settingsTimeBody))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(lang.t(K.settingsTimezoneTitle), style = MaterialTheme.typography.titleSmall)
                    Hint(timeZone ?: lang.t(K.settingsTimezoneDevice))
                }
                OutlinedButton(onClick = { showZonePicker = true }) {
                    Text(lang.t(K.settingsTimezonePick))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(lang.t(K.settingsInternetTimeTitle), style = MaterialTheme.typography.titleSmall)
                    Hint(lang.t(K.settingsInternetTimeBody))
                }
                Switch(
                    checked = useInternet,
                    onCheckedChange = { value ->
                        useInternet = value
                        TimeProvider.setUseInternetTime(value)
                    }
                )
            }
            OutlinedButton(
                onClick = {
                    syncing = true
                    scope.launch {
                        val offset = withContext(Dispatchers.IO) { TimeProvider.syncInternetTime() }
                        syncing = false
                        if (offset == null) {
                            onMessage(lang.t(K.settingsInternetSyncFail))
                        } else {
                            useInternet = true
                            onMessage(lang.t(K.settingsInternetSyncOk, offset))
                        }
                    }
                },
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth()
            ) { Text(lang.t(K.settingsInternetSync)) }

            HorizontalDivider()
            Section(lang.t(K.settingsPasswordsTitle), lang.t(K.settingsPasswordsBody))
            OutlinedButton(
                onClick = { changeSlot = 1 },
                modifier = Modifier.fillMaxWidth()
            ) { Text(lang.t(K.settingsPasswordsChangeFirst)) }
            OutlinedButton(
                onClick = { changeSlot = 2 },
                modifier = Modifier.fillMaxWidth()
            ) { Text(lang.t(K.settingsPasswordsChangeSecond)) }

            HorizontalDivider()
            Section(lang.t(K.settingsTransferTitle), lang.t(K.settingsTransferBody))
            OutlinedButton(onClick = onTransfer, modifier = Modifier.fillMaxWidth()) {
                Text(lang.t(K.settingsTransferButton))
            }

            HorizontalDivider()
            Section(lang.t(K.settingsBackupTitle), lang.t(K.settingsBackupBody))
            Button(
                onClick = { task = BackupTask.Export },
                enabled = accounts.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(lang.t(K.settingsBackupCreate)) }
            OutlinedButton(
                onClick = { picker = FilePickerMode.Open },
                modifier = Modifier.fillMaxWidth()
            ) { Text(lang.t(K.settingsBackupRestore)) }

            HorizontalDivider()
            Section(lang.t(K.settingsAboutTitle), null)
            Hint(lang.t(K.settingsAboutBody, accounts.size, Prefs.directory.absolutePath))

            HorizontalDivider()
            OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                Text(lang.t(K.settingsClearAll), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val currentTask = task
    if (currentTask != null) {
        val isExport = currentTask is BackupTask.Export
        BackupPasswordDialog(
            lang = lang,
            title = lang.t(if (isExport) K.backupDialogTitleExport else K.backupDialogTitleImport),
            confirmLabel = lang.t(if (isExport) K.backupDialogCreate else K.backupDialogRestore),
            requireRepeat = isExport,
            requireSecond = if (currentTask is BackupTask.Import) !currentTask.legacy else true,
            legacyHint = currentTask is BackupTask.Import && currentTask.legacy,
            onDismiss = { task = null },
            onConfirm = onConfirm@{ first, second ->
                task = null
                if (isExport) {
                    scope.launch {
                        val bytes = withContext(Dispatchers.Default) {
                            val valid = Passwords.verifyFirst(first) && Passwords.verifySecond(second)
                            if (!valid) null
                            else runCatching {
                                Backup.export(accounts, first.toCharArray(), second.toCharArray())
                            }.getOrNull()
                        }
                        if (bytes == null) {
                            onMessage(lang.t(K.msgBackupSaveFailed))
                            return@launch
                        }
                        pendingBytes = bytes
                        picker = FilePickerMode.Save
                    }
                } else {
                    val bytes = (currentTask as BackupTask.Import).bytes
                    scope.launch {
                        val imported = withContext(Dispatchers.Default) {
                            runCatching {
                                Backup.import(bytes, first.toCharArray(), second.toCharArray())
                            }
                        }
                        imported.onSuccess { list ->
                            val fresh = TransferSession.newOnly(list, accounts)
                            if (list.isEmpty()) {
                                onMessage(lang.t(K.msgBackupEmpty))
                            } else {
                                if (fresh.isNotEmpty()) onImported(fresh)
                                onMessage(lang.t(K.msgRestoredCount, list.size))
                            }
                        }.onFailure {
                            onMessage(lang.t(K.msgRestoreFailed))
                        }
                    }
                }
            }
        )
    }

    val pickerMode = picker
    if (pickerMode != null) {
        FilePickerDialog(
            lang = lang,
            mode = pickerMode,
            extension = "rybak",
            defaultName = if (pickerMode == FilePickerMode.Save) "rython-backup-$stamp.rybak" else "",
            onDismiss = {
                picker = null
                pendingBytes = null
            },
            onPicked = { file ->
                picker = null
                if (pickerMode == FilePickerMode.Open) {
                    scope.launch {
                        val bytes = withContext(Dispatchers.IO) {
                            runCatching { file.readBytes() }.getOrNull()
                        }
                        if (bytes == null || bytes.isEmpty()) {
                            onMessage(lang.t(K.msgBackupReadFailed))
                        } else {
                            task = BackupTask.Import(bytes, Backup.isLegacy(bytes))
                        }
                    }
                } else {
                    val bytes = pendingBytes
                    pendingBytes = null
                    if (bytes != null) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                runCatching { file.writeBytes(bytes) }.isSuccess
                            }
                            onMessage(lang.t(if (ok) K.msgBackupSaved else K.msgBackupSaveFailed))
                        }
                    }
                }
            }
        )
    }

    if (changeSlot != 0) {
        ChangePasswordDialog(
            lang = lang,
            slot = changeSlot,
            onDismiss = { changeSlot = 0 },
            onChanged = {
                changeSlot = 0
                onMessage(lang.t(K.msgPasswordsChanged))
            }
        )
    }

    if (showZonePicker) {
        AlertDialog(
            onDismissRequest = { showZonePicker = false },
            title = { Text(lang.t(K.settingsTimezonePick)) },
            text = {
                LazyColumn(modifier = Modifier.height(360.dp)) {
                    item {
                        TextButton(onClick = {
                            timeZone = null
                            TimeProvider.setSelectedTimeZone(null)
                            showZonePicker = false
                        }) { Text(lang.t(K.settingsTimezoneDevice)) }
                    }
                    items(TimeProvider.commonZoneIds()) { zone ->
                        TextButton(onClick = {
                            timeZone = zone
                            TimeProvider.setSelectedTimeZone(zone)
                            showZonePicker = false
                        }) { Text(zone) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showZonePicker = false }) { Text(lang.t(K.actionCancel)) }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(lang.t(K.settingsClearTitle)) },
            text = { Text(lang.t(K.settingsClearBody)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearAll() }) {
                    Text(lang.t(K.settingsClearConfirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(lang.t(K.actionCancel)) }
            }
        )
    }
}

@Composable
private fun Section(title: String, body: String?) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    if (body != null) Hint(body)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LangChips(lang: Lang, onLangChange: (Lang) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        L.all.forEach { candidate ->
            FilterChip(
                selected = lang.code == candidate.code,
                onClick = { if (lang.code != candidate.code) onLangChange(candidate) },
                label = { Text(candidate.nativeName) }
            )
        }
    }
}

@Composable
private fun BackupPasswordDialog(
    lang: Lang,
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

    fun toggle() { showPasswords = !showPasswords }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(
                    value = first,
                    onValueChange = { first = it; error = null },
                    label = lang.t(K.backupDialogFirst),
                    visible = showPasswords,
                    onToggleVisibility = { toggle() }
                )
                if (requireRepeat && !showPasswords) {
                    PasswordField(
                        value = firstRepeat,
                        onValueChange = { firstRepeat = it; error = null },
                        label = lang.t(K.setupRepeat),
                        visible = false,
                        onToggleVisibility = { toggle() }
                    )
                }
                if (requireSecond) {
                    PasswordField(
                        value = second,
                        onValueChange = { second = it; error = null },
                        label = lang.t(K.backupDialogSecond),
                        visible = showPasswords,
                        onToggleVisibility = { toggle() }
                    )
                    if (requireRepeat && !showPasswords) {
                        PasswordField(
                            value = secondRepeat,
                            onValueChange = { secondRepeat = it; error = null },
                            label = lang.t(K.setupRepeat),
                            visible = false,
                            onToggleVisibility = { toggle() }
                        )
                    }
                }
                if (legacyHint) Hint(lang.t(K.backupDialogLegacy))
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
                    first.length < Passwords.MIN_LENGTH ->
                        error = lang.t(K.backupErrorShort, 1, Passwords.MIN_LENGTH)
                    requireSecond && second.length < Passwords.MIN_LENGTH ->
                        error = lang.t(K.backupErrorShort, 2, Passwords.MIN_LENGTH)
                    !showPasswords && requireRepeat && first != firstRepeat ->
                        error = lang.t(K.setupMismatch)
                    !showPasswords && requireRepeat && requireSecond && second != secondRepeat ->
                        error = lang.t(K.setupMismatch)
                    else -> onConfirm(first, second)
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(lang.t(K.actionCancel)) }
        }
    )
}

@Composable
private fun ChangePasswordDialog(
    lang: Lang,
    slot: Int,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isFirst = slot == 1

    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newRepeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showPasswords by remember { mutableStateOf(false) }

    fun toggle() { showPasswords = !showPasswords }

    fun submit() {
        if (busy) return
        when {
            newPassword.length < Passwords.MIN_LENGTH ->
                error = lang.t(K.setupTooShort, Passwords.MIN_LENGTH)
            !showPasswords && newPassword != newRepeat ->
                error = lang.t(K.setupMismatch)
            else -> {
                busy = true
                scope.launch {
                    val valid = withContext(Dispatchers.Default) {
                        if (isFirst) Passwords.verifyFirst(current) else Passwords.verifySecond(current)
                    }
                    if (!valid) {
                        busy = false
                        error = lang.t(K.unlockWrong)
                        return@launch
                    }
                    withContext(Dispatchers.Default) {
                        if (isFirst) Passwords.saveFirst(newPassword) else Passwords.saveSecond(newPassword)
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
                    lang.t(
                        if (isFirst) K.settingsPasswordsChangeFirst else K.settingsPasswordsChangeSecond
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = lang.t(K.actionClose))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PasswordField(
                    value = current,
                    onValueChange = { current = it; error = null },
                    label = lang.t(
                        if (isFirst) K.changePasswordsCurrentFirst else K.changePasswordsCurrentSecond
                    ),
                    visible = showPasswords,
                    onToggleVisibility = { toggle() }
                )
                PasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    label = lang.t(
                        if (isFirst) K.changePasswordsNewFirst else K.changePasswordsNewSecond
                    ),
                    visible = showPasswords,
                    onToggleVisibility = { toggle() },
                    imeAction = if (showPasswords) ImeAction.Done else ImeAction.Next,
                    onDone = { submit() }
                )
                if (!showPasswords) {
                    PasswordField(
                        value = newRepeat,
                        onValueChange = { newRepeat = it; error = null },
                        label = lang.t(K.setupRepeat),
                        visible = false,
                        onToggleVisibility = { toggle() },
                        imeAction = ImeAction.Done,
                        onDone = { submit() }
                    )
                }
                Hint(lang.t(K.changePasswordsNote))
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
            TextButton(onClick = { submit() }, enabled = !busy) { Text(lang.t(K.actionSave)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(lang.t(K.actionClose)) }
        }
    )
}
