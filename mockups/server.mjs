// mockups 唯一的伺服器。根目錄執行 `pnpm mock`。
//
//   /uiux-v2/login.html  UI 排版驗收原型（假資料）
//   /probe/           storyboard 縮圖功能探測工具（打真的 YouTube）
//
// 為什麼合併成一台：先前 UIUX 走 `npx serve`（只有靜態檔）、探測工具走自己的 node 伺服器
// （多了 /api/*），開錯那台的症狀是「頁面看起來正常但 API 回 404 的 HTML」，很難第一眼看懂。
// 一台伺服器就沒有這個問題。
//
// 路由：
//   /api/spec?v=<videoId>    代抓 watch page，回傳 playerStoryboardSpecRenderer.spec
//   /api/sheet?u=<sheetUrl>  代理 i.ytimg.com 圖檔（補 CORS，讓前端量得到大小、canvas 不被污染）
//   /shared/storyboard.js    由 src/lib/storyboard.ts 即時轉譯而成（見下方 buildStoryboardJs）
//   其餘                     靜態檔
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const DIR = fileURLToPath(new URL('.', import.meta.url));
const ROOT = fileURLToPath(new URL('..', import.meta.url));
const PORT = Number(process.env.PORT) || 8231;
const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.md': 'text/markdown; charset=utf-8',
  '.json': 'application/json; charset=utf-8'
};

// mockup 的 storyboard 解碼邏輯直接來自正式版 src/lib/storyboard.ts，不留手抄副本。
// 曾經兩份並存，改 frameAt 的取整規則時必須手動同步兩邊，漏改一份會靜默不一致。
//
// 轉譯用專案自己的 typescript（已是 devDependency），不是正則剝型別。
// 輸出成 CommonJS 再包一層 IIFE，就能當成傳統 <script> 載入而不必把所有 mockup 頁面
// 改成模組 —— 模組一律 defer，會排在 app.js 之後執行，順序會壞掉。
async function buildStoryboardJs() {
  const source = await readFile(join(ROOT, 'src', 'lib', 'storyboard.ts'), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  });
  const frameStyle = await readFile(join(DIR, 'shared', 'frame-style.js'), 'utf8');

  // frameStyle 放進自己的作用域，用參數解構拿到核心函式。
  // 不能直接在同一層解構 —— TS 的 CommonJS 輸出保留了 `function parseStoryboardSpec` 宣告，
  // 同層再 `const { parseStoryboardSpec } = exports` 會撞名而整支腳本掛掉。
  return `// 自動產生，請勿直接編輯。
// 核心來自 src/lib/storyboard.ts，frameStyle 來自 mockups/shared/frame-style.js。
(function () {
const exports = {};
${outputText}
const core = {};
for (const k of Object.keys(exports)) if (k !== '__esModule') core[k] = exports[k];

const frameStyle = (function ({ parseStoryboardSpec, pickLevel, frameAt, sheetUrl }) {
${frameStyle}
return frameStyle;
})(core);

window.SB = Object.assign({ frameStyle }, core);
})();
`;
}

// 抓 watch page，撈出 spec 與幾個基本欄位。
// 失敗要分類（見設計規格第七節）：no_storyboard 是影片本身沒有，parse_failed 才是解析器壞了。
async function fetchSpec(videoId) {
  const res = await fetch(`https://www.youtube.com/watch?v=${encodeURIComponent(videoId)}`, {
    headers: { 'user-agent': UA, 'accept-language': 'zh-TW,zh;q=0.9,en;q=0.8' }
  });
  if (!res.ok) return { result: 'fetch_failed', status: res.status };
  const html = await res.text();

  const pick = (re) => {
    const m = html.match(re);
    return m ? m[1] : null;
  };
  const spec = pick(/"playerStoryboardSpecRenderer":\{"spec":"([^"]*)"/);
  const title = pick(/"videoDetails":\{[^{]*?"title":"([^"]*)"/) || pick(/<title>([^<]*)<\/title>/);
  const lengthSeconds = pick(/"lengthSeconds":"(\d+)"/);

  if (spec) {
    return {
      result: 'ok',
      videoId,
      spec,
      title: title ? JSON.parse(`"${title.replace(/"/g, '\\"')}"`) : null,
      duration: lengthSeconds ? Number(lengthSeconds) : null
    };
  }
  // 沒抓到 spec 時要分類，因為三種情況的處置完全不同（見設計規格第七節）。
  // 判定順序很重要：先確認影片本身能不能播，再談有沒有 storyboard。
  const playability = pick(/"playabilityStatus":\{"status":"(\w+)"/);
  const reason = pick(/"playabilityStatus":\{[^{]*?"reason":"([^"]*)"/);
  const hasVideoDetails = /"videoDetails":\{/.test(html);

  // 影片不存在／私人／已刪除／地區限制：playabilityStatus 在但不是 OK，且沒有 videoDetails。
  // 這是「輸入的影片有問題」，不是解析器壞了 —— 正式版不可據此發告警。
  if (playability && playability !== 'OK' && !hasVideoDetails) {
    return { result: 'video_unavailable', videoId, playability, reason, htmlBytes: html.length };
  }
  // 影片正常（videoDetails 在），只是沒有 storyboard：過短、直播中、或剛上傳尚未產生。
  if (hasVideoDetails) {
    return {
      result: 'no_storyboard',
      videoId,
      title,
      duration: lengthSeconds ? Number(lengthSeconds) : null,
      htmlBytes: html.length
    };
  }
  // 連 videoDetails 和 playabilityStatus 都撈不到 → 頁面結構變了，解析器失效。
  return { result: 'parse_failed', videoId, playability, htmlBytes: html.length };
}

const INDEX = `<!DOCTYPE html>
<html lang="zh-Hant"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>videoshot mockups</title>
<style>body{font:15px/1.8 system-ui,sans-serif;margin:40px auto;max-width:560px;padding:0 16px}
a{display:block;padding:14px 16px;margin:10px 0;border:1px solid #ccd;border-radius:8px;
text-decoration:none;color:#224}a:hover{background:#f4f6ff}b{display:block;font-size:16px}
small{color:#667}</style></head><body>
<h1>videoshot mockups</h1>
<a href="/uiux-v2/login.html"><b>UI 原型</b><small>假資料，驗收版面與互動 · 需先登入 · 另有驗收操作手冊</small></a>
<a href="/probe/"><b>storyboard 縮圖探測工具</b><small>打真的 YouTube，驗證縮圖解析與儲存量試算</small></a>
<a href="/uiux-v2/%E9%A9%97%E6%94%B6%E6%93%8D%E4%BD%9C%E6%89%8B%E5%86%8A.md"><b>驗收操作手冊</b><small>逐頁檢查清單</small></a>
</body></html>`;

createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`);

  if (url.pathname === '/api/spec') {
    const v = url.searchParams.get('v');
    res.setHeader('content-type', 'application/json; charset=utf-8');
    if (!v) return res.writeHead(400).end(JSON.stringify({ result: 'bad_request' }));
    try {
      const out = await fetchSpec(v);
      res.writeHead(out.result === 'ok' ? 200 : 502).end(JSON.stringify(out));
    } catch (e) {
      res.writeHead(502).end(JSON.stringify({ result: 'fetch_failed', error: String(e) }));
    }
    return;
  }

  if (url.pathname === '/api/sheet') {
    const u = url.searchParams.get('u');
    if (!u) return res.writeHead(400).end('bad_request');
    let target;
    try {
      target = new URL(u);
    } catch {
      return res.writeHead(400).end('bad_url');
    }
    if (target.hostname !== 'i.ytimg.com') return res.writeHead(403).end('host_not_allowed');
    try {
      const up = await fetch(target, { headers: { 'user-agent': UA } });
      // 上游失敗一律回 502，不要原樣轉發它的狀態碼。
      // 否則上游的 404 會跟「這台伺服器沒有這條路由」的 404 撞在一起，前端分不出來。
      if (!up.ok) {
        return res
          .writeHead(502, { 'content-type': 'application/json; charset=utf-8' })
          .end(JSON.stringify({ error: 'upstream', status: up.status, url: target.href }));
      }
      const buf = Buffer.from(await up.arrayBuffer());
      res.writeHead(200, {
        'content-type': up.headers.get('content-type') || 'application/octet-stream',
        'content-length': buf.length,
        'cache-control': 'no-store'
      }).end(buf);
    } catch (e) {
      res.writeHead(502, { 'content-type': 'application/json; charset=utf-8' })
         .end(JSON.stringify({ error: 'fetch_failed', message: String(e) }));
    }
    return;
  }

  if (url.pathname === '/shared/storyboard.js') {
    try {
      const body = await buildStoryboardJs();
      res.writeHead(200, { 'content-type': MIME['.js'], 'cache-control': 'no-store' }).end(body);
    } catch (e) {
      res.writeHead(500, { 'content-type': MIME['.js'] })
         .end(`console.error(${JSON.stringify('storyboard.js 轉譯失敗：' + String(e))});`);
    }
    return;
  }

  if (url.pathname === '/') {
    return res.writeHead(200, { 'content-type': MIME['.html'] }).end(INDEX);
  }

  const name = url.pathname.endsWith('/') ? url.pathname + 'index.html' : url.pathname;
  const file = join(DIR, normalize(decodeURIComponent(name)).replace(/^[\\/]+/, ''));
  // 擋掉 ../ 逃出 mockups/ 的路徑
  if (!file.startsWith(DIR.endsWith(sep) ? DIR : DIR + sep)) {
    return res.writeHead(403).end('forbidden');
  }
  try {
    const body = await readFile(file);
    res.writeHead(200, { 'content-type': MIME[extname(file)] || 'application/octet-stream' }).end(body);
  } catch {
    res.writeHead(404).end('not found');
  }
}).listen(PORT, () => {
  console.log(`  mockups   → http://localhost:${PORT}/`);
  console.log(`  UI 原型   → http://localhost:${PORT}/uiux-v2/login.html`);
  console.log(`  探測工具  → http://localhost:${PORT}/probe/`);
});
