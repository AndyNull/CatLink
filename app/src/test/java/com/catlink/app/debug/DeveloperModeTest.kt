package com.catlink.app.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeveloperModeTest {
    @Test
    fun `Gin health envelope is accepted`() {
        assertEquals("Gin API", parseGinHealth("""{"code":0,"data":"ok","msg":"成功"}"""))
    }

    @Test
    fun `legacy Ktor health envelope is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseGinHealth("""{"status":"ok","service":"catlink-server"}""")
        }
    }
}
