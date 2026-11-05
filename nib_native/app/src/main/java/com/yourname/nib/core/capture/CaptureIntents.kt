package com.yourname.nib.core.capture

import android.content.Context
import android.content.Intent
import com.yourname.nib.MainActivity

const val ACTION_QUICK_CAPTURE = "com.yourname.nib.action.QUICK_CAPTURE"
const val EXTRA_NIB_TAB = "nib_tab"
const val EXTRA_NIB_ENTRY_ID = "nib_entry_id"

fun captureIntent(context: Context): Intent =
    Intent(context, QuickCaptureActivity::class.java)
        .setAction(ACTION_QUICK_CAPTURE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

fun mainIntent(context: Context, tab: String): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .putExtra(EXTRA_NIB_TAB, tab)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

fun detailIntent(context: Context, entryId: String): Intent =
    mainIntent(context, "all").putExtra(EXTRA_NIB_ENTRY_ID, entryId)
