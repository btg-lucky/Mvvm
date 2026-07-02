package com.btg.mvvm.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.btg.common.base.BaseFragment
import com.btg.mvvm.R
import com.btg.mvvm.databinding.FragmentHomeBinding

/** 演示入口：按钮跳转到各能力演示页。 */
class HomeFragment : BaseFragment<FragmentHomeBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentHomeBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnNews.setOnClickListener { findNavController().navigate(R.id.newsFragment) }
        // btnComponents / btnStorage 的跳转在 Task 4 / Task 5 接入目的地后补上
    }
}
