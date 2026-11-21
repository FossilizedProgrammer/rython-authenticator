package com.rython.authenticator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import com.rython.authenticator.R
import com.rython.authenticator.core.OtpEngine
import com.rython.authenticator.core.TimeProvider
import com.rython.authenticator.core.OtpUri
import com.rython.authenticator.core.TransferSession
import com.rython.authenticator.data.Account
import com.rython.authenticator.data.VaultStore
import com.rython.authenticator.security.AppPasswords
import kotlinx.coroutines.launch

private enum class Route { Accounts, Editor, Scan, Settings, Transfer }

@Composable
fun RythonRoot(store: VaultStore) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val accounts by store.accounts.collectAsState()
    val error by store.error.collectAsState()

    val prefs = remember { context.getSharedPreferences("rython_prefs", Context.MODE_PRIVATE) }
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    var lockEnabled by remember { mutableStateOf(prefs.getBoolean("biometric_lock", false) && biometricAvailable) }
    var unlocked by remember { mutableStateOf(!lockEnabled) }
    var promptShown by remember { mutableStateOf(false) }

    // Two app passwords: created on first start, then asked for on every launch.
    var passwordsConfigured by remember { mutableStateOf(AppPasswords.isConfigured(context)) }
    var passwordsOk by remember { mutableStateOf(false) }

    var route by remember { mutableStateOf(Route.Accounts) }
    var editing by remember { mutableStateOf<Account?>(null) }

    // Google Authenticator "Transfer accounts" import (may span several QR codes).
    val transfer = remember { TransferSession() }
    var transferVersion by remember { mutableIntStateOf(0) }
    var continuousScan by remember { mutableStateOf(false) }

    val toast: (String) -> Unit = remember {
        { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(Unit) { store.load() }

    LaunchedEffect(error) {
        error?.let { code ->
            toast(
                context.getString(
                    when (code) {
                        VaultStore.ERROR_READ -> R.string.msg_vault_read_failed
                        VaultStore.ERROR_CORRUPT -> R.string.msg_vault_corrupt
                        else -> R.string.msg_vault_save_failed
                    }
                )
            )
            store.clearError()
        }
    }

    // Re-lock with biometrics when the app goes to the background (passwords are only
    // asked once per launch).
    DisposableEffect(lifecycleOwner, lockEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && lockEnabled) {
                unlocked = false
                promptShown = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val promptUnlock: () -> Unit = {
        if (activity != null) {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        unlocked = true
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_USER_CANCELED
                        ) {
                            toast(errString.toString())
                        }
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Rython-Authenticator")
                .setSubtitle(context.getString(R.string.prompt_subtitle))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButtonText(context.getString(R.string.prompt_cancel))
                .build()
            runCatching { prompt.authenticate(info) }
                .onFailure { toast(context.getString(R.string.msg_biometric_unavailable)) }
        }
    }

    if (!passwordsConfigured) {
        PasswordGateScreen(
            setup = true,
            onDone = {
                passwordsConfigured = true
                passwordsOk = true
                unlocked = !lockEnabled
            },
            onQuit = { activity?.finish() }
        )
        return
    }

    if (!passwordsOk) {
        PasswordGateScreen(
            setup = false,
            onDone = {
                passwordsOk = true
                if (lockEnabled) {
                    unlocked = false
                    promptShown = false
                }
            },
            onQuit = { activity?.finish() }
        )
        return
    }

    LaunchedEffect(lockEnabled, unlocked, passwordsOk) {
        if (lockEnabled && !unlocked && !promptShown) {
            promptShown = true
            promptUnlock()
        }
    }

    if (lockEnabled && !unlocked) {
        LockScreen(
            onUnlock = { promptShown = true; promptUnlock() },
            onLeave = { activity?.finish() }
        )
        return
    }

    // Android back returns to the account list instead of closing the app.
    BackHandler(enabled = route != Route.Accounts) {
        editing = null
        when (route) {
            Route.Scan -> route = if (continuousScan) Route.Transfer else Route.Accounts
            else -> route = Route.Accounts
        }
    }

    when (route) {
        Route.Accounts -> AccountsScreen(
            accounts = accounts,
            onCopy = { account ->
                val code = OtpEngine.code(
                    secretBase32 = account.secret,
                    type = account.type,
                    algorithm = account.algorithm,
                    digits = account.digits,
                    period = account.period,
                    counter = account.counter,
                    nowMillis = TimeProvider.nowMillis(context)
                )
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                // Keep the copied code out of clipboard previews / sync history.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    clipboard.clearPrimaryClip()
                    clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                }
                toast(context.getString(R.string.accounts_copied, account.displayIssuer))
                if (account.type == com.rython.authenticator.core.OtpType.HOTP) {
                    scope.launch { store.incrementCounter(account.id) }
                }
            },
            onEdit = { editing = it; route = Route.Editor },
            onAddManual = { editing = null; route = Route.Editor },
            onScan = { route = Route.Scan },
            onOpenSettings = { route = Route.Settings },
            onAdvanceCounter = { scope.launch { store.incrementCounter(it.id) } }
        )

        Route.Editor -> EditorScreen(
            existing = editing,
            folders = accounts.map { it.folder }.filter { it.isNotBlank() }.distinct(),
            onSave = { account ->
                scope.launch {
                    store.upsert(account)
                    editing = null
                    route = Route.Accounts
                    toast(context.getString(R.string.msg_saved))
                }
            },
            onDelete = { account ->
                scope.launch {
                    store.delete(account.id)
                    editing = null
                    route = Route.Accounts
                    toast(context.getString(R.string.msg_deleted))
                }
            },
            onBack = { editing = null; route = Route.Accounts }
        )

        Route.Scan -> ScanScreen(
            continuous = continuousScan,
            onCode = { raw ->
                if (continuousScan) {
                    runCatching { transfer.accept(raw) }
                        .onSuccess { added ->
                            transferVersion++
                            toast(
                                if (added == 0) context.getString(R.string.msg_already_received)
                                else context.getString(R.string.msg_received_count, added)
                            )
                        }
                        .onFailure { toast(context.getString(R.string.msg_not_export)) }
                } else {
                    val parsed = runCatching { OtpUri.parse(raw) }.getOrElse { emptyList() }
                    val fresh = TransferSession.newOnly(parsed, accounts)
                    scope.launch {
                        if (fresh.isNotEmpty()) store.addAll(fresh)
                        route = Route.Accounts
                        toast(
                            when {
                                parsed.isEmpty() -> context.getString(R.string.msg_nothing_found)
                                fresh.isEmpty() -> context.getString(R.string.msg_already_added)
                                fresh.size == 1 ->
                                    context.getString(R.string.msg_added_named, fresh.first().displayIssuer)
                                else -> context.getString(R.string.msg_added_count, fresh.size)
                            }
                        )
                    }
                }
            },
            onMessage = toast,
            onBack = {
                route = if (continuousScan) Route.Transfer else Route.Accounts
            }
        )

        Route.Transfer -> {
            val transferAccounts = remember(transferVersion) { transfer.accounts }
            val transferBatch = remember(transferVersion) {
                if (transfer.batchSize > 1) {
                    context.getString(
                        R.string.transfer_batch,
                        transfer.batchesReceived,
                        transfer.batchSize
                    )
                } else {
                    null
                }
            }
            TransferScreen(
                accounts = transferAccounts,
                batchLabel = transferBatch,
                onScan = {
                    continuousScan = true
                    route = Route.Scan
                },
                onAcceptRaw = { raw ->
                    runCatching { transfer.accept(raw) }
                        .onSuccess { added ->
                            transferVersion++
                            toast(
                                if (added == 0) context.getString(R.string.msg_transfer_invalid)
                                else context.getString(R.string.msg_read_count, added)
                            )
                        }
                        .onFailure {
                            toast(context.getString(R.string.msg_not_export_text))
                        }
                },
                onFinish = {
                    val fresh = TransferSession.newOnly(transfer.accounts, accounts)
                    val received = transfer.accounts.size
                    scope.launch {
                        if (fresh.isNotEmpty()) store.addAll(fresh)
                        transfer.clear()
                        transferVersion++
                        continuousScan = false
                        route = Route.Accounts
                        toast(
                            when {
                                fresh.isNotEmpty() -> context.getString(R.string.msg_added_count, fresh.size)
                                received > 0 -> context.getString(R.string.msg_all_existing)
                                else -> context.getString(R.string.msg_nothing_added)
                            }
                        )
                    }
                },
                onMessage = toast,
                onBack = {
                    continuousScan = false
                    route = Route.Accounts
                }
            )
        }

        Route.Settings -> SettingsScreen(
            accounts = accounts,
            lockEnabled = lockEnabled,
            biometricAvailable = biometricAvailable,
            onLockChange = { enabled ->
                if (enabled && !biometricAvailable) {
                    toast(context.getString(R.string.msg_biometric_missing))
                } else {
                    lockEnabled = enabled
                    unlocked = !enabled
                    promptShown = false
                    prefs.edit().putBoolean("biometric_lock", enabled).apply()
                    toast(
                        context.getString(
                            if (enabled) R.string.msg_biometric_enabled
                            else R.string.msg_biometric_disabled
                        )
                    )
                }
            },
            onImported = { imported ->
                scope.launch {
                    val keys = accounts.map { it.secret + "|" + it.issuer + "|" + it.name }.toSet()
                    val fresh = imported.filter { "${it.secret}|${it.issuer}|${it.name}" !in keys }
                    if (fresh.isNotEmpty()) store.addAll(fresh)
                }
            },
            onClearAll = {
                scope.launch {
                    store.deleteAll()
                    toast(context.getString(R.string.msg_all_deleted))
                }
            },
            onMessage = toast,
            onTransfer = {
                transfer.clear()
                transferVersion++
                continuousScan = false
                route = Route.Transfer
            },
            onBack = { route = Route.Accounts }
        )
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit, onLeave: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.lock_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                stringResource(R.string.lock_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
            Button(onClick = onUnlock, modifier = Modifier.padding(top = 24.dp)) {
                Text(stringResource(R.string.lock_unlock))
            }
            TextButton(onClick = onLeave, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.lock_quit))
            }
        }
    }
}
