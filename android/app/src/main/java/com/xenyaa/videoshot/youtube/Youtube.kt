package com.xenyaa.videoshot.youtube

import com.xenyaa.videoshot.core.youtube.WatchPage

/**
 * **所有對 YouTube 非官方端點的存取都只走這裡**（規格第三節模組邊界第 3 條）。
 * YouTube 改版時只有這個模組與 :core 的 WatchPageParser 需要改。
 */
interface Youtube {
    /** 抓 watch page 並解析。網路或 HTTP 失敗時回 FETCH_FAILED，**不丟例外**（功能降級，絕不當機）。 */
    suspend fun watchPage(videoId: String): WatchPage

    /** 下載一張 storyboard sheet。403 時丟 [SheetForbidden]，呼叫端才知道該重抓 spec。 */
    suspend fun sheet(url: String): ByteArray
}

/**
 * sprite 的 sigh 簽章失效了（規格第二節第 2 點：效期不可知）。
 * 呼叫端的對策是重抓一次 watch page 拿新 spec，仍失敗才退回封面圖（規格第七節）。
 */
class SheetForbidden(url: String) : Exception("sprite 回 403，簽章可能已失效：$url")
