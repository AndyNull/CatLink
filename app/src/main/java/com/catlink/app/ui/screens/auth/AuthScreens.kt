package com.catlink.app.ui.screens.auth

import android.os.Build
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.catlink.app.CatLinkApplication
import com.catlink.app.network.OnlineErrorException
import com.catlink.contracts.LoginCaptchaResponse
import com.catlink.app.ui.components.CatButton
import com.catlink.app.ui.components.CatNoticeCenter
import com.catlink.app.ui.components.CatSecretField
import com.catlink.app.ui.components.CatTextField
import com.catlink.app.ui.navigation.SecondaryPage
import com.catlink.app.ui.theme.CatSpacing
import com.catlink.app.ui.theme.CatSizes
import com.catlink.app.ui.theme.CatRadii
import kotlinx.coroutines.launch

private fun deviceName(): String = "${Build.MODEL} - ${Build.VERSION.RELEASE}"

private fun validEmail(value: String): Boolean =
    value.trim().matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))

private fun validPassword(value: String): Boolean =
    value.length in 12..128 && value.any(Char::isLetter) && value.any(Char::isDigit)

@Composable
private fun AuthHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.xs)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onGoRegister: () -> Unit,
    onGoForgot: () -> Unit,
    onGoVerify: () -> Unit,
    onLoggedIn: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captchaInput by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf<LoginCaptchaResponse?>(null) }
    var busy by remember { mutableStateOf(false) }
    var captchaLoading by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var captchaError by remember { mutableStateOf<String?>(null) }
    val captchaBitmap = remember(challenge?.imageBase64) {
        challenge?.imageBytes()?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    suspend fun refreshCaptcha(previousId: String? = challenge?.captchaId) {
        if (captchaLoading) return
        captchaLoading = true
        captchaInput = ""
        captchaError = null
        challenge = null
        app.container.authSession.captcha(previousId)
            .onSuccess { challenge = it }
            .onFailure { captchaError = it.message ?: "验证码加载失败" }
        captchaLoading = false
    }

    LaunchedEffect(Unit) {
        refreshCaptcha(null)
    }

    SecondaryPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = CatSpacing.pageHorizontal, end = CatSpacing.pageHorizontal, top = CatSpacing.pageTop, bottom = CatSpacing.pageBottom),
            verticalArrangement = Arrangement.spacedBy(CatSpacing.lg),
        ) {
            AuthHeader("登录", "登录后可开启云端同步与社区功能。")
            CatTextField(
                value = email,
                onValueChange = { email = it; emailError = null },
                label = "邮箱",
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
                errorText = emailError,
                modifier = Modifier.fillMaxWidth(),
            )
            CatSecretField(
                value = password,
                onValueChange = { password = it; passwordError = null },
                label = "密码",
                errorText = passwordError,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(CatSpacing.xxs)) {
                Text(
                    text = "图形验证码",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CatSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CatTextField(
                        value = captchaInput,
                        onValueChange = {
                            captchaInput = it.filter(Char::isDigit).take(challenge?.length ?: 4)
                            captchaError = null
                        },
                        placeholder = "输入图中数字",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        modifier = Modifier.width(164.dp).height(CatSizes.fieldHeight),
                        shape = RoundedCornerShape(CatRadii.field),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.weight(1f).height(CatSizes.fieldHeight),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    captchaLoading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                    captchaBitmap != null -> Image(
                                        bitmap = captchaBitmap,
                                        contentDescription = "登录图形验证码",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(4.dp),
                                    )
                                    else -> Text("重试", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            IconButton(
                                enabled = !busy && !captchaLoading,
                                onClick = { scope.launch { refreshCaptcha() } },
                            ) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "刷新验证码")
                            }
                        }
                    }
                }
                if (captchaError != null) {
                    Text(
                        text = captchaError.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
            CatButton(
                text = if (busy) "登录中…" else "登录",
                enabled = !busy && !captchaLoading && challenge != null && captchaBitmap != null,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val trimmed = email.trim()
                    emailError = if (!validEmail(trimmed)) "请输入有效的邮箱地址" else null
                    passwordError = if (password.isEmpty()) "请输入密码" else null
                    captchaError = if (captchaInput.length != challenge?.length) "请输入 ${challenge?.length ?: 4} 位图形验证码" else null
                    if (emailError != null || passwordError != null || captchaError != null) return@CatButton
                    val activeChallenge = challenge ?: return@CatButton
                    busy = true
                    scope.launch {
                        val result = app.container.authSession.login(
                            trimmed,
                            password,
                            activeChallenge.captchaId,
                            captchaInput,
                            deviceName(),
                        )
                        busy = false
                        result.onSuccess {
                            CatNoticeCenter.show("登录成功")
                            app.container.authSession.notifySignedIn(context)
                            onLoggedIn()
                        }.onFailure { error ->
                            val online = (error as? OnlineErrorException)?.error
                            if (online is com.catlink.app.network.OnlineError.Permission &&
                                online.userMessage.contains("邮箱验证")
                            ) {
                                CatNoticeCenter.show("请先完成邮箱验证", error = true)
                                onGoVerify()
                            } else {
                                CatNoticeCenter.show(error.message ?: "登录失败", error = true)
                            }
                            refreshCaptcha(activeChallenge.captchaId)
                        }
                    }
                },
            )
            TextButton(onClick = onGoForgot, modifier = Modifier.fillMaxWidth()) {
                Text("忘记密码？", color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onGoRegister, modifier = Modifier.fillMaxWidth()) {
                Text("还没有账号？注册", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(CatSpacing.md))
        }
    }
}
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onGoLogin: () -> Unit,
    onRegistered: (email: String) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    SecondaryPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = CatSpacing.pageHorizontal, end = CatSpacing.pageHorizontal, top = CatSpacing.pageTop, bottom = CatSpacing.pageBottom),
            verticalArrangement = Arrangement.spacedBy(CatSpacing.lg),
        ) {
            AuthHeader("注册账号", "密码需 12-128 位，且同时包含字母和数字。")
            CatTextField(
                value = email,
                onValueChange = { email = it; emailError = null },
                label = "邮箱",
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
                errorText = emailError,
                modifier = Modifier.fillMaxWidth(),
            )
            CatSecretField(
                value = password,
                onValueChange = { password = it; passwordError = null },
                label = "密码",
                errorText = passwordError,
                modifier = Modifier.fillMaxWidth(),
            )
            CatButton(
                text = if (busy) "注册中…" else "注册",
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val trimmed = email.trim()
                    emailError = if (!validEmail(trimmed)) "请输入有效的邮箱地址" else null
                    passwordError = when {
                        !validPassword(password) -> "密码需 12-128 位且同时包含字母和数字"
                        else -> null
                    }
                    if (emailError != null || passwordError != null) return@CatButton
                    busy = true
                    scope.launch {
                        val result = app.container.authSession.register(trimmed, password, deviceName())
                        busy = false
                        result.onSuccess {
                            CatNoticeCenter.show("注册成功，验证码已发送")
                            onRegistered(trimmed)
                        }.onFailure { error ->
                            CatNoticeCenter.show(error.message ?: "注册失败", error = true)
                        }
                    }
                },
            )
            TextButton(onClick = onGoLogin, modifier = Modifier.fillMaxWidth()) {
                Text("已有账号？去登录", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(CatSpacing.md))
        }
    }
}
@Composable
fun VerifyEmailScreen(
    email: String,
    onBack: () -> Unit,
    onVerified: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tokenError by remember { mutableStateOf<String?>(null) }

    SecondaryPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = CatSpacing.pageHorizontal, end = CatSpacing.pageHorizontal, top = CatSpacing.pageTop, bottom = CatSpacing.pageBottom),
            verticalArrangement = Arrangement.spacedBy(CatSpacing.lg),
        ) {
            AuthHeader("验证邮箱", "验证码已发送至 $email，10 分钟内有效。")
            CatTextField(
                value = token,
                onValueChange = { token = it; tokenError = null },
                label = "验证码",
                errorText = tokenError,
                modifier = Modifier.fillMaxWidth(),
            )
            CatButton(
                text = if (busy) "验证中…" else "完成验证",
                enabled = !busy && token.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (token.isBlank()) {
                        tokenError = "请输入验证码"
                        return@CatButton
                    }
                    busy = true
                    scope.launch {
                        val result = app.container.authSession.verifyEmail(email, token.trim(), deviceName())
                        busy = false
                        result.onSuccess {
                            CatNoticeCenter.show("邮箱验证成功，已登录")
                            app.container.authSession.notifySignedIn(context)
                            onVerified()
                        }.onFailure { error ->
                            CatNoticeCenter.show(error.message ?: "验证失败", error = true)
                        }
                    }
                },
            )
            TextButton(
                onClick = {
                    scope.launch {
                        app.container.authSession.resendVerification(email).onSuccess {
                            CatNoticeCenter.show("已重新发送验证邮件")
                        }.onFailure { error ->
                            CatNoticeCenter.show(error.message ?: "发送失败", error = true)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("重新发送验证邮件", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(CatSpacing.md))
        }
    }
}

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, onCodeSent: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }

    SecondaryPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = CatSpacing.pageHorizontal, end = CatSpacing.pageHorizontal, top = CatSpacing.pageTop, bottom = CatSpacing.pageBottom),
            verticalArrangement = Arrangement.spacedBy(CatSpacing.lg),
        ) {
            AuthHeader("找回密码", "输入注册邮箱，我们会发送密码重置邮件。")
            CatTextField(
                value = email,
                onValueChange = { email = it; emailError = null },
                label = "邮箱",
                placeholder = "you@example.com",
                keyboardType = KeyboardType.Email,
                errorText = emailError,
                modifier = Modifier.fillMaxWidth(),
            )
            CatButton(
                text = if (busy) "发送中…" else "发送重置邮件",
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val trimmed = email.trim()
                    emailError = if (!validEmail(trimmed)) "请输入有效的邮箱地址" else null
                    if (emailError != null) return@CatButton
                    busy = true
                    scope.launch {
                        val result = app.container.authSession.forgotPassword(trimmed)
                        busy = false
                        result.onSuccess {
                            CatNoticeCenter.show("如果该邮箱已注册，验证码会发送到邮箱")
                            onCodeSent(trimmed)
                        }.onFailure { error ->
                            CatNoticeCenter.show(error.message ?: "发送失败", error = true)
                        }
                    }
                },
            )
            Spacer(Modifier.height(CatSpacing.md))
        }
    }
}

@Composable
fun ResetPasswordScreen(email: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CatLinkApplication
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    SecondaryPage {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = CatSpacing.pageHorizontal, end = CatSpacing.pageHorizontal, top = CatSpacing.pageTop, bottom = CatSpacing.pageBottom),
            verticalArrangement = Arrangement.spacedBy(CatSpacing.lg),
        ) {
            AuthHeader("重置密码", "输入发送至 $email 的验证码和新密码。")
            CatTextField(
                value = code,
                onValueChange = { code = it },
                label = "验证码",
                modifier = Modifier.fillMaxWidth(),
            )
            CatSecretField(
                value = password,
                onValueChange = { password = it; passwordError = null },
                label = "新密码",
                errorText = passwordError,
                modifier = Modifier.fillMaxWidth(),
            )
            CatButton(
                text = if (busy) "提交中…" else "重置密码",
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    passwordError = when {
                        !validPassword(password) -> "密码需 12-128 位且同时包含字母和数字"
                        else -> null
                    }
                    if (passwordError != null) return@CatButton
                    busy = true
                    scope.launch {
                        val result = app.container.authSession.resetPassword(email, code.trim(), password)
                        busy = false
                        result.onSuccess {
                            CatNoticeCenter.show("密码已重置，请使用新密码登录")
                            onBack()
                        }.onFailure { error ->
                            CatNoticeCenter.show(error.message ?: "重置失败", error = true)
                        }
                    }
                },
            )
            Spacer(Modifier.height(CatSpacing.md))
        }
    }
}
