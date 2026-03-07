package com.example.touchlock

import android.view.View
import android.widget.AdapterView

class SimpleItemSelected(private val onSelected: (index: Int) -> Unit) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        onSelected(position)
    }
    override fun onNothingSelected(parent: AdapterView<*>?) {}
}