package com.xenyaa.videoshot.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSortTest {

    private fun item(
        id: Long,
        takenAt: Long,
        kind: MediaKind = MediaKind.PHOTO,
    ) = MediaItem(
        id = id,
        uri = "content://media/external/file/$id",
        kind = kind,
        takenAtMs = takenAt,
        durationMs = if (kind == MediaKind.VIDEO) 5_000L else null,
        width = 1080,
        height = 1920,
        rotation = 0,
    )

    @Test fun 沒有拍攝時間退回修改時間() {
        assertEquals(1_700_000_000_000L, takenAtOf(null, 1_700_000_000L))
        assertEquals(1_700_000_000_000L, takenAtOf(0L, 1_700_000_000L))
        assertEquals(1_700_000_000_000L, takenAtOf(-5L, 1_700_000_000L))
        assertEquals(1_600_000_000_123L, takenAtOf(1_600_000_000_123L, 1_700_000_000L))
    }

    @Test fun 新的在前同時間依id由大到小() {
        val a = item(id = 1, takenAt = 100)
        val b = item(id = 2, takenAt = 300)
        val c = item(id = 3, takenAt = 100)
        assertEquals(listOf(2L, 3L, 1L), sortNewestFirst(listOf(a, b, c)).map { it.id })
    }

    @Test fun 兩萬筆照片與影片混排仍依時間() {
        val items = (1L..20_000L).map {
            item(
                id = it,
                takenAt = (it * 7919) % 100_000,
                kind = if (it % 3 == 0L) MediaKind.VIDEO else MediaKind.PHOTO,
            )
        }
        val sorted = sortNewestFirst(items)
        assertEquals(20_000, sorted.size)
        assertTrue(
            sorted.zipWithNext().all { (x, y) ->
                x.takenAtMs > y.takenAtMs || (x.takenAtMs == y.takenAtMs && x.id > y.id)
            },
        )
    }
}
