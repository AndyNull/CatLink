package com.catlink.app.data

import com.catlink.app.network.AuthApiClient
import com.catlink.app.network.AuthSessionSnapshot
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PublicApiLocationSearchTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `location page sends pagination and keeps complete provider fields`() = runBlocking {
        val longAddress = "北京市东城区东长安街天安门广场人民英雄纪念碑北侧完整地址"
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"items":[{"id":"amap-1","label":"天安门广场","address":"$longAddress","province":"北京市","city":"北京市","district":"东城区","street":"东长安街","latitude":39.9037,"longitude":116.3976,"source":"高德地图"}],"page":2,"pageSize":20,"total":45,"hasMore":true}}"""))

        val page = api().searchLocationPage(
            " 天安门 ", " 北京市 ", " 东城区 ", page = 2, pageSize = 20,
            cityCode = "110100", districtCode = "110101",
        ).getOrThrow()

        assertEquals(2, page.page)
        assertEquals(20, page.pageSize)
        assertEquals(45, page.total)
        assertTrue(page.hasMore)
        assertEquals(longAddress, page.items.single().address)
        assertEquals("北京市 · 东城区 · 东长安街", page.items.single().regionLabel())
        assertEquals("高德地图", page.items.single().sourceLabel())
        assertEquals(
            "/api/v1/location/search?q=%E5%A4%A9%E5%AE%89%E9%97%A8&city=%E5%8C%97%E4%BA%AC%E5%B8%82&district=%E4%B8%9C%E5%9F%8E%E5%8C%BA&cityCode=110100&districtCode=110101&page=2&pageSize=20",
            server.takeRequest().path,
        )
    }

    @Test
    fun `location page infers hasMore from total`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"items":[{"label":"地点"}],"page":2,"pageSize":10,"total":25}}"""))

        val page = api().searchLocationPage("地点", page = 2, pageSize = 10).getOrThrow()

        assertTrue(page.hasMore)
        assertEquals(25, page.total)
    }

    @Test
    fun `unknown provider total keeps explicit hasMore`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"items":[{"label":"地点"}],"page":1,"pageSize":20,"total":-1,"hasMore":true}}"""))

        val page = api().searchLocationPage("地点").getOrThrow()

        assertEquals(-1, page.total)
        assertTrue(page.hasMore)
    }

    @Test
    fun `legacy items response remains a single page`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"items":[{"label":"旧地点","address":"旧地址","attribution":"旧地图"}]}}"""))

        val page = api().searchLocationPage("旧地点").getOrThrow()

        assertEquals(1, page.page)
        assertEquals(20, page.pageSize)
        assertEquals(1, page.total)
        assertFalse(page.hasMore)
        assertEquals("旧地图", page.items.single().sourceLabel())
    }

    @Test
    fun `cross page merge is ordered and stable`() {
        val first = PublicApi.LocationSuggestion(id = "poi-1", label = "天安门", address = "第一页地址")
        val fallback = PublicApi.LocationSuggestion(label = "人民公园", address = "公园路 1 号", latitude = 30.1, longitude = 104.1)
        val duplicate = first.copy(address = "第二页重复地址")
        val next = PublicApi.LocationSuggestion(id = "poi-2", label = "钟楼")

        val merged = mergeLocationSuggestions(listOf(first, fallback), listOf(duplicate, fallback.copy(), next))

        assertEquals(listOf("天安门", "人民公园", "钟楼"), merged.map { it.label })
        assertEquals("第一页地址", merged.first().address)
    }

    private fun api() = PublicApi(
        AuthApiClient(
            baseUrlProvider = { server.url("/").toString() },
            authSessionProvider = { AuthSessionSnapshot(null, 0L) },
        ),
    )
}
