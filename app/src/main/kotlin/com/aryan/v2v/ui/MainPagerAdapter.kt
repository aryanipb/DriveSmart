package com.aryan.v2v.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> RadarDashboardFragment()
            1 -> ConnectedDevicesFragment()
            else -> PredictedCoordinatesFragment()
        }
    }
}
