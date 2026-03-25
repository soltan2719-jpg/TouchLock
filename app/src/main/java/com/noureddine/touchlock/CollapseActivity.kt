package com.noureddine.touchlock

import android.app.Activity
import android.os.Bundle

class CollapseActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This closes the activity immediately so the user never sees it
        finish()
    }
}
