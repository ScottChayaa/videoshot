# 用 Media3 預覽或輸出剪輯，有哪些坑？

[← 回目錄](README.md)

剪輯的預覽（`CompositionPlayer`）與之後的輸出（Transformer）都走 Media3 1.10.1。這些是階段 19 在實機踩過的：

- **相簿素材要依檔案類型排格式辨識器**：`content://media/...` 沒有副檔名，Media3 照固定順序逐一試，MP3 排在圖片前面。JPEG 壓縮資料裡剛好有「ID3」三個字時，MP3 辨識器照它宣稱的大小要幾百 MB 記憶體 → `OutOfMemoryError`（`Id3Peeker`）→ 播放器停在 IDLE，按播放沒反應。
  - 解法：`MimeFirstExtractorsFactory` 用 `ContentResolver.getType()` 當 `Content-Type` 標頭交給 `DefaultExtractorsFactory`，對應的辨識器就排第一。`MediaItem.setMimeType()` 沒用（辨識器只看網址與回應標頭）。
  - 包一層之後 `DefaultMediaSourceFactory` 不會再幫忙打開 JPEG 的 `FLAG_READ_IMAGE`（它只認 `DefaultExtractorsFactory` 本身），要自己開，不然照片整個認不出來。
- **內建的 `GaussianBlurWithFrameOverlaid` 不能直接當「放大蓋滿再模糊」用**：它的背景是把輸入**拉伸**到輸出框再模糊（直紋）。兩邊給同一個倍率可以變成等比放大，但畫布要放大到 3 倍、大半徑卷積，手機 GPU 吃不消，播放時連主執行緒都被卡 1～2 秒。改用自己的 `BlurBackgroundEffect`（縮到 1/8 再模糊）。
- **自己寫 GL 效果（`BaseGlShaderProgram`）時，先讀輸出框再建自己的貼圖**：`drawFrame` 被呼叫時輸出框已經綁好，`GlUtil.createFboForTexture` 會改掉目前綁定的框。先建再讀，第一格就畫到別處、輸出全黑——暫停停在第一格時才看得出來。
- **手機直拍的影片在 MediaStore 是橫的尺寸＋旋轉 90／270**：影片表也要讀 `ORIENTATION`（API 29 起才有），不然直拍影片會被當成橫的。
- **判斷「是不是卡」先分清楚是播放器停了還是畫面沒更新**：播放中每一幀記下 `uptimeMillis` 與 `currentPosition`，兩者的差距一致就是播放器沒停、只是畫面沒畫；記錄的間隔突然拉長到幾百毫秒就是主執行緒被卡住。要比對時暫時拿掉可疑的效果再量一次。
- **出錯時要聽 `onPlayerError`**：播放器出錯後停在 IDLE，`play()` 不會動，要先 `prepare()`。`CompositionPlayer` 不回報 `onSurfaceSizeChanged`，不能靠它判斷畫面準備好了沒有。
- **預覽用 `SurfaceView`**：時間軸清空後播放器停了，`SurfaceView` 還會留著最後一格，要把它拿掉改畫黑底。

相關：[怎麼量照片頁捲動的每一幀？](怎麼量捲動的每一幀.md)
