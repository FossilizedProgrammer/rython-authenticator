package com.rython.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rython.desktop.data.Account

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    lang: Lang,
    accounts: List<Account>,
    batchLabel: String?,
    onPasteFromClipboard: () -> Unit,
    onAccept: (String) -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    var link by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lang.t(K.transferTitle)) },
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(lang.t(K.transferHeading), style = MaterialTheme.typography.titleSmall)
            Text(
                lang.t(K.transferSteps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text(lang.t(K.linkPlaceholder)) },
                minLines = 2,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val text = Platform.clipboardText()
                        if (text == null) {
                            onPasteFromClipboard()
                        } else {
                            link = text
                        }
                    }
                ) { Text(lang.t(K.transferPaste)) }
                Button(
                    onClick = {
                        val text = link.trim()
                        if (text.isNotEmpty()) {
                            onAccept(text)
                            link = ""
                        }
                    }
                ) { Text(lang.t(K.actionAdd)) }
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(lang.t(K.transferReceived), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(0.dp))
                Text(
                    "  " + lang.t(K.transferCount, accounts.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (batchLabel != null) {
                    Text(
                        "  ·  " + batchLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (accounts.isEmpty()) {
                Text(
                    lang.t(K.transferEmpty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(accounts, key = { it.id }) { account ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            ServiceBadge(initials = account.initials, seed = account.issuer, size = 32.dp)
                            Spacer(Modifier.height(0.dp))
                            Text(
                                "  " + account.displayIssuer +
                                    (if (account.displayName.isNotBlank()) " · ${account.displayName}" else ""),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onFinish,
                enabled = accounts.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(lang.t(K.transferAdd))
            }
        }
    }
}
