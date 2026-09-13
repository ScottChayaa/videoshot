package com.xenyaa.videoshot.core.storyboard

// 實測擷取自 YouTube watch page（24 秒的 unlisted 影片）；移植自 src/lib/storyboard.test.ts。
// 注意 Kotlin 字串裡的 $ 必須跳脫成 \$。
const val REAL_SPEC: String =
    "https://i.ytimg.com/sb/KUdmrPVssFA/storyboard3_L\$L/\$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgjTpKzTBg==" +
        "|48#27#100#10#10#0#default#rs\$AOn4CLDCQG-jwLOoOGPBLaFWxpqItJgENA" +
        "|80#45#25#10#10#1000#M\$M#rs\$AOn4CLAdQajGjXcFllukj8IozdMskyx6Zw" +
        "|160#90#25#5#5#1000#M\$M#rs\$AOn4CLDDTrcJY1ywfKuJLuu2E4bctSN8og" +
        "|320#180#25#3#3#1000#M\$M#rs\$AOn4CLAF8rkqvc6h6mM0WUjOJy55DJC1vA"
