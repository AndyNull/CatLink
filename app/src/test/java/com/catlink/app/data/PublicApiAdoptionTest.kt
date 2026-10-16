package com.catlink.app.data

import com.catlink.app.network.AuthApiClient
import com.catlink.app.network.AuthSessionSnapshot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PublicApiAdoptionTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PublicApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = PublicApi(
            AuthApiClient(
                baseUrlProvider = { server.url("/").toString() },
                authSessionProvider = { AuthSessionSnapshot("access-token", Long.MAX_VALUE) },
            ),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `applications decode stable ids roles names and millis`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"items":[{"id":"12","catId":"8","catNickname":"团团","applicantId":"3","applicantName":"小李","ownerId":"4","ownerName":"救助人","role":"owner","message":"有稳定住所","contactSummary":"微信 cat-home","status":"contact","createdAt":1787356800000,"updatedAt":1787356900000,"decidedAt":null,"timeline":[{"id":"22","applyId":"12","actorId":"4","actorName":"救助人","status":"contact","note":"开始沟通","mediaUrls":"/uploads/proof.png","createdAt":1787356900000}]}]},"msg":"ok"}"""))

        val row = api.adoptionApplications().getOrThrow().single()

        assertEquals("12", row.id)
        assertEquals("团团", row.catNickname)
        assertEquals("owner", row.role)
        assertEquals("微信 cat-home", row.contactSummary)
        assertEquals(1787356900000, row.updatedAt)
        assertEquals("开始沟通", row.timeline.single().note)
        assertEquals(server.url("/uploads/proof.png").toString(), row.timeline.single().mediaUrls)
        val request = server.takeRequest()
        assertEquals("/api/v1/adoption-applications", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
    }

    @Test
    fun `timeline decodes actor and transition sends note`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"items":[{"id":"22","applyId":"12","actorId":"4","actorName":"救助人","status":"home_visit","note":"家访完成","createdAt":1787357000000}],"status":"home_visit"},"msg":"ok"}"""))
        val event = api.adoptionTimeline("12").getOrThrow().single()
        assertEquals("救助人", event.actorName)
        server.takeRequest()

        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"status":"approved"},"msg":"ok"}"""))
        api.updateAdoptionTimeline("12", "approved", "审核通过").getOrThrow()
        val request = server.takeRequest()
        assertEquals("/api/v1/adoption-applications/12/timeline", request.path)
        assertTrue(request.body.readUtf8().contains("\"note\":\"审核通过\""))
    }

    @Test
    fun `account export uses authenticated server data endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"format":"youmaoni-server-account-data","data":{"posts":[]}},"msg":"ok"}"""))

        val export = api.exportAccountData().getOrThrow().jsonObject

        assertEquals("youmaoni-server-account-data", export.getValue("format").jsonPrimitive.content)
        val request = server.takeRequest()
        assertEquals("/api/v1/account/export", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
    }
}
