# 問答目錄

開發時常查的問題，一題一個檔案，點進去看答案。細節以規格為準（`docs/superpowers/specs/`）。

## 圖片與縮圖

- [首頁的圖存在哪裡？](首頁的圖存在哪裡.md)
- [為什麼 thumbs/ 裡有圖庫沒有的檔案？](thumbs多出來的檔案.md)
- [縮圖牆的格數為什麼比 storyboard spec 的 frameCount 少一格？](縮圖牆格數為什麼比spec少一格.md)

## 首頁篩選

- [首頁篩選中，改了或刪了圖，首頁怎麼更新？](篩選中改了圖首頁怎麼更新.md)

## 實機與工具

- [怎麼在手機上看 app 的檔案？](手機上看app檔案.md)
- [files/ 底下的 harvest-test-*、thumbs-test-*、drafts/vid 是什麼？](測試留下的暫存資料夾.md)

## 實機驗收

- [怎麼用 adb 驅動手機做驗收？](用adb驅動手機驗收.md)
- [怎麼讀 WebView 裡影片的實際狀態？](讀WebView裡影片的狀態.md)
- [為什麼 app 偶爾白畫面不動？](開app白畫面不動.md)
- [怎麼量照片頁捲動的每一幀？](怎麼量捲動的每一幀.md)

## 測試

- [儀器測試（androidTest）怎麼跑才算數？](儀器測試怎麼跑才算數.md)
- [Compose 單元測試（Robolectric）有哪些坑？](Robolectric測試的坑.md)
- [為什麼只編譯過、單元測試綠燈還不夠？](只編譯過為什麼不夠.md)

## 寫程式

- [非同步讀取寫回畫面狀態要注意什麼？](非同步讀取寫回狀態.md)
- [從 bitmap 裁一塊（或縮放）之後回收，要注意什麼？](裁切後回收bitmap要注意什麼.md)
- [用 Media3 預覽或輸出剪輯，有哪些坑？](Media3剪輯踩過的坑.md)
- [剪輯在背景輸出，Android 會擋什麼？](背景輸出的前景工作與限制.md)
- [WorkManager 的工作串取消一支，有什麼坑？](工作串取消的坑.md)
- [WorkManager 的工作要每隔幾十秒查一次，怎麼排？](WorkManager定時查詢怎麼排.md)
- [上傳到 YouTube 中途被殺或斷網，怎麼從斷掉的地方接著傳？](上傳中斷怎麼接著傳.md)

---

新增問題的格式規則見 [AGENTS.md](AGENTS.md)。
