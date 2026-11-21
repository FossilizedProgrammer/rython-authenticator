package com.rython.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.rython.desktop.data.Vault
import com.rython.desktop.ui.L
import com.rython.desktop.ui.RythonTheme
import com.rython.desktop.ui.Root

fun main() = application {
    val vault = remember { Vault() }
    var lang by remember { mutableStateOf(L.current()) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Rython-Authenticator",
        icon = painterResource("icons/icon.png"),
        state = rememberWindowState(width = 480.dp, height = 860.dp)
    ) {
        RythonTheme {
            Root(
                vault = vault,
                lang = lang,
                onLangChange = { selected ->
                    lang = selected
                    L.select(selected.code)
                }
            )
        }
    }
}
