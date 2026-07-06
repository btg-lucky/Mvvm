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
class LoginViewModel @Inject constructor(
    private val repository: UserRepository,
) : BaseViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _loginSuccess = Channel<Unit>(Channel.BUFFERED)
    val loginSuccess: Flow<Unit> = _loginSuccess.receiveAsFlow()

    fun login(username: String, password: String) {
        val name = username.trim()
        if (name.isBlank() || password.isBlank()) {
            postError("用户名和密码不能为空")
            return
        }
        _isSubmitting.value = true
        viewModelScope.launch {
            try {
                when (val result = repository.login(name, password)) {
                    is ApiResult.Success -> _loginSuccess.send(Unit)
                    is ApiResult.Error -> postError(result.throwable.message ?: "登录失败")
                }
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}
