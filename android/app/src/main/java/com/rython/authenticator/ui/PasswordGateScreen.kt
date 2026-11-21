package com.rython.authenticator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.rython.authenticator.R
import com.rython.authenticator.security.AppPasswords
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Entry gate: two passwords, asked one after the other. On first start the same screen
 * is used to create them, so the app always opens with a two-step password check.
 */
@Composable
fun PasswordGateScreen(
    setup: Boolean,
    onDone: () -> Unit,
    onQuit: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(0) }
    var value by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var firstPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val tooShort = stringResource(R.string.setup_too_short, AppPasswords.MIN_LENGTH)
    val mismatch = stringResource(R.string.setup_mismatch)
    val wrong = stringResource(R.string.unlock_wrong)

    fun reset() {
        value = ""
        repeated = ""
        error = null
        showPassword = false
    }

    fun submit() {
        if (busy) return
        if (setup) {
            if (value.length < AppPasswords.MIN_LENGTH) {
                error = tooShort
                return
            }
            // With the password visible a second entry adds nothing.
            if (!showPassword && value != repeated) {
                error = mismatch
                return
            }
            if (step == 0) {
                firstPassword = value
                step = 1
                reset()
            } else {
                busy = true
                scope.launch {
                    withContext(Dispatchers.Default) {
                        AppPasswords.save(context, firstPassword, value)
                    }
                    busy = false
                    onDone()
                }
            }
        } else {
            if (value.isBlank()) {
                error = wrong
                return
            }
            busy = true
            val candidate = value
            scope.launch {
                val accepted = withContext(Dispatchers.Default) {
                    if (step == 0) AppPasswords.verifyFirst(context, candidate)
                    else AppPasswords.verifySecond(context, candidate)
                }
                busy = false
                if (!accepted) {
                    error = wrong
                    value = ""
                } else if (step == 0) {
                    step = 1
                    reset()
                } else {
                    onDone()
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(if (setup) R.string.setup_title else R.string.unlock_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                stringResource(when {
                    setup && step == 0 -> R.string.setup_step1
                    setup -> R.string.setup_step2
                    step == 0 -> R.string.unlock_step1
                    else -> R.string.unlock_step2
                }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            OutlinedTextField(
                value = value,
                onValueChange = { value = it; error = null },
                label = { Text(stringResource(R.string.password_label)) },
                singleLine = true,
                enabled = !busy,
                visualTransformation = passwordTransformation(showPassword),
                trailingIcon = {
                    PasswordVisibilityToggle(visible = showPassword) { showPassword = !showPassword }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (showPassword) KeyboardType.Text else KeyboardType.Password,
                    imeAction = if (setup && !showPassword) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onNext = { },
                    onDone = { submit() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            )

            if (setup && !showPassword) {
                OutlinedTextField(
                    value = repeated,
                    onValueChange = { repeated = it; error = null },
                    label = { Text(stringResource(R.string.setup_repeat)) },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = passwordTransformation(showPassword),
                    trailingIcon = {
                        PasswordVisibilityToggle(visible = showPassword) { showPassword = !showPassword }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (showPassword) KeyboardType.Text else KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
            }

            if (error != null) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Text(
                stringResource(if (setup) R.string.setup_note else R.string.unlock_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )

            Button(
                onClick = { submit() },
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                Text(
                    stringResource(when {
                        setup && step == 0 -> R.string.setup_next
                        setup -> R.string.setup_done
                        step == 0 -> R.string.unlock_continue
                        else -> R.string.unlock_open
                    })
                )
            }

            TextButton(onClick = onQuit, enabled = !busy, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.lock_quit))
            }
        }
    }
}