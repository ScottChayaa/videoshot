package com.xenyaa.videoshot.backup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/** 帳號頁頭像 52dp，高密度螢幕約 150px；多要一點留給更高密度的手機。 */
private const val AVATAR_PX = 192

/**
 * 已連結 Google 帳號的頭像（帳號頁 hero、導覽列帳號格）。
 *
 * 用 Drive 的 `about?fields=user(photoLink)` 取網址，不是連結當下 Credential Manager 給的
 * `profilePictureUri`——這樣已經連結過的帳號不用重新連結，使用者換了頭像也會跟著更新。
 * `about` 在 `drive.appdata` 授權範圍內可讀，不用多要權限。
 *
 * @return 圖檔的位元組；帳號沒有設頭像回 null。網路或授權失敗丟例外，由呼叫端決定要不要沿用舊圖。
 */
class DriveAvatar(
    private val http: OkHttpClient,
    private val accessToken: suspend () -> String,
    private val io: CoroutineDispatcher,
    private val baseUrl: String = "https://www.googleapis.com",
) {
    suspend fun fetch(): ByteArray? = withContext(io) {
        val aboutRequest = Request.Builder()
            .url("$baseUrl/drive/v3/about?fields=user(photoLink)")
            .header("Authorization", "Bearer ${accessToken()}")
            .build()
        val photoLink = http.newCall(aboutRequest).execute().use { response ->
            check(response.isSuccessful) { "讀取帳號資料失敗: ${response.code}" }
            val user = Json.parseToJsonElement(response.body.string()).jsonObject["user"]?.jsonObject
            user?.get("photoLink")?.jsonPrimitive?.content
        } ?: return@withContext null

        val imageRequest = Request.Builder().url(withSize(photoLink)).build()
        http.newCall(imageRequest).execute().use { response ->
            check(response.isSuccessful) { "下載頭像失敗: ${response.code}" }
            response.body.bytes()
        }
    }
}

/** Google 頭像網址結尾的 `=s64` 是邊長；換成 [AVATAR_PX]，不然放大會糊。沒有這個結尾就原樣用。 */
internal fun withSize(photoLink: String): String = photoLink.replace(Regex("=s\\d+(-c)?$"), "=s$AVATAR_PX")
