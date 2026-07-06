package com.btg.mine.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import com.btg.common.base.BaseFragment
import com.btg.mine.databinding.FragmentMineBinding

/** 我的页占位:阶段 3 实现登录/注册。 */
class MineFragment : BaseFragment<FragmentMineBinding>() {
    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentMineBinding.inflate(inflater, container, false)
}
