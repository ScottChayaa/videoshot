package com.xenyaa.videoshot.core.similarity

/** dHash 的取樣尺寸：寬要比高多 1，因為每一列比較的是「相鄰兩點」，9 個點產生 8 個位元。 */
const val DHASH_WIDTH = 9
const val DHASH_HEIGHT = 8

/**
 * 差分雜湊（difference hash）。把畫面縮成 9×8 灰階後，
 * 每一列由左至右比較相鄰兩點：右邊比較亮就記 1，否則記 0 —— 共 8×8 = 64 位元。
 *
 * 為什麼用 dHash 而不是平均雜湊：它比的是**相鄰像素的相對關係**，
 * 對整體亮度變化免疫（同一個場景忽明忽暗仍算同一張），而這正是 storyboard 相鄰格的典型差異。
 *
 * @param gray 長度必須是 [DHASH_WIDTH] × [DHASH_HEIGHT]，列優先，值域 0～255
 */
fun dHash(gray: IntArray): Long {
    require(gray.size == DHASH_WIDTH * DHASH_HEIGHT) {
        "dHash 需要 ${DHASH_WIDTH}×${DHASH_HEIGHT} = ${DHASH_WIDTH * DHASH_HEIGHT} 個灰階值，收到 ${gray.size} 個"
    }
    var hash = 0L
    var bit = 0
    for (y in 0 until DHASH_HEIGHT) {
        val row = y * DHASH_WIDTH
        for (x in 0 until DHASH_WIDTH - 1) {
            if (gray[row + x + 1] > gray[row + x]) hash = hash or (1L shl bit)
            bit++
        }
    }
    return hash
}

/** 兩個指紋有幾個位元不同。0 代表一模一樣，64 代表完全相反。 */
fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
