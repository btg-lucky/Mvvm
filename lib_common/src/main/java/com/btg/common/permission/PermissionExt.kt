package com.btg.common.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** 跳转到本应用的系统设置页（权限被永久拒绝后引导用户手动开启）。 */
fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
