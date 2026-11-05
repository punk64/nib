package com.yourname.nib.core.capture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.yourname.nib.core.db.NibGraph
import com.yourname.nib.core.organize.shareTextFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ShareCaptureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        val raw = shareTextFor(subject, text)
        if (raw != null) {
            runBlocking(Dispatchers.IO) {
                runCatching { NibGraph.repository(applicationContext).enqueueCapture(raw) }
            }
            Toast.makeText(this, "已保存到落笔", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
