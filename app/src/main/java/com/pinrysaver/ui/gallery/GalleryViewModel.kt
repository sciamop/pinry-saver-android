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
    private var prefetchJob: Job? = null
    private var prefetchedPage: PrefetchedPage? = null

    fun hasConfiguredServer(): Boolean = repository.getSettings().isConfigured()

    fun loadInitial(force: Boolean = false) {
        if (!force && (_pins.value?.isNotEmpty() == true || currentJob?.isActive == true)) {
            return
        }

        prefetchJob?.cancel()
        prefetchJob = null
        prefetchedPage = null

        _loadingInitial.value = true
        _errorMessage.value = null
        currentJob?.cancel()

        currentJob = viewModelScope.launch {
            val offset = 0
            val result = repository.fetchPins(offset = offset, limit = PAGE_SIZE)
            applyResult(result, replace = true, requestedOffset = offset, allowPrefetch = true)
            _loadingInitial.value = false
        }
    }

    fun refresh() {
        if (_refreshing.value == true) return

        _refreshing.value = true

        prefetchJob?.cancel()
        prefetchJob = null
        prefetchedPage = null

        viewModelScope.launch {
            val currentCount = _pins.value?.size?.coerceAtLeast(PAGE_SIZE) ?: PAGE_SIZE
            val result = repository.fetchPins(offset = 0, limit = currentCount)
            applyResult(result, replace = true, requestedOffset = 0, allowPrefetch = true)
            _refreshing.value = false
        }
    }

    fun loadMoreIfNeeded(position: Int, fastScroll: Boolean) {
        if (!hasMore || currentJob?.isActive == true) return

        val total = _pins.value?.size ?: 0
        if (total == 0 || position < total - LOAD_MORE_THRESHOLD) return

        prefetchedPage?.let { prefetched ->
            if (prefetched.offset == total) {
                prefetchedPage = null
                applyResult(prefetched.result, replace = false, requestedOffset = prefetched.offset, allowPrefetch = !fastScroll)
                return
            }
        }

        currentJob = viewModelScope.launch {
            val result = repository.fetchPins(offset = total, limit = PAGE_SIZE)
            applyResult(result, replace = false, requestedOffset = total, allowPrefetch = !fastScroll)
        }
    }

    private fun applyResult(
        result: PinFetchResult,
        replace: Boolean,
        requestedOffset: Int,
        allowPrefetch: Boolean
    ) {
        if (result.success) {
            hasMore = result.hasMore
            _errorMessage.value = null
            val base = if (replace) emptyList() else _pins.value.orEmpty()
            _pins.value = base + result.pins
            if (replace) {
                prefetchedPage = null
            }
            if (allowPrefetch && result.hasMore) {
                val nextOffset = requestedOffset + result.pins.size
                maybePrefetch(nextOffset)
            }
        } else {
            _errorMessage.value = result.error
            if (replace) {
                _pins.value = emptyList()
            }
            hasMore = false
        }
    }

    private fun maybePrefetch(nextOffset: Int) {
        if (prefetchJob?.isActive == true) return
        if (!hasMore) return
        prefetchedPage?.let { if (it.offset == nextOffset && it.result.success) return }

        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            try {
                val result = repository.fetchPins(offset = nextOffset, limit = PAGE_SIZE)
                if (result.success) {
                    prefetchedPage = PrefetchedPage(nextOffset, result)
                }
            } finally {
                prefetchJob = null
            }
        }
    }

    private data class PrefetchedPage(val offset: Int, val result: PinFetchResult)

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
        prefetchJob?.cancel()
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val LOAD_MORE_THRESHOLD = 5
    }
}

