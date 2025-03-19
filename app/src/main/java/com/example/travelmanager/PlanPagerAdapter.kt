package com.example.travelmanager

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class PlanPagerAdapter(
    activity: FragmentActivity,
    private val daysCount: Int,
    private val tripId: String
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = daysCount

    override fun createFragment(position: Int): Fragment {
        // dayNumber = position + 1
        return PlanDayFragment.newInstance(position + 1, tripId)
    }
}
