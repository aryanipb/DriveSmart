package com.aryan.v2v.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aryan.v2v.R

class PredictedCoordinatesFragment : Fragment(R.layout.fragment_predicted_coordinates) {
    private val uiState: V2VUiStateViewModel by activityViewModels()
    private val adapter = PredictedCoordinatesAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler: RecyclerView = view.findViewById(R.id.coordsRecycler)
        val summary: TextView = view.findViewById(R.id.coordsSummary)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        uiState.predictedRows.observe(viewLifecycleOwner) { rows ->
            adapter.submit(rows)
            val available = rows.count { it.x != null && it.y != null }
            summary.text = "Exact 30 live points from trajectory model | available=$available/30"
        }
    }
}
