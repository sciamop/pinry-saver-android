package com.pinrysaver.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pinrysaver.R
import com.pinrysaver.data.PinryRepository
import com.pinrysaver.data.ValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SettingsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    interface SettingsListener {
        fun onSettingsSaved(readOnly: Boolean)
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

        viewLifecycleOwner.lifecycleScope.launch {
            statusText.isVisible = false
            toggleLoading(true)
            try {
                val validation = repository.validatePinryServer(normalizedUrl, apiToken.ifBlank { null })
                toggleLoading(false)
                handleValidationResult(normalizedUrl, apiToken, boardId, validation)
            } catch (cancellation: CancellationException) {
                toggleLoading(false)
                throw cancellation
            } catch (ex: Exception) {
                toggleLoading(false)
                statusText.isVisible = true
                statusText.text = ex.localizedMessage ?: getString(R.string.error_generic)
            }
        }
    }

    private fun handleValidationResult(
        normalizedUrl: String,
        apiToken: String,
        boardId: String,
        validation: ValidationResult
    ) {
        when {
            !validation.urlValid -> {
                statusText.isVisible = true
                statusText.text = validation.errorMessage ?: getString(R.string.error_generic)
            }
            validation.requiresToken && apiToken.isBlank() -> {
                showTokenWarningDialog(
                    titleRes = R.string.settings_token_required_title,
                    messageRes = R.string.settings_token_required_message
                ) {
                    persistSettings(normalizedUrl, "", boardId, readOnly = true)
                }
            }
            !validation.tokenValid && apiToken.isNotBlank() -> {
                showTokenWarningDialog(
                    titleRes = R.string.settings_token_invalid_title,
                    messageRes = R.string.settings_token_invalid_message
                ) {
                    persistSettings(normalizedUrl, "", boardId, readOnly = true)
                }
            }
            else -> {
                persistSettings(normalizedUrl, apiToken, boardId, readOnly = false)
            }
        }
    }

    private fun showTokenWarningDialog(
        titleRes: Int,
        messageRes: Int,
        onContinue: () -> Unit
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setCancelable(false)
            .setNegativeButton(R.string.settings_return, null)
            .setPositiveButton(R.string.settings_continue_read_only) { _, _ ->
                onContinue()
            }
            .show()
    }

    private fun persistSettings(url: String, token: String, board: String, readOnly: Boolean) {
        settingsManager.saveSettings(url, token, board)
        listener?.onSettingsSaved(readOnly)
        dismissAllowingStateLoss()
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

