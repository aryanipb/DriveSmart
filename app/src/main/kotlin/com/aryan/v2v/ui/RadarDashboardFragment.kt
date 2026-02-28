package com.aryan.v2v.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.aryan.v2v.R
import com.aryan.v2v.RadarView

class RadarDashboardFragment : Fragment(R.layout.fragment_radar_dashboard) {
    private val uiState: V2VUiStateViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val radarView: RadarView = view.findViewById(R.id.radarView)
        val statusText: TextView = view.findViewById(R.id.statusText)
        val modelOutputText: TextView = view.findViewById(R.id.modelOutputText)

        uiState.dashboardState.observe(viewLifecycleOwner) { state ->
            radarView.render(state.ego, state.neighbors, state.prediction)
            statusText.text = state.statusText
            modelOutputText.text = state.modelText
        }
    }
}
