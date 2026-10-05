# 怎麼在手機上看 app 的檔案？

[← 回目錄](README.md)

- 只有開發測試版能看（`run-as` 需要 debug 版）
- 終端機找不到 `adb`：它不在系統 PATH 上，先設定

  ```bash
  export PATH=$PATH:$HOME/Android/Sdk/platform-tools
  ```

  - 只對目前分頁有效；要永久生效就加進 `~/.bashrc`
- 列出縮圖檔：

  ```bash
  adb shell run-as com.xenyaa.videoshot ls -R files/thumbs
  ```

- 看總大小：

  ```bash
  adb shell run-as com.xenyaa.videoshot du -sh files/thumbs
  ```

- 把 `library.db` 拉到電腦（WAL 也要拉，不然看不到最新資料）：

  ```bash
  for f in library.db library.db-wal library.db-shm; do adb exec-out run-as com.xenyaa.videoshot cat files/$f > $f; done
  ```

相關：[首頁的圖存在哪裡？](首頁的圖存在哪裡.md)
