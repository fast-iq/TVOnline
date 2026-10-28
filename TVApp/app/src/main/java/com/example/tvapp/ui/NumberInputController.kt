package com.example.tvapp.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.example.tvapp.R
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList

class NumberInputController(
    private val context: Context,
    private val overlay: TextView,
    private val onSelected: (Channel) -> Unit
) {

    private val typedDigits = StringBuilder()
    private val handler = Handler(Looper.getMainLooper())
    private val commitRunnable = Runnable { commit() }

    fun onDigit(digit: Char) {
        if (typedDigits.length >= ChannelNumberInput.MAX_DIGITS) return
        typedDigits.append(digit)
        updateOverlay()
        handler.removeCallbacks(commitRunnable)
        handler.postDelayed(commitRunnable, COMMIT_DELAY_MS)
        if (typedDigits.length >= ChannelNumberInput.MAX_DIGITS) {
            commit()
        }
    }

    fun isActive(): Boolean = typedDigits.isNotEmpty()

    fun cancel() {
        clear()
    }

    fun release() {
        handler.removeCallbacks(commitRunnable)
        typedDigits.setLength(0)
    }

    private fun commit() {
        val digits = typedDigits.toString()
        clear()
        if (digits.isEmpty()) return
        val channel = ChannelNumberInput.select(ChannelList.channels, digits) ?: return
        onSelected(channel)
    }

    private fun clear() {
        typedDigits.setLength(0)
        handler.removeCallbacks(commitRunnable)
        overlay.visibility = View.GONE
        overlay.text = ""
    }

    private fun updateOverlay() {
        if (typedDigits.isEmpty()) {
            overlay.visibility = View.GONE
            overlay.text = ""
            return
        }
        val digits = typedDigits.toString()
        val matches = ChannelNumberInput.matches(ChannelList.channels, digits)
        val sb = StringBuilder(context.getString(R.string.channel_number_typing, digits))
        if (matches.isEmpty()) {
            sb.append('\n').append(context.getString(R.string.channel_number_no_match))
        } else {
            matches.take(MAX_MATCHES_SHOWN).forEach { channel ->
                sb.append('\n').append(context.getString(R.string.channel_number_item, channel.number, channel.name))
            }
        }
        overlay.text = sb
        overlay.visibility = View.VISIBLE
    }

    companion object {
        private const val COMMIT_DELAY_MS = 1200L
        private const val MAX_MATCHES_SHOWN = 4
    }
}
