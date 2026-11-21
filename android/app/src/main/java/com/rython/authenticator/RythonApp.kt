package com.rython.authenticator

import android.app.Application
import com.rython.authenticator.data.VaultStore

class RythonApp : Application() {
    val vault: VaultStore by lazy { VaultStore(this) }
}
