package com.aryan.v2v.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aryan.v2v.R

class ConnectedDevicesFragment : Fragment(R.layout.fragment_connected_devices) {
    private val uiState: V2VUiStateViewModel by activityViewModels()
    private val adapter = ConnectedDevicesAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler: RecyclerView = view.findViewById(R.id.deviceRecycler)
        val summary: TextView = view.findViewById(R.id.deviceSummary)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        uiState.connectedDeviceIds.observe(viewLifecycleOwner) { ids ->
            adapter.submit(ids)
            summary.text = "Live endpoint IDs from active V2V mesh | connected=${ids.size}"
        }
    }
}
