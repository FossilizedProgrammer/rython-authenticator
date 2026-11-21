package com.rython.authenticator

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.rython.authenticator.ui.AppLocale
import com.rython.authenticator.ui.RythonRoot
import com.rython.authenticator.ui.RythonTheme

class MainActivity : FragmentActivity() {

    /** Applies the language picked in settings before the views/resources are created. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = (application as RythonApp).vault
        setContent {
            // Layout direction follows the locale: RTL for Persian, LTR otherwise.
            RythonTheme(dark = false) {
                RythonRoot(store = store)
            }
        }
    }
}
