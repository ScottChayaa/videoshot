// 原型｜實機對照截圖：同一個畫面各截一張，左原型、右實機，並排存成 PNG。
// 用法：pnpm compare [--dark] [--only 首頁,查詢] [--seed]
// 需要：另一個終端機跑著 pnpm mock（port 8231）、adb 連著一台裝上 debug 版的手機、本機有 Chrome
// （預設 /usr/bin/google-chrome，位置不同就設 CHROME 環境變數）。
import { chromium } from 'playwright-core';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';
import os from 'node:os';
import screens from './ui-compare.screens.mjs';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const BASE = 'http://localhost:8231/uiux-v2/';
const PKG = 'com.xenyaa.videoshot';
const ADB = path.join(process.env.ANDROID_HOME || path.join(os.homedir(), 'Android/Sdk'), 'platform-tools', 'adb');
const CHROME = process.env.CHROME || '/usr/bin/google-chrome';

// ---- 參數 ----
const args = process.argv.slice(2);
const dark = args.includes('--dark');
const seed = args.includes('--seed');
const onlyIdx = args.indexOf('--only');
const only = onlyIdx >= 0 ? (args[onlyIdx + 1] ?? '').split(',').map((s) => s.trim()).filter(Boolean) : null;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
// stderr 丟掉：MIUI 的 uiautomator dump 會印一長串無害的 stack trace
const adb = (...a) => execFileSync(ADB, a, { maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] });
const adbText = (...a) => adb(...a).toString('utf8');

function die(msg) {
  console.error(msg);
  process.exit(1);
}

// ---- 深色模式：記下原本的設定，結束時還原 ----
let originalNight = null;
function readNightMode() {
  // 輸出像 "Night mode: no"；auto／custom_schedule／custom_bedtime 也是 cmd uimode night 接受的值
  const m = adbText('shell', 'cmd', 'uimode', 'night').match(/Night mode:\s*(\S+)/i);
  return m ? m[1].toLowerCase() : null;
}
function restoreNightMode() {
  if (originalNight == null) return;
  try { adb('shell', 'cmd', 'uimode', 'night', originalNight); } catch { /* 裝置可能已斷線 */ }
}

// ---- 前置檢查 ----
async function preflight() {
  try {
    const res = await fetch(BASE + 'home.html');
    if (!res.ok) throw new Error(String(res.status));
  } catch {
    die('連不到原型伺服器（http://localhost:8231）。先在另一個終端機跑 pnpm mock');
  }
  let devices = '';
  try {
    devices = adbText('devices');
  } catch (e) {
    die(`執行不了 adb（${ADB}）：${e.message}`);
  }
  if (!devices.split('\n').slice(1).some((l) => /\tdevice$/.test(l.trim() ? l : ''))) {
    die('adb 沒有看到裝置。請接上手機並開啟 USB 偵錯');
  }
}

// ---- 假資料匯入（--seed）----
async function runSeed() {
  console.log('--seed：匯入假資料…');
  adb('shell', 'am', 'force-stop', PKG);
  // 不清 logcat（那是整個裝置共用的緩衝區）：記下裝置現在的時間，之後只看這個時間點以後的 VsSeed
  // date 整串當一個 shell 指令，不然空格會把它拆開
  const since = adbText('shell', "date '+%m-%d %H:%M:%S.000'").trim();
  adb('shell', 'am', 'broadcast', '-a', `${PKG}.debug.SEED`, '-n', `${PKG}/.debug.seed.SeedReceiver`);
  let done = false;
  for (let i = 0; i < 30 && !done; i++) {
    await sleep(1000);
    done = /\bdone\b/.test(adbText('logcat', '-d', '-s', 'VsSeed', '-T', since));
  }
  if (!done) die('--seed：30 秒內沒有在 logcat（VsSeed）看到 done');
  // 由接收器啟動的行程直接開 app 可能白畫面，先停掉再正常啟動一次
  adb('shell', 'am', 'force-stop', PKG);
  launchApp();
  console.log('--seed：匯入完成，等 60 秒讓回填抓縮圖…');
  await sleep(60_000);
}

function launchApp() {
  adb('shell', 'monkey', '-p', PKG, '-c', 'android.intent.category.LAUNCHER', '1');
}

// ---- 實機操作 ----
const decodeXml = (s) =>
  s.replace(/&#10;/g, '\n').replace(/&#13;/g, '\r').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');

function dumpNodes() {
  for (let attempt = 0; attempt < 4; attempt++) {
    try {
      adb('shell', 'uiautomator', 'dump', '/sdcard/vs.xml');
      const xml = adbText('shell', 'cat', '/sdcard/vs.xml');
      const nodes = [];
      // Compose 的文字節點常常 bounds 是 [0,0][0,0]（可點的是外層容器），
      // 所以用堆疊追蹤祖先，空 bounds 時改用最近一個有面積的祖先。
      const stack = [];
      for (const m of xml.matchAll(/<node\b[^>]*?(\/?)>|<\/node>/g)) {
        const tag = m[0];
        if (tag === '</node>') { stack.pop(); continue; }
        const attr = (n) => {
          const r = tag.match(new RegExp(`\\s${n}="([^"]*)"`));
          return r ? decodeXml(r[1]) : '';
        };
        const b = attr('bounds').match(/\[(\d+),(\d+)\]\[(\d+),(\d+)\]/);
        let box = b ? { x1: +b[1], y1: +b[2], x2: +b[3], y2: +b[4] } : null;
        if (!box || box.x2 <= box.x1 || box.y2 <= box.y1) {
          box = [...stack].reverse().find((a) => a && a.x2 > a.x1 && a.y2 > a.y1) ?? null;
        }
        nodes.push({ text: attr('text'), desc: attr('content-desc'), ...(box ?? { x1: 0, y1: 0, x2: 0, y2: 0 }) });
        if (m[1] !== '/') stack.push(box);
      }
      if (nodes.length) return nodes;
    } catch { /* 動畫中 dump 可能失敗，重試 */ }
    execFileSync('sleep', ['1']);
  }
  return [];
}

function tapNode(n) {
  adb('shell', 'input', 'tap', String(Math.round((n.x1 + n.x2) / 2)), String(Math.round((n.y1 + n.y2) / 2)));
}

// 找不到時等一下再試幾次（畫面還在載入）。多個符合時取最靠下的（底部導覽優先於頁面標題）。
async function findAndTap(pred, label) {
  let lastSeen = '';
  for (let attempt = 0; attempt < 4; attempt++) {
    const nodes = dumpNodes();
    lastSeen = nodes.map((n) => n.text || n.desc).filter(Boolean).slice(0, 25).join(' | ');
    const hits = nodes.filter((n) => n.x2 > n.x1 && n.y2 > n.y1 && pred(n));
    if (hits.length) {
      hits.sort((a, b) => b.y1 - a.y1);
      tapNode(hits[0]);
      return;
    }
    await sleep(1000);
  }
  throw new Error(`實機畫面上找不到：${label}（畫面上有：${lastSeen}）`);
}

async function runDeviceSteps(steps) {
  for (const s of steps) {
    if (s.wait != null) await sleep(s.wait);
    else if (s.tapText != null) {
      await findAndTap((n) => n.text === s.tapText || n.desc === s.tapText, `文字「${s.tapText}」`);
      await sleep(800);
    } else if (s.tapDescPrefix != null) {
      await findAndTap((n) => n.desc.startsWith(s.tapDescPrefix), `描述開頭「${s.tapDescPrefix}」`);
      await sleep(800);
    } else throw new Error(`不認得的實機步驟：${JSON.stringify(s)}`);
  }
}

function deviceShot() {
  return adb('exec-out', 'screencap', '-p');
}

// ---- 原型操作 ----
async function runMockupSteps(page, steps = []) {
  for (const s of steps) {
    if (typeof s === 'string') await page.evaluate(s);
    else if (s.click) await page.click(s.click);
    else if (s.js) await page.evaluate(s.js);
    else if (s.wait != null) await page.waitForTimeout(s.wait);
    else throw new Error(`不認得的原型步驟：${JSON.stringify(s)}`);
  }
}

// ---- 合成 ----
const esc = (s) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

async function compose(browser, outDir, file, title, cells, cols, cellW) {
  const bg = '#222', fg = '#eee';
  const html = `<!doctype html><meta charset="utf-8"><body style="margin:0;background:${bg};color:${fg};font-family:sans-serif">
<div style="display:grid;grid-template-columns:repeat(${cols},auto);gap:24px;padding:16px;width:max-content">
${cells.map((c) => `<div><div style="font-size:22px;margin:0 0 8px">${esc(c.title)}</div>
<div style="display:flex;gap:8px;align-items:flex-start">
<img src="${c.left}" style="width:${cellW}px;display:block"><img src="${c.right}" style="width:${cellW}px;display:block"></div></div>`).join('\n')}
</div>`;
  const htmlPath = path.join(outDir, `_${file}.html`);
  writeFileSync(htmlPath, html);
  const page = await browser.newPage({ viewport: { width: 1200, height: 800 } });
  await page.goto(pathToFileURL(htmlPath).href);
  await page.waitForFunction(() => [...document.images].every((i) => i.complete && i.naturalWidth > 0));
  await page.screenshot({ path: path.join(outDir, file), fullPage: true });
  await page.close();
}

// ---- 主流程 ----
async function main() {
  await preflight();
  if (seed) await runSeed();

  const list = only ? screens.filter((s) => only.includes(s.name)) : screens;
  if (!list.length) die(`--only 沒有符合的畫面。可用：${screens.map((s) => s.name).join('、')}`);

  const stamp = new Date().toLocaleString('sv').replace(/[-: ]/g, '').replace(/^(\d{8})(\d{6})$/, '$1-$2');
  const outDir = path.join(ROOT, 'tmp/ui-compare', stamp);
  mkdirSync(outDir, { recursive: true });

  const failures = [];
  const browser = await chromium.launch({ executablePath: CHROME, headless: true });
  try {
    originalNight = readNightMode();
    adb('shell', 'cmd', 'uimode', 'night', dark ? 'yes' : 'no');
    await sleep(1500);

    const context = await browser.newContext({
      viewport: { width: 392, height: 850 },
      deviceScaleFactor: 2.75,
      colorScheme: dark ? 'dark' : 'light',
    });
    await context.addInitScript(() => {
      try {
        localStorage.setItem('ytspace2_user', JSON.stringify({ name: 'Scott Lin', mail: 'scott@example.com', initial: 'S' }));
        localStorage.removeItem('ytspace2_lb_hint');
      } catch { /* 無痕等情境 */ }
    });

    const done = [];
    for (const s of list) {
      process.stdout.write(`${s.name}… `);
      const errs = [];
      const leftFile = `_${s.name}.mockup.png`;
      const rightFile = `_${s.name}.device.png`;
      // 原型
      try {
        const page = await context.newPage();
        await page.goto(BASE + s.mockup.path);
        await runMockupSteps(page, s.mockup.steps);
        await page.waitForTimeout(800);
        await page.screenshot({ path: path.join(outDir, leftFile) });
        await page.close();
      } catch (e) {
        errs.push(`原型：${e.message.split('\n')[0]}`);
      }
      // 實機
      try {
        adb('shell', 'am', 'force-stop', PKG);
        launchApp();
        await sleep(2000);
        await runDeviceSteps(s.device.steps);
        await sleep(1500); // 等轉場動畫與資料載入結束再截圖
        writeFileSync(path.join(outDir, rightFile), deviceShot());
      } catch (e) {
        errs.push(`實機：${e.message.split('\n')[0]}`);
      }
      if (errs.length) {
        failures.push(`${s.name}：${errs.join('；')}`);
        console.log('失敗');
        continue;
      }
      await compose(browser, outDir, `${s.name}.png`, s.name, [{ title: `${s.name}　左：原型　右：實機`, left: leftFile, right: rightFile }], 1, 540);
      done.push({ title: `${s.name}　左：原型　右：實機`, left: leftFile, right: rightFile });
      console.log('ok');
    }
    if (done.length) await compose(browser, outDir, 'all.png', 'all', done, 3, 360);
  } finally {
    await browser.close();
    restoreNightMode();
  }

  console.log(`\n輸出資料夾：${outDir}`);
  if (failures.length) {
    console.log('失敗清單：');
    for (const f of failures) console.log(`  - ${f}`);
    process.exitCode = 1;
  } else console.log('失敗清單：（無）');
}

main().catch((e) => {
  restoreNightMode();
  die(e.stack || String(e));
});
