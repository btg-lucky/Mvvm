package com.btg.common.permission

import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 权限请求封装。必须在 Activity/Fragment 创建期（作为字段）实例化，
 * 以满足 registerForActivityResult 的时机要求。
 *
 * 用法：
 *   private val permission = PermissionRequester(this)
 *   permission.request(Manifest.permission.CAMERA) { result ->
 *       if (result.allGranted) { ... } else { openAppSettings() }
 *   }
 */
class PermissionRequester(caller: ActivityResultCaller) {

    private var callback: ((PermissionResult) -> Unit)? = null

    private val launcher = caller.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        callback?.invoke(PermissionResult(grants))
    }

    fun request(vararg permissions: String, onResult: (PermissionResult) -> Unit) {
        callback = onResult
        launcher.launch(arrayOf(*permissions))
    }
}
