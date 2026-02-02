package org.webdsl.lsp

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import org.webdsl.lsp.utils.parseConfig

class UtilsTest {
    @Test
    fun `parseConfig should parse config with a single entry`() {
        assertEquals(mapOf("appname" to "yellowgrass"), parseConfig("appname=yellowgrass"))
    }

    @Test
    fun `parseConfig should work with empty lines and trailing newlines`() {
        assertEquals(mapOf("appname" to "yellowgrass", "backend" to "servlet"), parseConfig("appname=yellowgrass\n\n  \nbackend=servlet\n"))
    }

    @Test
    fun `parseConfig should properly work with comments`() {
        assertEquals(mapOf("appname" to "yellowgrass # aaaa", "backend" to "servlet"), parseConfig("appname=yellowgrass # aaaa\n# below is the backend type\nbackend=servlet"))
    }

    @Test
    fun `parseConfig should trim the keys and left-trim the values`() {
        assertEquals(mapOf("appname" to "yellowgrass  "), parseConfig("   appname =     yellowgrass  "))
    }
}
