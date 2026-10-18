# 從 bitmap 裁一塊（或縮放）之後回收，要注意什麼？

[← 回目錄](README.md)

- `Bitmap.createBitmap(src, x, y, w, h)` 在**範圍等於整張圖、而且 `src` 不可變**時，回的是 `src` 本身，不會新造一張
  - `createScaledBitmap` 在尺寸剛好相同時也一樣
  - `BitmapFactory.decodeFile`／`decodeByteArray` 解出來的都是不可變的
- 所以「裁完回收來源」或「用完回收裁出來的」之前，一定要先比對是不是同一個物件：

  ```kotlin
  val frame = Bitmap.createBitmap(sheet, pos.x, pos.y, pos.width, pos.height)
  if (frame !== sheet) sheet.recycle()
  ```

- 會遇到的真實情況：影片最後一張 storyboard sheet 只剩一格時，YouTube 給的圖就只有一格大
- 沒防的下場：畫面拿到已回收的圖，Compose 一畫就當機（`Canvas: trying to use a recycled bitmap`）
- 寫測試時，測試用的來源圖要是不可變的（`bitmap.copy(config, false)`），用 `Bitmap.createBitmap(w, h, config)` 直接畫的是可變的，測不出這個坑

相關：[儀器測試（androidTest）怎麼跑才算數？](儀器測試怎麼跑才算數.md)
