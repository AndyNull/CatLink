package com.catlink.app.network

import com.catlink.app.data.MediaFiles
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaUrlResolutionTest {
    @Test
    fun absolute_upload_path_resolves_against_origin() {
        assertEquals(
            "https://api.catlink.app/uploads/cat/photo.png",
            ApiEnvironment.resolveServerUrl("https://api.catlink.app/api/v1", "/uploads/cat/photo.png"),
        )
    }

    @Test
    fun same_origin_absolute_url_is_preserved() {
        assertEquals(
            "https://api.catlink.app/uploads/cat/photo.webp",
            ApiEnvironment.resolveServerUrl("https://api.catlink.app", "https://api.catlink.app/uploads/cat/photo.webp"),
        )
    }

    @Test
    fun same_origin_media_url_converts_to_server_path() {
        assertEquals(
            "/uploads/cat/photo.webp",
            ApiEnvironment.serverRelativePath(
                "https://api.catlink.app/api/v1",
                "https://api.catlink.app/uploads/cat/photo.webp",
            ),
        )
        assertEquals(
            "/uploads/cat/photo.webp",
            ApiEnvironment.serverRelativePath("https://api.catlink.app/api/v1", "/uploads/cat/photo.webp"),
        )
    }

    @Test
    fun cross_origin_and_authority_urls_are_rejected() {
        assertEquals("", ApiEnvironment.resolveServerUrl("https://api.catlink.app", "https://attacker.example/cat.webp"))
        assertEquals("", ApiEnvironment.resolveServerUrl("https://api.catlink.app", "//attacker.example/cat.webp"))
        assertEquals("", ApiEnvironment.resolveServerUrl("https://api.catlink.app", "http://api.catlink.app/cat.webp"))
        assertEquals("", ApiEnvironment.resolveServerUrl("https://api.catlink.app", "https://api.catlink.app:444/cat.webp"))
        assertEquals("", ApiEnvironment.resolveServerUrl("https://api.catlink.app", "file:///data/local/tmp/cat.webp"))
        assertEquals("", ApiEnvironment.serverRelativePath("https://api.catlink.app", "https://attacker.example/cat.webp"))
        assertEquals("", ApiEnvironment.serverRelativePath("https://api.catlink.app", "https://api.catlink.app/cat.webp?token=secret"))
    }

    @Test
    fun media_mime_maps_to_server_supported_extension() {
        assertEquals("png", MediaFiles.extensionForMime("image/png"))
        assertEquals("webp", MediaFiles.extensionForMime("image/webp"))
        assertEquals("pdf", MediaFiles.extensionForMime("application/pdf"))
        assertEquals("jpg", MediaFiles.extensionForMime("image/jpeg"))
        assertEquals("jpg", MediaFiles.extensionForMime(null))
    }
}
