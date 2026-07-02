package com.btg.common.ui

import android.content.Context
import androidx.appcompat.app.AlertDialog

/** 二次确认弹窗：确定 + 取消。 */
fun Context.showConfirmDialog(
    title: CharSequence? = null,
    message: CharSequence,
    positiveText: CharSequence = "确定",
    negativeText: CharSequence = "取消",
    cancelable: Boolean = true,
    onConfirm: () -> Unit,
    onCancel: (() -> Unit)? = null,
): AlertDialog = AlertDialog.Builder(this)
    .setTitle(title)
    .setMessage(message)
    .setCancelable(cancelable)
    .setPositiveButton(positiveText) { _, _ -> onConfirm() }
    .setNegativeButton(negativeText) { _, _ -> onCancel?.invoke() }
    .show()

/** 单按钮提示弹窗。 */
fun Context.showAlertDialog(
    title: CharSequence? = null,
    message: CharSequence,
    buttonText: CharSequence = "确定",
    onDismiss: (() -> Unit)? = null,
): AlertDialog = AlertDialog.Builder(this)
    .setTitle(title)
    .setMessage(message)
    .setPositiveButton(buttonText) { _, _ -> onDismiss?.invoke() }
    .show()
