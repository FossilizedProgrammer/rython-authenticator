package com.rython.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rython.desktop.core.OtpEngine
import com.rython.desktop.core.OtpType
import com.rython.desktop.core.TimeProvider
import com.rython.desktop.core.TransferSession
import com.rython.desktop.data.Account
import com.rython.desktop.data.Vault
import com.rython.desktop.security.Passwords
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Route { Accounts, Editor, Settings, Transfer }

@Composable
fun Root(vault: Vault, lang: Lang, onLangChange: (Lang) -> Unit) {
    val scope = rememberCoroutineScope()
    val accounts by vault.accounts.collectAsState()
    val error by vault.error.collectAsState()

    var configured by remember { mutableStateOf(Passwords.isConfigured()) }
    var unlocked by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf(Route.Accounts) }
    var editing by remember { mutableStateOf<Account?>(null) }
    var linkDialog by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val transfer = remember { TransferSession() }
    var transferVersion by remember { mutableIntStateOf(0) }

    val toast: (String) -> Unit = { message = it }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3000)
            message = null
        }
    }

    LaunchedEffect(Unit) {
        if (Passwords.isConfigured()) vault.load()
    }

    LaunchedEffect(error) {
        error?.let { code ->
            toast(
                lang.t(
                    when (code) {
                        Vault.ERROR_READ -> K.msgVaultReadFailed
                        Vault.ERROR_CORRUPT -> K.msgVaultCorrupt
                        else -> K.msgVaultSaveFailed
                    }
                )
            )
            vault.clearError()
        }
    }

    if (!configured) {
        GateScreen(
            lang = lang,
            setup = true,
            onDone = {
                configured = true
                unlocked = true
                scope.launch { vault.load() }
            },
            onQuit = { kotlin.system.exitProcess(0) }
        )
        return
    }

    if (!unlocked) {
        GateScreen(
            lang = lang,
            setup = false,
            onDone = {
                unlocked = true
                scope.launch { vault.load() }
            },
            onQuit = { kotlin.system.exitProcess(0) }
        )
        return
    }

    fun addLink(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            runCatching { com.rython.desktop.core.OtpUri.parse(trimmed) }
                .onSuccess { parsed ->
                    val fresh = TransferSession.newOnly(parsed, accounts)
                    scope.launch {
                        if (fresh.isNotEmpty()) vault.addAll(fresh)
                        toast(
                            when {
                                fresh.isEmpty() -> lang.t(K.msgAlreadyAdded)
                                fresh.size == 1 -> lang.t(K.msgAddedNamed, fresh.first().displayIssuer)
                                else -> lang.t(K.msgAddedCount, fresh.size)
                            }
                        )
                    }
                }
                .onFailure { toast(lang.t(K.msgNotExport)) }
        } else {
            runCatching { transfer.accept(trimmed) }
                .onSuccess { added ->
                    transferVersion++
                    route = Route.Transfer
                    toast(
                        if (added == 0) lang.t(K.msgAlreadyReceived)
                        else lang.t(K.msgReceivedCount, added)
                    )
                }
                .onFailure { toast(lang.t(K.msgNotExport)) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (route) {
            Route.Accounts -> AccountsScreen(
                lang = lang,
                accounts = accounts,
                onCopy = { account ->
                    val code = OtpEngine.code(
                        secretBase32 = account.secret,
                        type = account.type,
                        algorithm = account.algorithm,
                        digits = account.digits,
                        period = account.period,
                        counter = account.counter,
                        nowMillis = TimeProvider.nowMillis()
                    )
                    Platform.setClipboard(code)
                    toast(lang.t(K.accountsCopied, account.displayIssuer))
                    if (account.type == OtpType.HOTP) {
                        scope.launch { vault.incrementCounter(account.id) }
                    }
                },
                onEdit = { editing = it; route = Route.Editor },
                onAddManual = { editing = null; route = Route.Editor },
                onPasteLink = {
                    val clip = Platform.clipboardText()
                    if (clip != null && clip.trim().startsWith("otpauth", ignoreCase = true)) {
                        addLink(clip)
                    } else {
                        linkText = clip.orEmpty()
                        linkDialog = true
                    }
                },
                onOpenSettings = { route = Route.Settings },
                onAdvanceCounter = { scope.launch { vault.incrementCounter(it.id) } }
            )

            Route.Editor -> EditorScreen(
                lang = lang,
                existing = editing,
                folders = accounts.map { it.folder }.filter { it.isNotBlank() }.distinct(),
                onSave = { account ->
                    scope.launch {
                        vault.upsert(account)
                        editing = null
                        route = Route.Accounts
                        toast(lang.t(K.msgSaved))
                    }
                },
                onDelete = { account ->
                    scope.launch {
                        vault.delete(account.id)
                        editing = null
                        route = Route.Accounts
                        toast(lang.t(K.msgDeleted))
                    }
                },
                onBack = { editing = null; route = Route.Accounts }
            )

            Route.Settings -> SettingsScreen(
                lang = lang,
                accounts = accounts,
                onLangChange = onLangChange,
                onBack = { route = Route.Accounts },
                onMessage = toast,
                onTransfer = {
                    transfer.clear()
                    transferVersion++
                    route = Route.Transfer
                },
                onImported = { imported -> scope.launch { vault.addAll(imported) } },
                onClearAll = {
                    scope.launch {
                        vault.deleteAll()
                        toast(lang.t(K.msgAllDeleted))
                    }
                }
            )

            Route.Transfer -> {
                val received = remember(transferVersion) { transfer.accounts }
                val batchLabel = remember(transferVersion) {
                    if (transfer.batchSize > 1) {
                        lang.t(K.transferBatch, transfer.batchesReceived, transfer.batchSize)
                    } else null
                }
                TransferScreen(
                    lang = lang,
                    accounts = received,
                    batchLabel = batchLabel,
                    onPasteFromClipboard = { toast(lang.t(K.transferNothingToPaste)) },
                    onAccept = { text ->
                        runCatching { transfer.accept(text) }
                            .onSuccess { added ->
                                transferVersion++
                                toast(
                                    if (added == 0) lang.t(K.msgTransferInvalid)
                                    else lang.t(K.msgReadCount, added)
                                )
                            }
                            .onFailure { toast(lang.t(K.msgNotExport)) }
                    },
                    onFinish = {
                        val fresh = TransferSession.newOnly(transfer.accounts, accounts)
                        val receivedCount = transfer.accounts.size
                        scope.launch {
                            if (fresh.isNotEmpty()) vault.addAll(fresh)
                            transfer.clear()
                            transferVersion++
                            route = Route.Accounts
                            toast(
                                when {
                                    fresh.isNotEmpty() -> lang.t(K.msgAddedCount, fresh.size)
                                    receivedCount > 0 -> lang.t(K.msgAllExisting)
                                    else -> lang.t(K.msgNothingAdded)
                                }
                            )
                        }
                    },
                    onBack = {
                        transfer.clear()
                        transferVersion++
                        route = Route.Accounts
                    }
                )
            }
        }

        val current = message
        if (current != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .fillMaxWidth(0.9f)
            ) {
                Column(
                    modifier = Modifier.background(MaterialTheme.colorScheme.inverseSurface).padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        current,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    if (linkDialog) {
        AlertDialog(
            onDismissRequest = { linkDialog = false },
            title = { Text(lang.t(K.linkTitle)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hint(lang.t(K.linkBody))
                    OutlinedTextField(
                        value = linkText,
                        onValueChange = { linkText = it },
                        label = { Text(lang.t(K.linkPlaceholder)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = linkText
                    linkDialog = false
                    linkText = ""
                    addLink(text)
                }) { Text(lang.t(K.actionAdd)) }
            },
            dismissButton = {
                TextButton(onClick = { linkDialog = false; linkText = "" }) {
                    Text(lang.t(K.actionCancel))
                }
            }
        )
    }
}
