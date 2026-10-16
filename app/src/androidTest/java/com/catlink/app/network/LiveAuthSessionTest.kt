package com.catlink.app.network

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.catlink.app.CatLinkApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveAuthSessionTest {
    @Test
    fun login_with_instrumentation_credentials_persists_session() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("live instrumentation test requires -e live true", arguments.getString("live") == "true")
        val email = requireNotNull(arguments.getString("email")) { "instrumentation argument email is required" }
        val password = requireNotNull(arguments.getString("password")) { "instrumentation argument password is required" }
        val captchaId = requireNotNull(arguments.getString("captchaId")) { "instrumentation argument captchaId is required" }
        val captcha = requireNotNull(arguments.getString("captcha")) { "instrumentation argument captcha is required" }
        val app = ApplicationProvider.getApplicationContext<CatLinkApplication>()

        app.container.authSession.login(email, password, captchaId, captcha, "MuMu instrumentation").getOrThrow()

        assertTrue(app.container.authSession.isSignedIn())
        assertTrue(app.container.authSession.currentEmail() == email)
        assertTrue(app.container.authSession.requireAccessToken().getOrThrow().isNotBlank())
    }
}
