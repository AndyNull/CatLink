package com.catlink.contracts

import kotlinx.serialization.Serializable
import java.util.Base64

@Serializable data class RegisterRequest(val email: String, val password: String, val deviceName: String)
@Serializable data class LoginCaptchaRequest(val captchaId: String? = null)
@Serializable data class LoginRequest(val email: String, val password: String, val captchaId: String, val captcha: String, val deviceName: String)
@Serializable data class EmailVerificationRequest(val email: String, val code: String)
@Serializable data class ResendVerificationRequest(val email: String)
@Serializable data class PasswordResetRequest(val email: String)
@Serializable data class PasswordResetConfirmRequest(val email: String, val code: String, val newPassword: String)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class LogoutRequest(val refreshToken: String? = null)

@Serializable
data class NeutralAcceptedResponse(
    val accepted: Boolean = true,
    val message: String = "如果信息有效，我们会发送后续邮件。",
)
@Serializable
data class LoginCaptchaResponse(
    val captchaId: String,
    val imageBase64: String,
    val length: Int,
    val expiresInSeconds: Int,
) {
    fun imageBytes(): ByteArray = Base64.getDecoder().decode(imageBase64.substringAfter(','))
}

@Serializable
data class AuthTokensResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
    val account: AccountSummary,
    val deviceId: String,
)

@Serializable
data class AccountSummary(
    val id: String,
    val email: String,
    val emailVerified: Boolean,
    val displayName: String = "",
    val nickname: String = "",
    val avatarUrl: String = "",
)
@Serializable
data class DeviceSessionSummary(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    val current: Boolean,
)

@Serializable data class DeviceSessionsResponse(val devices: List<DeviceSessionSummary>)

/** 用户资料更新请求。 */
@Serializable
data class UpdateProfileRequest(
    val nickname: String = "",
    val avatarUrl: String = "",
)
