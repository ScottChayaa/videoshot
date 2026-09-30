// mockup 專用：把某個時間點的畫面轉成可直接套用的 CSS 背景定位字串。
// 這是唯一沒有出現在 mockups/shared/storyboard.ts 的函式 —— 正式版改存單格縮圖後就用不到了
// （見設計規格第七節），但 mockup 仍然直接引用 i.ytimg.com 的整張 sheet，所以需要它。
//
// 這個檔案由 server.mjs 接在 mockups/shared/storyboard.ts 的轉譯結果後面一起送出，
// 因此可以直接使用 parseStoryboardSpec / pickLevel / frameAt / sheetUrl。

// 回傳可直接塞進 style 屬性的字串。用百分比定位，因此與元素實際尺寸無關，
// 任何 16:9 容器都能直接套用（效果等同 object-fit: cover）。
function frameStyle(spec, t) {
  const parsed = typeof spec === 'string' ? parseStoryboardSpec(spec) : spec;
  if (!parsed) return null;
  const level = pickLevel(parsed);
  if (!level) return null;
  const pos = frameAt(level, t);
  const px = level.cols > 1 ? (pos.col / (level.cols - 1)) * 100 : 0;
  const py = level.rows > 1 ? (pos.row / (level.rows - 1)) * 100 : 0;
  return [
    `background-image:url('${sheetUrl(parsed, level, pos.sheetIndex)}')`,
    `background-size:${level.cols * 100}% ${level.rows * 100}%`,
    `background-position:${px.toFixed(4)}% ${py.toFixed(4)}%`
  ].join(';');
}
