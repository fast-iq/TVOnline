package com.example.tvapp.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.example.tvapp.BaseActivity
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList
import java.util.Locale

class SearchActivity : BaseActivity() {

    private lateinit var searchInput: EditText
    private lateinit var resultsList: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var resultsAdapter: SearchResultAdapter

    private val preferences by lazy { AppPreferences(applicationContext) }

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val query = voiceResultToQuery(result.data)
            if (!query.isNullOrBlank()) {
                searchInput.setText(query)
                searchInput.setSelection(searchInput.text.length)
                resultsList.requestFocus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        searchInput = findViewById(R.id.searchInput)
        resultsList = findViewById(R.id.searchResultsList)
        emptyText = findViewById(R.id.searchEmptyText)
        findViewById<ImageView>(R.id.voiceButton).setOnClickListener { startVoiceSearch() }

        resultsAdapter = SearchResultAdapter(filterChannels("")) { channel -> openChannel(channel) }
        resultsList.layoutManager = LinearLayoutManager(this)
        resultsList.adapter = resultsAdapter
        resultsList.itemAnimator = null

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateResults(s?.toString().orEmpty())
            }
        })

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                resultsList.requestFocus()
                true
            } else {
                false
            }
        }

        searchInput.requestFocus()
    }

    private fun updateResults(query: String) {
        val filtered = filterChannels(query)
        resultsAdapter.updateChannels(filtered)
        emptyText.visibility = if (filtered.isEmpty() && query.isNotBlank()) View.VISIBLE else View.GONE
    }

    /** Голосовой поиск через системный RecognizerIntent (голосовые пульты Android TV). */
    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openChannel(channel: Channel) {
        preferences.lastChannelId = channel.id
        preferences.addToWatchHistory(channel.id)
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("channel_id", channel.id)
            putExtra("channel_name", channel.name)
            putExtra("stream_url", channel.streamUrl)
            putExtra("logo_url", channel.logoUrl)
        }
        startActivity(intent)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> {
            if (searchInput.text.isNotEmpty()) {
                searchInput.setText("")
                true
            } else {
                super.onKeyDown(keyCode, event)
            }
        }
        else -> super.onKeyDown(keyCode, event)
    }

    class SearchResultAdapter(
        private var channels: List<Channel>,
        private val onSelect: (Channel) -> Unit
    ) : RecyclerView.Adapter<SearchResultAdapter.ResultHolder>() {

        class ResultHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val logo: ImageView = view.findViewById(R.id.searchItemLogo)
            private val number: TextView = view.findViewById(R.id.searchItemNumber)
            private val name: TextView = view.findViewById(R.id.searchItemName)

            fun bind(channel: Channel, onSelect: (Channel) -> Unit) {
                name.text = channel.name
                if (channel.number > 0) {
                    number.text = itemView.context.getString(R.string.channel_number_format, channel.number)
                    number.visibility = View.VISIBLE
                } else {
                    number.visibility = View.GONE
                }
                Glide.with(itemView.context)
                    .load(channel.logoUrl)
                    // Логотипу хватает RGB_565 — вдвое меньше памяти на битмап.
                    .format(DecodeFormat.PREFER_RGB_565)
                    .placeholder(R.drawable.ic_channel_placeholder)
                    .error(R.drawable.ic_channel_placeholder)
                    .into(logo)
                itemView.setOnClickListener { onSelect(channel) }
                itemView.setOnFocusChangeListener { v, hasFocus ->
                    v.scaleX = if (hasFocus) 1.02f else 1.0f
                    v.scaleY = if (hasFocus) 1.02f else 1.0f
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_result, parent, false)
            return ResultHolder(view)
        }

        override fun onBindViewHolder(holder: ResultHolder, position: Int) {
            holder.bind(channels[position], onSelect)
        }

        override fun getItemCount(): Int = channels.size

        fun updateChannels(newChannels: List<Channel>) {
            channels = newChannels
            notifyDataSetChanged()
        }
    }

    companion object {
        fun filterChannels(query: String): List<Channel> {
            val normalized = query.trim().lowercase().replace(" ", "")
            if (normalized.isEmpty()) return ChannelList.channels
            return ChannelList.channels.filter { channel ->
                channel.name.lowercase().replace(" ", "").contains(normalized)
            }
        }

        /** Реплика распознавателя -> строка запроса (первая и единственная). */
        fun voiceResultToQuery(data: Intent?): String? = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }
    }
}
