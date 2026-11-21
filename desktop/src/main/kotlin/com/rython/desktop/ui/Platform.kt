package com.rython.desktop.ui

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/** Desktop equivalents of the Android clipboard and document pickers. */
object Platform {

    fun clipboardText(): String? = runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return null
        (clipboard.getData(DataFlavor.stringFlavor) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun setClipboard(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

}
