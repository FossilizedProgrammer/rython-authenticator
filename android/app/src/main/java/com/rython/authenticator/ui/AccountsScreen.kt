package com.rython.authenticator.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rython.authenticator.R
import com.rython.authenticator.core.OtpEngine
import com.rython.authenticator.core.OtpType
import com.rython.authenticator.core.TimeProvider
import androidx.compose.ui.platform.LocalContext
import com.rython.authenticator.data.Account
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    accounts: List<Account>,
    onCopy: (Account) -> Unit,
    onEdit: (Account) -> Unit,
    onAddManual: () -> Unit,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onAdvanceCounter: (Account) -> Unit
) {
    val context = LocalContext.current
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf<String?>(null) }
    var addMenuOpen by remember { mutableStateOf(false) }

    var now by remember { mutableStateOf(TimeProvider.nowMillis(context)) }
    LaunchedEffect(context) {
        while (true) {
            now = TimeProvider.nowMillis(context)
            delay(500)
        }
    }

    val folders = accounts.map { it.folder.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

    val visible = accounts.filter { account ->
        val matchesFolder = folder == null || account.folder.trim() == folder
        val haystack = "${account.issuer} ${account.name} ${account.folder}".lowercase()
        val matchesQuery = query.isBlank() || haystack.contains(query.trim().lowercase())
        matchesFolder && matchesQuery
    }

    Scaffold(
        topBar = {
            if (searchOpen) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.accounts_search_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { query = ""; searchOpen = false }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Rython-Authenticator",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addMenuOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (folders.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    item {
                        FilterChip(
                            selected = folder == null,
                            onClick = { folder = null },
                            label = { Text(stringResource(R.string.accounts_filter_all)) }
                        )
                    }
                    items(folders) { name ->
                        FilterChip(
                            selected = folder == name,
                            onClick = { folder = if (folder == name) null else name },
                            label = { Text(name) }
                        )
                    }
                }
            }

            if (visible.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(
                                if (accounts.isEmpty()) R.string.accounts_empty_title
                                else R.string.accounts_no_results
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.accounts_empty_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 40.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { account ->
                        AccountRow(
                            account = account,
                            now = now,
                            onCopy = { onCopy(account) },
                            onEdit = { onEdit(account) },
                            onAdvanceCounter = { onAdvanceCounter(account) }
                        )
                    }
                }
            }
        }
    }

    if (addMenuOpen) {
        AlertDialog(
            onDismissRequest = { addMenuOpen = false },
            title = { Text(stringResource(R.string.accounts_add_title)) },
            text = { Text(stringResource(R.string.accounts_add_body)) },
            confirmButton = {
                TextButton(onClick = { addMenuOpen = false; onScan() }) {
                    Text(stringResource(R.string.accounts_scan_qr))
                }
            },
            dismissButton = {
                TextButton(onClick = { addMenuOpen = false; onAddManual() }) {
                    Text(stringResource(R.string.accounts_add_manual))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountRow(
    account: Account,
    now: Long,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onAdvanceCounter: () -> Unit
) {
    val code = OtpEngine.code(
        secretBase32 = account.secret,
        type = account.type,
        algorithm = account.algorithm,
        digits = account.digits,
        period = account.period,
        counter = account.counter,
        nowMillis = now
    )
    val period = if (account.type == OtpType.STEAM) OtpEngine.STEAM_PERIOD else account.period
    val remaining = OtpEngine.secondsRemaining(period, now)
    val isTimed = account.type != OtpType.HOTP
    val urgent = remaining <= 5

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onCopy, onLongClick = onEdit)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ServiceBadge(initials = account.initials, seed = account.issuer.ifBlank { account.name })
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    account.displayIssuer,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (account.displayName.isNotBlank()) {
                    Text(
                        account.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (account.folder.isNotBlank()) {
                    Text(
                        "#${account.folder}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    OtpEngine.formatCode(code),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp,
                    color = if (urgent && isTimed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                if (isTimed) {
                    CountdownRing(
                        progress = remaining.toFloat() / period.toFloat(),
                        color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            remaining.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "HOTP #${account.counter}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = onAdvanceCounter, modifier = Modifier.width(32.dp)) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.accounts_next_code),
                                modifier = Modifier.width(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
