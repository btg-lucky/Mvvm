package com.btg.news.ui.list

sealed interface NewsEvent {
    data class OpenLink(val url: String) : NewsEvent
}
