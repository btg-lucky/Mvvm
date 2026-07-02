package com.btg.common.network.gson

import org.junit.Assert.assertEquals
import org.junit.Test

class GsonFactoryTest {

    private data class Foo(val n: Int, val d: Double, val l: Long, val s: String)

    private val gson = GsonFactory.create()

    @Test
    fun `empty and null-string numbers default to zero`() {
        val foo = gson.fromJson("""{"n":"","d":"null","l":"","s":"x"}""", Foo::class.java)
        assertEquals(0, foo.n)
        assertEquals(0.0, foo.d, 0.0)
        assertEquals(0L, foo.l)
    }

    @Test
    fun `normal numbers parse correctly`() {
        val foo = gson.fromJson("""{"n":5,"d":1.5,"l":9,"s":"x"}""", Foo::class.java)
        assertEquals(5, foo.n)
        assertEquals(1.5, foo.d, 0.0)
        assertEquals(9L, foo.l)
    }

    @Test
    fun `string null literal becomes empty`() {
        val foo = gson.fromJson("""{"n":0,"d":0,"l":0,"s":null}""", Foo::class.java)
        assertEquals("", foo.s)
    }

    @Test
    fun `string "null" text becomes empty`() {
        val foo = gson.fromJson("""{"n":0,"d":0,"l":0,"s":"null"}""", Foo::class.java)
        assertEquals("", foo.s)
    }
}
