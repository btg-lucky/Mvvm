package com.btg.common.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * 承载 ViewBinding 的 BottomSheet 基类。binding 在 onCreateView~onDestroyView 间有效，自动释放。
 * 需 app 主题为 Material 主题（Theme.MaterialComponents.* / Theme.Material3.*）。
 */
abstract class BaseBottomSheetFragment<VB : ViewBinding> : BottomSheetDialogFragment() {

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
