package com.yourname.nib.core.capture

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.yourname.nib.R
import com.yourname.nib.core.db.NibGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class QuickCaptureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.TOP)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val title = TextView(this).apply {
            text = getString(R.string.app_name) + " · 速记"
            textSize = 16f
        }
        val input = EditText(this).apply {
            hint = getString(R.string.capture_placeholder)
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val hint = TextView(this).apply {
            text = captureHint("")
            textSize = 12f
            setPadding(0, pad / 2, 0, pad / 2)
        }
        val save = Button(this).apply {
            text = getString(R.string.capture_save)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        root.addView(title)
        root.addView(input)
        root.addView(hint)
        root.addView(save)
        setContentView(root)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                hint.text = captureHint(s?.toString().orEmpty())
            }
        })
        save.setOnClickListener {
            val raw = input.text.toString().trim()
            if (raw.isNotEmpty()) {
                runBlocking(Dispatchers.IO) {
                    runCatching { NibGraph.repository(applicationContext).enqueueCapture(raw) }
                }
            }
            finish()
        }
        input.requestFocus()
    }
}
