package com.btg.mvvm.ui.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.btg.common.ui.BaseBottomSheetFragment
import com.btg.mvvm.R
import com.btg.mvvm.databinding.FragmentDemoBottomSheetBinding

/**
 * [BaseBottomSheetFragment] 用法示例：从底部弹出的操作菜单。
 *
 * 选项结果通过 [setFragmentResult] 回传给宿主（key = [RESULT_KEY]）后关闭，
 * 而非构造函数传 lambda —— 后者在旋屏 / 进程重建时 Fragment 会崩。
 */
class DemoBottomSheet : BaseBottomSheetFragment<FragmentDemoBottomSheetBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentDemoBottomSheetBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.optionShare.setOnClickListener { selectAndClose(R.string.sheet_option_share) }
        binding.optionFavorite.setOnClickListener { selectAndClose(R.string.sheet_option_favorite) }
        binding.optionCancel.setOnClickListener { dismiss() }
    }

    private fun selectAndClose(@StringRes actionRes: Int) {
        setFragmentResult(RESULT_KEY, bundleOf(KEY_ACTION to getString(actionRes)))
        dismiss()
    }

    companion object {
        const val TAG = "DemoBottomSheet"
        const val RESULT_KEY = "demo_bottom_sheet_result"
        const val KEY_ACTION = "action"
    }
}
