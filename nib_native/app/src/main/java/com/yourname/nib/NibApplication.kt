package com.yourname.nib

import android.app.Application
import com.yourname.nib.core.CrashCatcher

class NibApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { CrashCatcher.install(this) }
    }
}
