package com.yourname.nib.core.capture

import android.annotation.TargetApi
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

@TargetApi(Build.VERSION_CODES.N)
class NibTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = captureIntent(this).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        startActivityAndCollapse(intent)
    }
}
