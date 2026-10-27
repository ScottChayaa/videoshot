package com.xenyaa.videoshot.media

/**
 * app 對手機相片與影片的存取程度。
 *
 * - [FULL]：全部可讀。
 * - [PARTIAL]：Android 14 起使用者可以只允許「選取的相片和影片」，只讀得到那一部分。
 * - [DENIED]：讀不到。
 */
enum class MediaAccess { FULL, PARTIAL, DENIED }

// 權限名稱用字串常數而不引用 `Manifest`：這個檔案要能在純 JVM 測試裡跑。
private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
private const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
private const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"

private const val WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE"

private const val SDK_PIE = 28
private const val SDK_TIRAMISU = 33
private const val SDK_UPSIDE_DOWN_CAKE = 34

/**
 * 依系統版本與已授權的權限判定存取程度（Android 官方「部分存取」的寫法）。
 *
 * 照片與影片都有授權才算完整；Android 14 只有 `READ_MEDIA_VISUAL_USER_SELECTED`
 * 是部分存取；只給照片或只給影片視為拒絕（剪輯兩種都要）。
 * Android 12 以下沒有細分，看 `READ_EXTERNAL_STORAGE`。
 */
fun mediaAccessOf(sdkInt: Int, granted: Set<String>): MediaAccess = when {
    sdkInt >= SDK_TIRAMISU ->
        if (READ_MEDIA_IMAGES in granted && READ_MEDIA_VIDEO in granted) MediaAccess.FULL
        else if (sdkInt >= SDK_UPSIDE_DOWN_CAKE && READ_MEDIA_VISUAL_USER_SELECTED in granted) MediaAccess.PARTIAL
        else MediaAccess.DENIED
    READ_EXTERNAL_STORAGE in granted -> MediaAccess.FULL
    else -> MediaAccess.DENIED
}

/**
 * 該系統版本要向使用者請求的權限。
 *
 * Android 14 起要把 `READ_MEDIA_VISUAL_USER_SELECTED` 跟照片、影片權限**一起**請求，
 * 系統才會顯示「選取相片和影片」的選項。
 */
fun mediaPermissionsFor(sdkInt: Int): List<String> = when {
    sdkInt >= SDK_UPSIDE_DOWN_CAKE ->
        listOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)
    sdkInt >= SDK_TIRAMISU -> listOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
    // Android 9 以下：存剪輯成品進相簿要寫入權限，跟讀取一起要（判定存取程度只看讀取，見 [mediaAccessOf]）
    sdkInt <= SDK_PIE -> listOf(READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)
    else -> listOf(READ_EXTERNAL_STORAGE)
}
