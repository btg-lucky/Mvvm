package com.btg.mvvm.ui.demo

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.btg.common.base.BaseFragment
import com.btg.common.permission.PermissionRequester
import com.btg.common.permission.openAppSettings
import com.btg.common.ui.LoadingDialog
import com.btg.common.ui.showConfirmDialog
import com.btg.common.ui.toast
import com.btg.mvvm.R
import com.btg.mvvm.databinding.FragmentComponentsBinding

/** 弹窗 / Toast / Loading / 权限 演示。 */
class ComponentsFragment : BaseFragment<FragmentComponentsBinding>() {

    private val permissionRequester = PermissionRequester(this)

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentComponentsBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnToast.setOnClickListener { requireContext().toast("这是一个 Toast") }

        binding.btnConfirm.setOnClickListener {
            requireContext().showConfirmDialog(
                title = "提示",
                message = "确认执行该操作吗？",
                onConfirm = { requireContext().toast("已确认") },
                onCancel = { requireContext().toast("已取消") },
            )
        }

        binding.btnLoading.setOnClickListener {
            val dialog = LoadingDialog(requireContext())
            dialog.show("加载中…")
            binding.root.postDelayed({ dialog.dismiss() }, 2000)
        }

        binding.btnPermission.setOnClickListener {
            permissionRequester.request(Manifest.permission.CAMERA) { result ->
                if (result.allGranted) {
                    requireContext().toast("相机权限已授予")
                } else {
                    requireContext().showConfirmDialog(
                        message = "相机权限被拒绝，是否去设置页开启？",
                        onConfirm = { requireContext().openAppSettings() },
                    )
                }
            }
        }

        // BottomSheet：结果监听与 show 都走 childFragmentManager，二者需一致
        childFragmentManager.setFragmentResultListener(
            DemoBottomSheet.RESULT_KEY, viewLifecycleOwner,
        ) { _, bundle ->
            val action = bundle.getString(DemoBottomSheet.KEY_ACTION).orEmpty()
            requireContext().toast(getString(R.string.sheet_result_toast, action))
        }
        binding.btnBottomSheet.setOnClickListener {
            DemoBottomSheet().show(childFragmentManager, DemoBottomSheet.TAG)
        }
    }
}
