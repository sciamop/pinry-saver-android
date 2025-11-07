package com.pinrysaver.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.pinrysaver.R
import com.pinrysaver.data.PinryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SettingsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    interface SettingsListener {
        fun onSettingsSaved()
    }

    var listener: SettingsListener? = null

    private lateinit var pinryUrlInput: EditText
    private lateinit var apiTokenInput: EditText
    private lateinit var boardIdInput: EditText
    private lateinit var saveButton: Button
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    private val repository by lazy { PinryRepository.getInstance(requireContext()) }
    private val settingsManager by lazy { repository.getSettings() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pinryUrlInput = view.findViewById(R.id.inputPinryUrl)
        apiTokenInput = view.findViewById(R.id.inputApiToken)
        boardIdInput = view.findViewById(R.id.inputBoardId)
        saveButton = view.findViewById(R.id.saveSettingsButton)
        statusText = view.findViewById(R.id.settingsStatus)
        progressBar = view.findViewById(R.id.settingsProgress)

        populateFields()

        saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun populateFields() {
        pinryUrlInput.setText(settingsManager.getPinryUrl())
        apiTokenInput.setText(settingsManager.getApiToken())
        boardIdInput.setText(settingsManager.getBoardId())
    }

    private fun saveSettings() {
        val rawUrl = pinryUrlInput.text.toString().trim()
        val apiToken = apiTokenInput.text.toString().trim()
        val boardId = boardIdInput.text.toString().trim()

        if (rawUrl.isEmpty()) {
            statusText.isVisible = true
            statusText.text = getString(R.string.error_url_required)
            return
        }

        val normalizedUrl = normalizeUrl(rawUrl)

        statusText.isVisible = false
        toggleLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val validation = repository.validatePinryServer(normalizedUrl, apiToken.ifBlank { null })
                if (validation.isValid) {
                    settingsManager.saveSettings(normalizedUrl, apiToken, boardId)
                    listener?.onSettingsSaved()
                    Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
                    dismissAllowingStateLoss()
                } else {
                    statusText.isVisible = true
                    statusText.text = validation.errorMessage ?: getString(R.string.error_generic)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (ex: Exception) {
                statusText.isVisible = true
                statusText.text = ex.localizedMessage ?: getString(R.string.error_generic)
            } finally {
                toggleLoading(false)
            }
        }
    }

    private fun toggleLoading(isLoading: Boolean) {
        progressBar.isVisible = isLoading
        saveButton.isEnabled = !isLoading
        pinryUrlInput.isEnabled = !isLoading
        apiTokenInput.isEnabled = !isLoading
        boardIdInput.isEnabled = !isLoading
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed.removeSuffix("/")
        } else {
            "https://" + trimmed.removeSuffix("/")
        }
    }

    companion object {
        const val TAG = "PinrySettingsSheet"

        fun newInstance(): SettingsBottomSheetDialogFragment = SettingsBottomSheetDialogFragment()
    }
}

