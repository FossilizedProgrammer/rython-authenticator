package com.rython.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rython.desktop.security.Passwords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Entry gate: two passwords, asked one after the other. On first start the same screen
 * creates them, so the app always opens with a two-step password check.
 */
@Composable
fun GateScreen(
    lang: Lang,
    setup: Boolean,
    onDone: () -> Unit,
    onQuit: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(0) }
    var value by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var firstPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val tooShort = lang.t(K.setupTooShort, Passwords.MIN_LENGTH)
    val mismatch = lang.t(K.setupMismatch)
    val wrong = lang.t(K.unlockWrong)

    fun reset() {
        value = ""
        repeated = ""
        error = null
        showPassword = false
    }

    fun submit() {
        if (busy) return
        if (setup) {
            if (value.length < Passwords.MIN_LENGTH) {
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
                    withContext(Dispatchers.Default) { Passwords.save(firstPassword, value) }
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
                    if (step == 0) Passwords.verifyFirst(candidate) else Passwords.verifySecond(candidate)
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
                .padding(horizontal = 32.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                lang.t(if (setup) K.setupTitle else K.unlockTitle),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
            Hint(
                when {
                    setup && step == 0 -> lang.t(K.setupStep1)
                    setup -> lang.t(K.setupStep2)
                    step == 0 -> lang.t(K.unlockStep1)
                    else -> lang.t(K.unlockStep2)
                }
            )
            Spacer(Modifier.height(8.dp))

            Column(modifier = Modifier.widthIn(max = 420.dp)) {
                PasswordField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = lang.t(K.passwordLabel),
                    visible = showPassword,
                    onToggleVisibility = { showPassword = !showPassword },
                    imeAction = if (setup && !showPassword) ImeAction.Next else ImeAction.Done,
                    onDone = { submit() }
                )
                if (setup && !showPassword) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedFieldForRepeat(
                        value = repeated,
                        onValueChange = { repeated = it; error = null },
                        label = lang.t(K.setupRepeat),
                        onSubmit = { submit() }
                    )
                }
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

            Hint(
                lang.t(if (setup) K.setupNote else K.unlockHint)
            )

            Button(
                onClick = { submit() },
                enabled = !busy,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                Text(
                    lang.t(
                        when {
                            setup && step == 0 -> K.setupNext
                            setup -> K.setupDone
                            step == 0 -> K.unlockContinue
                            else -> K.unlockOpen
                        }
                    )
                )
            }

            TextButton(onClick = onQuit, enabled = !busy) {
                Text(lang.t(K.actionQuit))
            }
        }
    }
}

@Composable
private fun OutlinedFieldForRepeat(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onSubmit: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    PasswordField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        visible = visible,
        onToggleVisibility = { visible = !visible },
        imeAction = ImeAction.Done,
        onDone = onSubmit
    )
}
