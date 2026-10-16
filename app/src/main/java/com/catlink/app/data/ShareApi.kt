package com.catlink.app.data

import com.catlink.app.network.AuthApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

@Serializable
data class ShareCatInput(
    val catId: String,
    val nickname: String,
    val story: String = "",
    val gender: String = "",
    val ageBand: String = "",
    val coat: String = "",
    val personalityTags: String = "",
    val healthSummary: String = "",
    val statusCode: String = "adoptable",
    val archiveReason: String = "",
    val coverUrl: String = "",
    val photoUrls: List<String> = emptyList(),
)

@Serializable
data class SharedCatView(
    val code: String,
    val nickname: String,
    val story: String = "",
    val gender: String = "",
    val ageBand: String = "",
    val coat: String = "",
    val personalityTags: String = "",
    val healthSummary: String = "",
    val statusCode: String = "adoptable",
    val archiveReason: String = "",
    val coverUrl: String = "",
    val photoUrls: List<String> = emptyList(),
    val catId: String = "",
)

/** 猫咪档案快捷分享：生成分享码、上传照片、按码公开读取预览（无需登录）。 */
class ShareApi(
    private val api: AuthApiClient,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true },
) {
    suspend fun createShareLink(input: ShareCatInput): Result<String> =
        api.post("/share/cats", json.encodeToString(input), authenticated = true) { body ->
            kotlinx.serialization.json.Json.parseToJsonElement(body)
                .jsonObject["code"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("missing code")
        }

    suspend fun fetchSharedCat(code: String): Result<SharedCatView> =
        api.get("/share/cats/$code", authenticated = false) {
            json.decodeFromString<SharedCatView>(it)
        }

    suspend fun uploadMedia(file: File): Result<String> =
        api.postFile(
            path = "/media/upload",
            fields = mapOf("category" to "cat", "temporary" to "false"),
            file = file,
            filename = file.name,
            mediaType = "image/jpeg",
        ) { body ->
            kotlinx.serialization.json.Json.parseToJsonElement(body)
                .jsonObject["url"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("missing url")
        }

    suspend fun uploadMedia(base64: String, extension: String = "jpg"): Result<String> =
        api.post(
            "/media/upload",
            json.encodeToString(mapOf("category" to "cat", "filename" to "share.$extension", "dataBase64" to base64)),
            authenticated = true,
        ) { body ->
            kotlinx.serialization.json.Json.parseToJsonElement(body)
                .jsonObject["url"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("missing url")
        }
}
