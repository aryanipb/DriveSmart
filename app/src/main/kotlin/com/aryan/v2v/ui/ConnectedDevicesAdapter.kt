package com.aryan.v2v.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aryan.v2v.R

class ConnectedDevicesAdapter : RecyclerView.Adapter<ConnectedDevicesAdapter.DeviceViewHolder>() {
    private var ids: List<String> = emptyList()

    fun submit(newIds: List<String>) {
        ids = newIds
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun getItemCount(): Int = if (ids.isEmpty()) 1 else ids.size

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        if (ids.isEmpty()) {
            holder.index.text = "Device"
            holder.id.text = "No connected endpoints"
            return
        }

        holder.index.text = "Device ${position + 1}"
        holder.id.text = ids[position]
    }

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val index: TextView = view.findViewById(R.id.deviceIndex)
        val id: TextView = view.findViewById(R.id.deviceId)
    }
}
