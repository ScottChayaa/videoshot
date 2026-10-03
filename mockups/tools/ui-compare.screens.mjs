// 對照截圖的畫面清單。mockup.steps 在原型頁面裡執行；device.steps 用 adb 操作實機。
// 每個畫面在實機上都從「重新開 app、停在首頁」開始。
export default [
  { name: '首頁', mockup: { path: 'home.html' }, device: { steps: [] } },
  { name: 'Lightbox', mockup: { path: 'home.html', steps: [{ click: '.thumb' }] }, device: { steps: [{ tapDescPrefix: '夜潛第一次見到巨型犀牛蝦' }] } },
  {
    name: '詳情',
    // 原型從首頁開 Lightbox 再按【播放這一段】進詳情，跟實機同一條路（聚焦的是同一張，不用猜 videoId）
    mockup: { path: 'home.html', steps: [{ click: '.thumb' }, { click: '#lb-play' }, { wait: 1200 }] },
    device: { steps: [{ tapDescPrefix: '夜潛第一次見到巨型犀牛蝦' }, { wait: 800 }, { tapText: '播放這一段' }, { wait: 4000 }] },
  },
  { name: '查詢', mockup: { path: 'tags.html' }, device: { steps: [{ tapText: '查詢' }] } },
  {
    name: '查詢結果',
    // 原型會沿用上一個畫面留在 localStorage 的查詢條件：先按「清除」把條件清乾淨（跟實機一樣從空白開始），
    // 再點一個標籤、按【查詢 N 個條件】
    mockup: { path: 'tags.html', steps: [{ js: "document.getElementById('reuseClear').click()" }, { click: '#tagCloud .chip[data-key="place:加勒比海"]' }, { click: '#go' }, { wait: 800 }] },
    device: { steps: [{ tapText: '查詢' }, { wait: 800 }, { tapTextPrefix: '加勒比海 ' }, { tapTextPrefix: '查詢 1 個條件' }, { wait: 1500 }] },
  },
  { name: '取圖第一步', mockup: { path: 'capture.html?new=1' }, device: { steps: [{ tapText: '取圖' }, { wait: 800 }, { tapText: '重新開始', optional: true }] } },
  {
    name: '取圖第二步',
    // ?new=1：不要跳出「上次做到…要接著做嗎？」（前一個畫面會在 localStorage 留草稿）
    mockup: { path: 'capture.html?new=1', steps: [{ click: '.wz-hist .hrow' }, { wait: 800 }] },
    // 實機點取圖紀錄後要連網抓 watch page 與縮圖，所以等 6 秒；舊草稿的詢問框出現時選重新開始
    device: { steps: [{ tapText: '取圖' }, { wait: 800 }, { tapText: '重新開始', optional: true }, { tapTextPrefix: '80後老登勇闖加勒比海無人島' }, { wait: 6000 }] },
  },
  {
    name: '取圖第三步',
    mockup: { path: 'capture.html?new=1', steps: [{ click: '.wz-hist .hrow' }, { wait: 800 }, { click: '.wz-cell:not(.locked) >> nth=1' }, { click: '.wz-foot .btn' }, { wait: 800 }] },
    device: { steps: [{ tapText: '取圖' }, { wait: 800 }, { tapText: '重新開始', optional: true }, { tapTextPrefix: '80後老登勇闖加勒比海無人島' }, { wait: 6000 },
                      { tapDescPrefix: '第 2 格' }, { tapTextPrefix: '下一步' }, { wait: 1500 }] },
  },
  { name: '分類', mockup: { path: 'folders.html' }, device: { steps: [{ tapText: '分類' }] } },
  { name: '資料夾內容', mockup: { path: 'folder.html?id=f3' }, device: { steps: [{ tapText: '分類' }, { wait: 800 }, { tapText: '加勒比海之旅' }] } },
  {
    name: '取圖設定',
    mockup: { path: 'setting.html?k=capture' },
    device: { steps: [{ tapText: '帳號' }, { wait: 800 }, { tapTextPrefix: '過濾相似強度' }, { wait: 800 }] },
  },
  { name: '帳號', mockup: { path: 'account.html' }, device: { steps: [{ tapText: '帳號' }] } },
];
