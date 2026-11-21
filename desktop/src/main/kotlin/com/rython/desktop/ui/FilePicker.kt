package com.rython.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rython.desktop.app.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class FilePickerMode { Open, Save }

private const val PICKER_DIR_KEY = "picker_dir"

/**
 * In-app file browser. The Swing/AWT file chooser showed a blank, unreadable window in
 * this app, so the picker is drawn with the same Compose theme as the rest of the UI.
 */
@Composable
fun FilePickerDialog(
    lang: Lang,
    mode: FilePickerMode,
    extension: String,
    defaultName: String = "",
    onDismiss: () -> Unit,
    onPicked: (File) -> Unit
) {
    var dir by remember { mutableStateOf(startDirectory()) }
    var showAll by remember { mutableStateOf(false) }
    var entries by remember(dir) { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember(dir) { mutableStateOf(true) }
    var selected by remember(dir) { mutableStateOf<File?>(null) }
    var name by remember { mutableStateOf(defaultName) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dir, showAll) {
        loading = true
        entries = withContext(Dispatchers.IO) { listEntries(dir, extension, showAll) }
        loading = false
    }

    fun goTo(folder: File) {
        dir = folder
        selected = null
        error = null
        runCatching { Prefs.putString(PICKER_DIR_KEY, folder.absolutePath) }
    }

    fun confirm() {
        if (mode == FilePickerMode.Open) {
            val file = selected
            if (file == null) error = lang.t(K.pickerNoSelection) else onPicked(file)
            return
        }
        val typed = name.trim()
        if (typed.isEmpty()) {
            error = lang.t(K.pickerNoName)
            return
        }
        val fileName = if (typed.endsWith(".$extension", ignoreCase = true)) typed else "$typed.$extension"
        val target = File(dir, fileName)
        if (target.isDirectory) {
            error = lang.t(K.pickerNoName)
            return
        }
        onPicked(target)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(lang.t(if (mode == FilePickerMode.Open) K.pickerTitleOpen else K.pickerTitleSave))
        },
        text = {
            Column(
                modifier = Modifier.width(440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { dir.parentFile?.let { goTo(it) } },
                        enabled = dir.parentFile != null
                    ) { Text(lang.t(K.pickerUp)) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = dir.absolutePath,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                LazyColumn(modifier = Modifier.height(240.dp).fillMaxWidth()) {
                    when {
                        loading -> item { Hint(lang.t(K.pickerLoading)) }
                        entries.isEmpty() -> item { Hint(lang.t(K.pickerEmpty)) }
                        else -> items(entries, key = { it.absolutePath }) { entry ->
                            val isSelected = selected?.absolutePath == entry.absolutePath
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                    )
                                    .clickable {
                                        error = null
                                        if (entry.isDirectory) {
                                            goTo(entry)
                                        } else {
                                            selected = entry
                                            if (mode == FilePickerMode.Save) name = entry.name
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal,
                                    color = if (entry.isDirectory) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (entry.isDirectory) {
                                    Text(
                                        text = "/",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                if (mode == FilePickerMode.Save) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; error = null },
                        label = { Text(lang.t(K.pickerFileName)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Hint(
                        lang.t(
                            K.pickerSelected,
                            selected?.name ?: lang.t(K.pickerNoSelection)
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        lang.t(K.pickerAllFiles, extension),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = showAll,
                        onCheckedChange = { showAll = it; selected = null }
                    )
                }

                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }) {
                Text(lang.t(if (mode == FilePickerMode.Open) K.pickerOpen else K.actionSave))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(lang.t(K.actionCancel)) }
        }
    )
}

private fun startDirectory(): File {
    val saved = runCatching { Prefs.getString(PICKER_DIR_KEY) }.getOrNull()
    val candidates = listOfNotNull(
        saved?.let { File(it) },
        File(System.getProperty("user.home") ?: ".")
    )
    return candidates.firstOrNull { it.isDirectory } ?: File(".")
}

private fun listEntries(dir: File, extension: String, showAll: Boolean): List<File> {
    val children = runCatching { dir.listFiles() }.getOrNull() ?: return emptyList()
    return children
        .filter { child ->
            runCatching {
                child.isDirectory || showAll || child.name.endsWith(".$extension", ignoreCase = true)
            }.getOrDefault(false)
        }
        .sortedWith(
            compareByDescending<File> { runCatching { it.isDirectory }.getOrDefault(false) }
                .thenBy { it.name.lowercase() }
        )
}
