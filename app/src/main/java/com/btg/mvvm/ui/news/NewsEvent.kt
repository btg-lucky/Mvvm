package com.btg.mvvm.ui.news

sealed interface NewsEvent {
    data class OpenLink(val url: String) : NewsEvent
}
