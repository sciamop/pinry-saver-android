package com.pinrysaver.ui.gallery

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.pinrysaver.R
import com.pinrysaver.ui.settings.SettingsBottomSheetDialogFragment

class GalleryFragment : Fragment() {

    private val viewModel: GalleryViewModel by activityViewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PinAdapter
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var errorText: TextView
    private lateinit var settingsButton: ImageButton
    private lateinit var logoButton: ImageView

    private var settingsListener: SettingsBottomSheetDialogFragment.SettingsListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        settingsListener = object : SettingsBottomSheetDialogFragment.SettingsListener {
            override fun onSettingsSaved() {
                viewModel.loadInitial(force = true)
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        settingsListener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_gallery, container, false)
        recyclerView = root.findViewById(R.id.pinRecyclerView)
        loadingIndicator = root.findViewById(R.id.loadingIndicator)
        errorText = root.findViewById(R.id.errorText)
        swipeRefreshLayout = root.findViewById(R.id.swipeRefresh)
        settingsButton = root.findViewById(R.id.settingsButton)
        logoButton = root.findViewById(R.id.logoButton)

        adapter = PinAdapter { position ->
            openFullscreen(position)
        }

        val orientation = resources.configuration.orientation
        val spanCount = if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 3 else 2
        recyclerView.layoutManager = StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(false)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy >= 0) {
                    val layoutManager = recyclerView.layoutManager as? StaggeredGridLayoutManager ?: return
                    val lastPositions = layoutManager.findLastVisibleItemPositions(null)
                    val maxPosition = lastPositions.maxOrNull() ?: return
                    viewModel.loadMoreIfNeeded(maxPosition)
                }
            }
        })

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh()
        }

        settingsButton.setOnClickListener {
            showSettingsSheet()
        }

        logoButton.setOnClickListener {
            recyclerView.smoothScrollToPosition(0)
        }

        observeViewModel()

        return root
    }

    override fun onResume() {
        super.onResume()
        if (!viewModel.hasConfiguredServer()) {
            // No settings yet, prompt immediately
            view?.post { showSettingsSheet() }
        } else {
            viewModel.loadInitial()
        }
    }

    private fun observeViewModel() {
        viewModel.pins.observe(viewLifecycleOwner) { pins ->
            adapter.submitList(pins)
            if (pins.isEmpty() && !viewModel.loadingInitial.value.orFalse()) {
                errorText.isVisible = true
                errorText.text = getString(R.string.empty_gallery_message)
            } else if (pins.isNotEmpty()) {
                errorText.isGone = true
            }
        }

        viewModel.loadingInitial.observe(viewLifecycleOwner) { isLoading ->
            loadingIndicator.isVisible = isLoading
            swipeRefreshLayout.isEnabled = !isLoading
        }

        viewModel.refreshing.observe(viewLifecycleOwner) { refreshing ->
            swipeRefreshLayout.isRefreshing = refreshing
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrBlank()) {
                errorText.isVisible = adapter.itemCount == 0
                errorText.text = error
                view?.let { Snackbar.make(it, error, Snackbar.LENGTH_LONG).show() }
            } else if (adapter.itemCount > 0) {
                errorText.isGone = true
            }
        }
    }

    private fun openFullscreen(position: Int) {
        val fragmentManager = parentFragmentManager
        val existing = fragmentManager.findFragmentByTag(FullscreenPinDialogFragment.TAG)
        if (existing != null) return

        FullscreenPinDialogFragment.newInstance(position).show(fragmentManager, FullscreenPinDialogFragment.TAG)
    }

    private fun showSettingsSheet() {
        if (parentFragmentManager.findFragmentByTag(SettingsBottomSheetDialogFragment.TAG) != null) {
            return
        }
        SettingsBottomSheetDialogFragment.newInstance().apply {
            listener = settingsListener
        }.show(parentFragmentManager, SettingsBottomSheetDialogFragment.TAG)
    }

    private fun Boolean?.orFalse(): Boolean = this ?: false
}

