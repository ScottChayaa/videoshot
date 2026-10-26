package com.xenyaa.videoshot.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriveAvatarTest {

    private lateinit var server: MockWebServer
    private lateinit var avatar: DriveAvatar

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        avatar = DriveAvatar(
            http = OkHttpClient(),
            accessToken = { "fake-token" },
            io = Dispatchers.IO,
            baseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @After fun tearDown() { server.close() }

    @Test
    fun 讀到photoLink就下載放大過的頭像() = runTest {
        val photoLink = server.url("/a/abc=s64").toString()
        server.enqueue(MockResponse.Builder().body("""{"user":{"photoLink":"$photoLink"}}""").build())
        server.enqueue(MockResponse.Builder().body("PNG").build())

        val bytes = avatar.fetch()

        val about = server.takeRequest()
        assertEquals("/drive/v3/about?fields=user(photoLink)", about.target)
        assertEquals("Bearer fake-token", about.headers["Authorization"])
        assertEquals("/a/abc=s192", server.takeRequest().target)
        assertArrayEquals("PNG".toByteArray(), bytes)
    }

    @Test
    fun 帳號沒有頭像時回null() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"user":{}}""").build())

        assertNull(avatar.fetch())
        assertEquals(1, server.requestCount)
    }

    @Test(expected = IllegalStateException::class)
    fun 授權失敗時丟例外() = runTest {
        server.enqueue(MockResponse.Builder().code(401).build())

        avatar.fetch()
    }
}
