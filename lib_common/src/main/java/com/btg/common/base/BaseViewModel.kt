package com.btg.common.base

import androidx.lifecycle.ViewModel

/** MVVM 分层锚点。暂不抽象通用逻辑（YAGNI），出现真实重复再上提。 */
open class BaseViewModel : ViewModel()
