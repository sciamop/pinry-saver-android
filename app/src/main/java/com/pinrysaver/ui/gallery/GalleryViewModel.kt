package com.pinrysaver.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pinrysaver.data.PinFetchResult
import com.pinrysaver.data.PinryRepository
import com.pinrysaver.data.model.PinryPin
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PinryRepository.getInstance(application)

    private val _pins = MutableLiveData<List<PinryPin>>(emptyList())
    val pins: LiveData<List<PinryPin>> = _pins

    private val _loadingInitial = MutableLiveData(false)
    val loadingInitial: LiveData<Boolean> = _loadingInitial

    private val _refreshing = MutableLiveData(false)
    val refreshing: LiveData<Boolean> = _refreshing

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    private var hasMore = true
    private var currentJob: Job? = null

    fun hasConfiguredServer(): Boolean = repository.getSettings().isConfigured()

    fun loadInitial(force: Boolean = false) {
        if (!force && (_pins.value?.isNotEmpty() == true || currentJob?.isActive == true)) {
            return
        }

        _loadingInitial.value = true
        _errorMessage.value = null
        currentJob?.cancel()

        currentJob = viewModelScope.launch {
            val result = repository.fetchPins(offset = 0, limit = PAGE_SIZE)
            handleResult(result, replace = true)
            _loadingInitial.value = false
        }
    }

    fun refresh() {
        if (_refreshing.value == true) return

        _refreshing.value = true

        viewModelScope.launch {
            val currentCount = _pins.value?.size?.coerceAtLeast(PAGE_SIZE) ?: PAGE_SIZE
            val result = repository.fetchPins(offset = 0, limit = currentCount)
            handleResult(result, replace = true)
            _refreshing.value = false
        }
    }

    fun loadMoreIfNeeded(position: Int) {
        if (!hasMore || currentJob?.isActive == true) return

        val total = _pins.value?.size ?: 0
        if (total == 0 || position < total - LOAD_MORE_THRESHOLD) return

        currentJob = viewModelScope.launch {
            val result = repository.fetchPins(offset = total, limit = PAGE_SIZE)
            handleResult(result, replace = false)
        }
    }

    private fun handleResult(result: PinFetchResult, replace: Boolean) {
        if (result.success) {
            hasMore = result.hasMore
            _errorMessage.value = null
            val base = if (replace) emptyList() else _pins.value.orEmpty()
            _pins.value = base + result.pins
        } else {
            _errorMessage.value = result.error
            if (replace) {
                _pins.value = emptyList()
            }
            hasMore = false
        }
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val LOAD_MORE_THRESHOLD = 5
    }
}

