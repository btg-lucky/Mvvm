package com.btg.mine.ui

import androidx.lifecycle.viewModelScope
import com.btg.common.base.BaseViewModel
import com.btg.common.result.ApiResult
import com.btg.mine.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val repository: UserRepository,
) : BaseViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _registerSuccess = Channel<Unit>(Channel.BUFFERED)
    val registerSuccess: Flow<Unit> = _registerSuccess.receiveAsFlow()

    fun register(username: String, password: String, confirm: String) {
        val name = username.trim()
        when {
            name.isBlank() -> { postError("用户名不能为空"); return }
            password.length < 6 -> { postError("密码至少 6 位"); return }
            password != confirm -> { postError("两次输入的密码不一致"); return }
        }
        _isSubmitting.value = true
        viewModelScope.launch {
            when (val result = repository.register(name, password)) {
                is ApiResult.Success -> _registerSuccess.send(Unit)
                is ApiResult.Error -> postError(result.throwable.message ?: "注册失败")
            }
            _isSubmitting.value = false
        }
    }
}
