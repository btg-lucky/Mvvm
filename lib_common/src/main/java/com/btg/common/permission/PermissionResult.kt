package com.btg.common.permission

/** 权限请求结果。 */
data class PermissionResult(val grants: Map<String, Boolean>) {
    /** 是否全部授予（空结果视为全部授予）。 */
    val allGranted: Boolean get() = grants.values.all { it }

    /** 被拒绝的权限列表。 */
    val denied: List<String> get() = grants.filterValues { !it }.keys.toList()
}
