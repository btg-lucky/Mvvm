package com.btg.common.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

/**
 * 承载 ViewBinding 的 Fragment 基类。
 *
 * binding 仅在 onCreateView 与 onDestroyView 之间有效；onDestroyView 自动置空避免内存泄漏。
 * 观察数据请用 viewLifecycleOwner，勿用 Fragment 自身生命周期。
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null

    protected val binding: VB
        get() = _binding ?: error("binding 只能在 onCreateView 与 onDestroyView 之间访问")

    protected abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val vb = inflateBinding(inflater, container)
        _binding = vb
        return vb.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
