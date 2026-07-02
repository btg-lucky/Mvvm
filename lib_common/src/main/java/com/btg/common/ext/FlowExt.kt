package com.btg.common.ext

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 在 [owner] 处于 STARTED 时收集 Flow，离开 STARTED 自动取消、重新进入自动重启。
 * Fragment 中请传 viewLifecycleOwner；Activity 传自身。
 */
fun <T> Flow<T>.collectOnStarted(owner: LifecycleOwner, action: suspend (T) -> Unit) {
    owner.lifecycleScope.launch {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            collect { action(it) }
        }
    }
}
