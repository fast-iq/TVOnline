package com.example.tvapp.ui

import com.example.tvapp.data.Channel

object ChannelNumberInput {

    const val MAX_DIGITS = 3

    fun matches(channels: List<Channel>, digits: String): List<Channel> {
        if (digits.isEmpty()) return emptyList()
        return channels
            .filter { it.number > 0 && it.number.toString().startsWith(digits) }
            .sortedBy { it.number }
    }

    fun select(channels: List<Channel>, digits: String): Channel? {
        val matched = matches(channels, digits)
        return matched.firstOrNull { it.number.toString() == digits } ?: matched.firstOrNull()
    }
}
