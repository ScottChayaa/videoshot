# 階段 14：清理 web 程式碼

[← 回索引](README.md)

- **日期**：2026-09-30
- **狀態**：完成

## 做了什麼

刪除舊名 yt-space 時期的 web 版程式碼（內容留在 git 歷史）。`src/lib/` 的 storyboard 搬到 `mockups/shared/storyboard.ts`（原型伺服器執行期要讀它，不能刪），測試在 git 歷史。舊名只剩原型的 localStorage key `ytspace2_*`，刻意不改。
