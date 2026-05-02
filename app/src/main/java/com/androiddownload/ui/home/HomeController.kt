package com.androiddownload.ui.home

import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class HomeController(
    private val urlInput: EditText,
    private val downloadButton: Button,
    private val errorText: TextView
) {
    var onDownloadClick: (String) -> Unit = {}

    init {
        downloadButton.setOnClickListener {
            errorText.visibility = View.GONE
            onDownloadClick(urlInput.text.toString())
        }
    }

    fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    fun setUrl(url: String) {
        urlInput.setText(url)
        urlInput.setSelection(urlInput.text.length)
        errorText.visibility = View.GONE
    }

    fun setLoading(isLoading: Boolean) {
        downloadButton.isEnabled = !isLoading
        urlInput.isEnabled = !isLoading
    }

    fun clear() {
        urlInput.text.clear()
        errorText.visibility = View.GONE
    }
}
