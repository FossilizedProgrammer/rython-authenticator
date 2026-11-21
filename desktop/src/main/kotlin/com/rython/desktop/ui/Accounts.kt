package com.rython.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rython.desktop.core.OtpEngine
import com.rython.desktop.core.OtpType
import com.rython.desktop.core.TimeProvider
import com.rython.desktop.data.Account
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    lang: Lang,
    accounts: List<Account>,
    onCopy: (Account) -> Unit,
    onEdit: (Account) -> Unit,
    onAddManual: () -> Unit,
    onPasteLink: () -> Unit,
    onOpenSettings: () -> Unit,
    onAdvanceCounter: (Account) -> Unit
) {
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf<String?>(null) }
    var addMenuOpen by remember { mutableStateOf(false) }

    var now by remember { mutableStateOf(TimeProvider.nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = TimeProvider.nowMillis()
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(lang.t(K.accountsSearchPlaceholder)) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { query = ""; searchOpen = false }) {
                                    Icon(Icons.Filled.Close, contentDescription = lang.t(K.actionClose))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                CenterAlignedTopAppBar(
                    title = {
                        Text(lang.t(K.appName), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    },
                    navigationIcon = {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Filled.Search, contentDescription = lang.t(K.actionSearch))
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = lang.t(K.actionSettings))
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addMenuOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = lang.t(K.actionAdd))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (folders.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    item {
                        FilterChip(
                            selected = folder == null,
                            onClick = { folder = null },
                            label = { Text(lang.t(K.accountsFilterAll)) }
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
                            lang.t(if (accounts.isEmpty()) K.accountsEmptyTitle else K.accountsNoResults),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Hint(lang.t(K.accountsEmptyBody))
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { account ->
                        AccountRow(
                            lang = lang,
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
            title = { Text(lang.t(K.accountsAddTitle)) },
            text = { Text(lang.t(K.accountsAddBody)) },
            confirmButton = {
                TextButton(onClick = { addMenuOpen = false; onPasteLink() }) {
                    Text(lang.t(K.accountsPasteLink))
                }
            },
            dismissButton = {
                TextButton(onClick = { addMenuOpen = false; onAddManual() }) {
                    Text(lang.t(K.accountsAddManual))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountRow(
    lang: Lang,
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onCopy)
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
                        IconButton(onClick = onAdvanceCounter) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = lang.t(K.accountsNextCode),
                                modifier = Modifier.width(18.dp)
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = lang.t(K.editorTitleEdit))
            }
        }
    }
}
