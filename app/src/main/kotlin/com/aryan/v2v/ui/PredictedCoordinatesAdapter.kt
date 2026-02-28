package com.aryan.v2v.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aryan.v2v.R
import java.util.Locale

class PredictedCoordinatesAdapter : RecyclerView.Adapter<PredictedCoordinatesAdapter.CoordViewHolder>() {
    private var rows: List<CoordinateRow> = emptyList()

    fun submit(newRows: List<CoordinateRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_coordinate, parent, false)
        return CoordViewHolder(view)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: CoordViewHolder, position: Int) {
        val row = rows[position]
        holder.index.text = String.format(Locale.US, "%02d", row.index)
        if (row.x != null && row.y != null) {
            holder.x.text = String.format(Locale.US, "x=%9.3f", row.x)
            holder.y.text = String.format(Locale.US, "y=%9.3f", row.y)
        } else {
            holder.x.text = "x=   n/a"
            holder.y.text = "y=   n/a"
        }
    }

    class CoordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val index: TextView = view.findViewById(R.id.pointIndex)
        val x: TextView = view.findViewById(R.id.pointX)
        val y: TextView = view.findViewById(R.id.pointY)
    }
}
