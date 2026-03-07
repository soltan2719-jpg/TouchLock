package com.example.touchlock

import android.widget.SeekBar

class SimpleSeekBar(private val onChange: (Int) -> Unit) : SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        onChange(progress)
    }
    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
}