package com.example.tvapp.ui

import android.content.Context
import com.example.tvapp.R
import com.example.tvapp.data.Channel

object ChannelGrouping {

    fun categoriesInOrder(channels: List<Channel>): List<String> {
        val seen = LinkedHashSet<String>()
        for (channel in channels) seen.add(channel.category)
        return seen.toList()
    }

    fun channelsByCategory(channels: List<Channel>, category: String): List<Channel> = channels.filter { it.category == category }

    fun buildItems(channels: List<Channel>, headerTitle: (String) -> String): List<GridItem> {
        val result = mutableListOf<GridItem>()
        for (category in categoriesInOrder(channels)) {
            result.add(GridItem.Header(category, headerTitle(category)))
            channelsByCategory(channels, category).forEach { result.add(GridItem.Card(it)) }
        }
        return result
    }

    fun favoriteChannels(channels: List<Channel>, favorites: Set<String>): List<Channel> = channels.filter { it.id in favorites }

    fun historyChannels(channels: List<Channel>, history: List<String>): List<Channel> = history.mapNotNull { id -> channels.find { it.id == id } }
}

fun categoryLabel(context: Context, category: String): String {
    val resId = when (category) {
        "federal" -> R.string.category_federal
        "news" -> R.string.category_news
        "sport" -> R.string.category_sport
        "kids" -> R.string.category_kids
        "music" -> R.string.category_music
        "entertainment" -> R.string.category_entertainment
        "culture" -> R.string.category_culture
        else -> R.string.category_general
    }
    return context.getString(resId)
}
