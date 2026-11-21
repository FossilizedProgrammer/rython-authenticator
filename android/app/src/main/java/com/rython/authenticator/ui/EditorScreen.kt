package com.rython.authenticator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.rython.authenticator.R
import com.rython.authenticator.core.Base32
import com.rython.authenticator.core.OtpAlgorithm
import com.rython.authenticator.core.OtpEngine
import com.rython.authenticator.core.OtpType
import com.rython.authenticator.data.Account

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    existing: Account?,
    folders: List<String>,
    onSave: (Account) -> Unit,
    onDelete: (Account) -> Unit,
    onBack: () -> Unit
) {
    var issuer by remember { mutableStateOf(existing?.issuer ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var secret by remember { mutableStateOf(existing?.secret ?: "") }
    var folder by remember { mutableStateOf(existing?.folder ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: OtpType.TOTP) }
    var algorithm by remember { mutableStateOf(existing?.algorithm ?: OtpAlgorithm.SHA1) }
    var digits by remember { mutableStateOf((existing?.digits ?: 6).toString()) }
    var period by remember { mutableStateOf((existing?.period ?: 30).toString()) }
    var counter by remember { mutableStateOf((existing?.counter ?: 0L).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val nameError = stringResource(R.string.editor_error_name)
    val secretError = stringResource(R.string.editor_error_secret)
    val secretFormatError = stringResource(R.string.editor_error_secret_format)

    fun save() {
        val cleanSecret = Account.normalizeSecret(secret)
        when {
            issuer.isBlank() && name.isBlank() -> error = nameError
            cleanSecret.isBlank() -> error = secretError
            !Base32.isValid(cleanSecret) -> error = secretFormatError
            else -> {
                error = null
                onSave(
                    Account(
                        id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                        issuer = issuer.trim(),
                        name = name.trim().ifBlank { issuer.trim() },
                        secret = cleanSecret,
                        type = type,
                        algorithm = algorithm,
                        digits = (digits.toIntOrNull() ?: 6).coerceIn(6, 10),
                        period = (period.toIntOrNull() ?: 30).coerceAtLeast(1),
                        counter = counter.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                        folder = folder.trim(),
                        sortOrder = existing?.sortOrder ?: System.currentTimeMillis()
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (existing == null) R.string.editor_title_add
                            else R.string.editor_title_edit
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = issuer,
                onValueChange = { issuer = it },
                label = { Text(stringResource(R.string.editor_issuer)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.editor_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text(stringResource(R.string.editor_secret)) },
                supportingText = { Text(stringResource(R.string.editor_secret_help)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.editor_type), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OtpType.entries.forEach { candidate ->
                    FilterChip(
                        selected = type == candidate,
                        onClick = { type = candidate },
                        label = {
                            Text(
                                when (candidate) {
                                    OtpType.TOTP -> "TOTP"
                                    OtpType.HOTP -> "HOTP"
                                    OtpType.STEAM -> "Steam"
                                }
                            )
                        }
                    )
                }
            }

            if (type != OtpType.STEAM) {
                Text(
                    stringResource(R.string.editor_algorithm),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OtpAlgorithm.entries.forEach { candidate ->
                        FilterChip(
                            selected = algorithm == candidate,
                            onClick = { algorithm = candidate },
                            label = { Text(candidate.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = digits,
                        onValueChange = { digits = it.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.editor_digits)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = period,
                        onValueChange = { period = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.editor_period)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (type == OtpType.HOTP) {
                OutlinedTextField(
                    value = counter,
                    onValueChange = { counter = it.filter(Char::isDigit).take(12) },
                    label = { Text(stringResource(R.string.editor_counter)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = folder,
                onValueChange = { folder = it },
                label = { Text(stringResource(R.string.editor_folder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (folders.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    folders.take(4).forEach { suggestion ->
                        AssistChip(
                            onClick = { folder = suggestion },
                            label = { Text(suggestion) }
                        )
                    }
                }
            }

            val preview = OtpEngine.code(
                secretBase32 = Account.normalizeSecret(secret),
                type = type,
                algorithm = algorithm,
                digits = digits.toIntOrNull() ?: 6,
                period = period.toIntOrNull() ?: 30,
                counter = counter.toLongOrNull() ?: 0L
            )
            if (Base32.isValid(Account.normalizeSecret(secret))) {
                Text(
                    stringResource(R.string.editor_preview, OtpEngine.formatCode(preview)),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(4.dp))
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.height(0.dp))
                Text("  " + stringResource(R.string.action_save))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && existing != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.editor_delete_title)) },
            text = {
                Text(stringResource(R.string.editor_delete_body, existing.displayIssuer))
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { confirmDelete = false; onDelete(existing) }
                ) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
