package com.btg.news.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import com.btg.common.base.BaseFragment
import com.btg.news.databinding.FragmentNewsDetailBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NewsDetailFragment : BaseFragment<FragmentNewsDetailBinding>() {
    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNewsDetailBinding.inflate(inflater, container, false)
}
