package com.example.tvapp.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList
import com.example.tvapp.data.ChannelRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Что видит MainActivity: список каналов и провал последней сетевой перезагрузки. */
internal data class MainUiState(
    val channels: List<Channel> = emptyList(),
    val refreshFailed: Boolean = false
)

/**
 * Загрузка каналов для MainActivity: сначала мгновенный диск-снимок, затем фоновая
 * перезагрузка. Состояние переживает реконфигурацию (ночная тема/язык), активность
 * только реагирует на изменения списка. ChannelList синхронизируется здесь — остальные
 * активности (EPG/Player/Search) пока читают синглтон.
 */
internal class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    private val repository = ChannelRepository(application)
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state

    init {
        loadFromDiskThenRefresh()
    }

    private fun loadFromDiskThenRefresh() {
        viewModelScope.launch {
            val disk = withContext(ioDispatcher) {
                runCatching { repository.loadFromDisk() }
                    .onFailure { Log.e(TAG, "disk load failed: ${it.message}") }
                    .getOrNull()
            }
            if (!disk.isNullOrEmpty()) {
                publish(disk)
            }
            refresh()
        }
    }

    /** Сетевая перезагрузка (онлайн) — заменяет список свежим, ошибки не сбрасывают текущий. */
    fun refresh() {
        viewModelScope.launch {
            try {
                val channels = withContext(ioDispatcher) { repository.getChannels() }
                if (channels.isNotEmpty()) {
                    publish(channels)
                }
                _state.update { it.copy(refreshFailed = false) }
            } catch (e: Exception) {
                Log.e(TAG, "channel refresh failed: ${e.message}")
                _state.update { it.copy(refreshFailed = true) }
            }
        }
    }

    private fun publish(channels: List<Channel>) {
        ChannelList.updateChannels(channels)
        _state.update { it.copy(channels = channels) }
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
