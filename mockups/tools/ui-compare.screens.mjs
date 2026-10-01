// 對照截圖的畫面清單。mockup.steps 在原型頁面裡執行；device.steps 用 adb 操作實機。
// 每個畫面在實機上都從「重新開 app、停在首頁」開始。
export default [
  { name: '首頁', mockup: { path: 'home.html' }, device: { steps: [] } },
  { name: 'Lightbox', mockup: { path: 'home.html', steps: [{ click: '.thumb' }] }, device: { steps: [{ tapDescPrefix: '夜潛第一次見到巨型犀牛蝦' }] } },
  { name: '查詢', mockup: { path: 'tags.html' }, device: { steps: [{ tapText: '查詢' }] } },
  { name: '取圖第一步', mockup: { path: 'capture.html' }, device: { steps: [{ tapText: '取圖' }] } },
  { name: '分類', mockup: { path: 'folders.html' }, device: { steps: [{ tapText: '分類' }] } },
  { name: '資料夾內容', mockup: { path: 'folder.html?id=f3' }, device: { steps: [{ tapText: '分類' }, { wait: 800 }, { tapText: '加勒比海之旅' }] } },
  { name: '帳號', mockup: { path: 'account.html' }, device: { steps: [{ tapText: '帳號' }] } },
];
