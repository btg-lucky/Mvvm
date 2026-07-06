package com.btg.news.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewsDtoMappingTest {

    @Test
    fun `list item dto maps all fields`() {
        val dto = NewsListItemDto(
            uniquekey = "k1", title = "标题", date = "2026-07-06 10:00",
            category = "头条", authorName = "来源A",
            url = "https://example.com/1", thumbnail = "https://img/1.png"
        )
        val model = dto.toModel()
        assertEquals("k1", model.uniquekey)
        assertEquals("标题", model.title)
        assertEquals("来源A", model.source)
        assertEquals("2026-07-06 10:00", model.date)
        assertEquals("头条", model.category)
        assertEquals("https://img/1.png", model.imageUrl)
        assertEquals("https://example.com/1", model.url)
    }

    @Test
    fun `list item dto null fields fall back to empty`() {
        val dto = NewsListItemDto(null, null, null, null, null, null, null)
        val model = dto.toModel()
        assertEquals("", model.uniquekey)
        assertEquals("", model.title)
        assertNull(model.imageUrl)
        assertEquals("", model.source)
        assertEquals("", model.date)
        assertEquals("", model.category)
        assertEquals("", model.url)
    }

    @Test
    fun `detail result maps detail and content`() {
        val result = NewsDetailResult(
            uniquekey = "k1",
            content = "<p>正文</p>",
            detail = NewsDetailDto(
                title = "标题", date = "2026-07-06 10:00", category = "娱乐",
                authorName = "来源B", url = "https://example.com/1"
            )
        )
        val model = result.toModel()
        assertEquals("标题", model.title)
        assertEquals("来源B", model.source)
        assertEquals("娱乐", model.category)
        assertEquals("<p>正文</p>", model.contentHtml)
        assertEquals("https://example.com/1", model.url)
        assertEquals("2026-07-06 10:00", model.date)
    }
}
