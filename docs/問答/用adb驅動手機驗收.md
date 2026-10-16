# 怎麼用 adb 驅動手機做驗收？

[← 回目錄](README.md)

## 先確認手機狀態

- **每次 `input tap`／`input text` 前都確認焦點在 app 上**，不符就停下（手機是 scott 的個人手機，點錯會點到別的 app）：

  ```bash
  adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"
  ```

  - `mCurrentFocus` 要是 `com.xenyaa.videoshot`
  - ⋯ 選單、抽屜、對話框開著時 `mCurrentFocus` 是「彈出式視窗」，這時看 `mFocusedApp` 是不是 videoshot
- **手機 10 分鐘無操作就上鎖**（有密碼鎖），上鎖時 Activity 暫停、看起來跟畫面卡住一模一樣
  - 每輪實驗都確認 `dumpsys window` 的 `isKeyguardShowing=false` 與 `dumpsys power` 的 `mWakefulness=Awake`，不符的樣本作廢
  - 超過幾分鐘的流程，另開背景迴圈每 3 分鐘送一次喚醒：

    ```bash
    while true; do adb shell input keyevent KEYCODE_WAKEUP; sleep 180; done
    ```

  - `KEYCODE_WAKEUP` 只會亮到鎖定畫面，已經上鎖就要請 scott 解鎖
  - 鬧鐘或通知橫幅蓋住畫面時，截圖要重拍

## 操作時的規矩

- **返回鍵一次只按一下**：連按兩次會退出 app，之後的座標點擊會落到桌面或別的 app
- **取圖草稿詢問只按【繼續】**，不能按【重新開始】（會清掉 scott 的取圖草稿）
- **scott 的圖庫只看不改**；非改不可時先備份、驗完還原（例：`library.db` 拉到開發機）
- 從詳情頁返回時 Lightbox 已關掉、回到照片頁，原本的座標會點到底部導覽

## 常用做法

- 讀畫面上的元素與座標：

  ```bash
  adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
  ```

- 截圖：`screencap -p`（PNG）約 0.9 秒；要拍短暫的畫面（例如 0.6 秒的閃現）用不壓縮的 raw，約 0.2 秒，再在開發機轉檔：

  ```bash
  adb shell screencap /sdcard/x.raw && adb pull /sdcard/x.raw
  ```

  raw 檔前 12 bytes 是寬、高、格式（little-endian），之後是 RGBA 像素
- 送實體鍵盤的 Tab 要加 `keyboard` 來源，不然系統不會離開觸控模式，畫面上什麼都不會發生：

  ```bash
  adb shell input keyboard keyevent 61
  ```

- 模擬平板寬度（853dp → 5 欄），驗完一定要還原：

  ```bash
  adb shell wm size 1280x800 && adb shell wm density 240
  adb shell wm size reset && adb shell wm density reset
  ```

- 判斷影片有沒有在出聲：`adb shell dumpsys audio` 看 app 的 media player 是不是 started；要讀 `<video>` 的實際狀態見[這題](讀WebView裡影片的狀態.md)

## 做不到的

- adb 打不出中文（`input text` 只能打 ASCII）
- 這台 MIUI 上 adb 模擬不了旋轉，手機實際轉向只能請 scott 拿起來轉

相關：[怎麼在手機上看 app 的檔案？](手機上看app檔案.md)
