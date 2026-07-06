package com.btg.mine.ui

import androidx.lifecycle.viewModelScope
import com.btg.common.base.BaseViewModel
import com.btg.mine.data.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MineViewModel @Inject constructor(
    private val repository: UserRepository,
) : BaseViewModel() {

    /** 当前登录用户名，未登录为 null。 */
    val currentUser: StateFlow<String?> = repository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }
}
