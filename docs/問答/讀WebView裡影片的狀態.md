# 怎麼讀 WebView 裡影片的實際狀態？

[← 回目錄](README.md)

播放器是 WebView 裡的 YouTube `<video>`。要確認「真的靜音了沒」「真的暫停了沒」「循環點對不對」，直接從 WebView 的除錯通道讀，不要只看畫面。

- 只有開發測試版（可除錯）的 WebView 開了除錯通道
- 找到通道、轉到開發機的 9222 埠：

  ```bash
  adb forward tcp:9222 localabstract:webview_devtools_remote_$(adb shell pidof com.xenyaa.videoshot | tr -d '\r')
  ```

- 用 node（內建 `fetch` 與 `WebSocket`）對每個頁面執行一段 JS：

  ```js
  // vstate.mjs
  const pages = await (await fetch('http://localhost:9222/json')).json();
  for (const p of pages.filter(p => p.type === 'page' && p.url !== 'about:blank')) {
    const ws = new WebSocket(p.webSocketDebuggerUrl);
    await new Promise(r => ws.onopen = r);
    const res = await new Promise(r => {
      ws.onmessage = e => r(JSON.parse(e.data));
      ws.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { returnByValue: true, expression:
        "(function(){var v=document.querySelector('video'); return v ? JSON.stringify({muted:v.muted, paused:v.paused, t:v.currentTime}) : 'no video'})()" } }));
    });
    console.log(p.url.slice(0, 60), res.result?.result?.value);
    ws.close();
  }
  ```

  ```bash
  node vstate.mjs
  ```

- app 重開後行程 ID 會變，要重新 `adb forward`；用完 `adb forward --remove tcp:9222`
- 要看一段時間內的變化（例如 YouTube 會不會把 `muted` 改回去），隔一兩秒多讀幾次，或在 JS 裡每 50 毫秒取樣

相關：[怎麼用 adb 驅動手機做驗收？](用adb驅動手機驗收.md)
