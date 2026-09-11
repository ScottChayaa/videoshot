// videoshot mockup — 共用互動 + SVG icon 系統
const M = window.MOCK;

/* ───────── SVG icon（Lucide 風格，無 emoji）───────── */
function svg(inner, cls = 'ic', filled = false) {
  return `<svg class="${cls}" viewBox="0 0 24 24" fill="${filled ? 'currentColor' : 'none'}" stroke="${filled ? 'none' : 'currentColor'}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${inner}</svg>`;
}
const ICONS = {
  home: '<path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M9 22V12h6v10"/>',
  tag: '<path d="M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z"/><circle cx="7.5" cy="7.5" r="1.5"/>',
  inbox: '<path d="M22 12h-6l-2 3h-4l-2-3H2"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>',
  search: '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
  back: '<path d="m15 18-6-6 6-6"/>',
  history: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/><path d="M12 7.5V12l3 2"/>',
  more: '<circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/>',
  play: '<path d="M6 3 20 12 6 21Z"/>',
  pause: '<rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/>',
  record: '<circle cx="12" cy="12" r="6"/>',
  close: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
  calendar: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4"/><path d="M8 2v4"/><path d="M3 10h18"/>',
  chevronDown: '<path d="m6 9 6 6 6-6"/>',
  chevronRight: '<path d="m9 18 6-6-6-6"/>',
  plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
  folder: '<path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/>',
  folderPlus: '<path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/><path d="M12 10v6"/><path d="M9 13h6"/>',
  share: '<path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/><polyline points="16 6 12 2 8 6"/><line x1="12" y1="2" x2="12" y2="15"/>',
  trash: '<path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>',
  edit: '<path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  fileCheck: '<path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="m9 14.5 2 2 4-4"/>',
  eye: '<path d="M2.06 12.35a1 1 0 0 1 0-.7 10.75 10.75 0 0 1 19.88 0 1 1 0 0 1 0 .7 10.75 10.75 0 0 1-19.88 0"/><circle cx="12" cy="12" r="3"/>',
  filter: '<polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"/>',
  imagePlus: '<path d="M16 5h6"/><path d="M19 2v6"/><path d="M21 11.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.5"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/><circle cx="9" cy="9" r="2"/>',
  film: '<rect x="2" y="2" width="20" height="20" rx="2.18" ry="2.18"/><line x1="7" y1="2" x2="7" y2="22"/><line x1="17" y1="2" x2="17" y2="22"/><line x1="2" y1="12" x2="22" y2="12"/>',
  sparkles: '<path d="M12 3v4"/><path d="M12 17v4"/><path d="M3 12h4"/><path d="M17 12h4"/><path d="M7.5 7.5 5 5"/><path d="M19 19l-2.5-2.5"/><path d="M16.5 7.5 19 5"/><path d="M5 19l2.5-2.5"/><circle cx="12" cy="12" r="2.5"/>',
  logOut: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>'
};
// 標籤分類 icon（保留分類意義，改用 SVG + 顏色編碼）
const KIND = {
  person: { icon: '<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>', color: '#6366F1', label: '人物' },
  place:  { icon: '<path d="M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"/><circle cx="12" cy="10" r="3"/>', color: '#0EA5E9', label: '地點' },
  topic:  { icon: '<line x1="4" y1="9" x2="20" y2="9"/><line x1="4" y1="15" x2="20" y2="15"/><line x1="10" y1="3" x2="8" y2="21"/><line x1="16" y1="3" x2="14" y2="21"/>', color: '#8B5CF6', label: '主題' },
  pet:    { icon: '<circle cx="11" cy="4.5" r="1.9"/><circle cx="17.7" cy="8" r="1.9"/><circle cx="19.6" cy="15.4" r="1.9"/><path d="M9.2 10.2a4.8 4.8 0 0 1 4.8 4.8v3.3a3.3 3.3 0 0 1-6.5 1Q6.5 17.4 4.6 16.8a3.3 3.3 0 0 1 1-6.6z"/>', color: '#F59E0B', label: '動物' },
  other:  { icon: '<path d="M12 3 21 12 12 21 3 12z"/>', color: '#64748B', label: '其他' }
};
function kindSvg(kind) {
  const k = KIND[kind] || KIND.other;
  return `<svg class="kico" style="color:${k.color}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${k.icon}</svg>`;
}

/* 標籤 chip HTML（統一產生器）；plainHash: 一律用中性 # icon（首頁縮圖標籤）*/
function tagChip(tag, opts = {}) {
  const cls = ['chip', 'mini', opts.selected ? 'sel' : ''].join(' ').trim();
  const icon = opts.plainHash
    ? `<svg class="kico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${KIND.topic.icon}</svg>`
    : kindSvg(tag.kind);
  const key = opts.key ? ` data-key="${opts.key}"` : '';
  return `<span class="${cls}" data-name="${tag.name}"${key}>${icon}<span class="cn">${tag.name}</span></span>`;
}

/* ───────── 底部導覽（5 項，SVG icon + 文字標籤）─────────
   v1 是純圖示：紅色「＋」與頭像都不具自解釋性，而且程式叫「設定」、
   手冊叫「帳號」。v2 一律補上文字，名稱以程式為準。 */
function renderNav(active) {
  const items = [
    { id: 'home', href: 'home.html', ico: 'home', label: '首頁' },
    { id: 'tags', href: 'tags.html', ico: 'search', label: '查詢' },
    { id: 'capture', href: 'capture.html', ico: 'plus', label: '取圖', cls: 'capture' },
    { id: 'folders', href: 'folders.html', ico: 'folder', label: '分類' },
    { id: 'account', href: 'account.html', avatar: 'S', label: '設定' }
  ];
  return `<nav class="bottom-nav">${items.map(it => {
    const inner = it.avatar ? `<span class="avatar">${it.avatar}</span>` : svg(ICONS[it.ico], 'navico');
    const on = active === it.id;
    return `<a href="${it.href}" class="${[it.cls || '', on ? 'active' : ''].join(' ').trim()}"${on ? ' aria-current="page"' : ''}>
      <span class="nav-ic-wrap">${inner}</span><span class="nav-label">${it.label}</span>
    </a>`;
  }).join('')}</nav>`;
}

/* ───────── 長按偵測（點=tap，長按=hold）───────── */
function bindPress(el, { onTap, onHold, delay = 450 }) {
  let timer = null, held = false, sx = 0, sy = 0;
  el.addEventListener('pointerdown', (e) => {
    held = false; sx = e.clientX; sy = e.clientY;
    timer = setTimeout(() => { held = true; navigator.vibrate?.(15); onHold?.(); }, delay);
  });
  const cancel = () => clearTimeout(timer);
  el.addEventListener('pointermove', (e) => { if (Math.abs(e.clientX - sx) > 10 || Math.abs(e.clientY - sy) > 10) cancel(); });
  el.addEventListener('pointerup', () => { cancel(); if (!held) onTap?.(); });
  el.addEventListener('pointercancel', cancel);
  el.addEventListener('pointerleave', cancel);
}

/* 首頁 tile 用：取 top 2 標籤（依種類排序，地點優先）*/
function top2Tags(clip) {
  const order = { place: 0, person: 1, topic: 2, pet: 3, other: 4 };
  return [...M.shotFacets(clip)]
    .sort((a, b) => order[a.kind] - order[b.kind])
    .slice(0, 2);
}

/* ───────── 底部編輯 sheet ───────── */
function openSheet(clip) {
  const v = M.videoOf(clip);
  let bd = document.getElementById('sheet-backdrop');
  if (!bd) {
    bd = document.createElement('div'); bd.id = 'sheet-backdrop'; bd.className = 'sheet-backdrop';
    const sh = document.createElement('div'); sh.id = 'sheet'; sh.className = 'sheet';
    document.body.append(bd, sh);
    bd.addEventListener('click', closeSheet);
  }
  const sh = document.getElementById('sheet');
  const tagChips = clip.tags.length
    ? clip.tags.map(t => tagChip(t)).join('')
    : '<span style="color:var(--text-dim);font-size:.85rem">尚無標籤</span>';
  const dateWarn = clip.eventDate.slice(0, 7) !== (v.publishedAt || '').slice(0, 7);
  sh.innerHTML = `
    <div class="handle"></div>
    <div class="srow">
      <span class="trange">${M.fmtTime(clip.start)} – ${M.fmtTime(clip.end)}</span>
      <button class="close" onclick="closeSheet()">${svg(ICONS.close, 'ic')}</button>
    </div>
    <div class="adj"><span class="lab">起</span><div class="btns"><button>設為目前</button><button>−5s</button><button>+5s</button></div></div>
    <div class="adj"><span class="lab">迄</span><div class="btns"><button>設為目前</button><button>−5s</button><button>+5s</button></div></div>
    <div class="field"><label>備註</label><textarea rows="2">${clip.note || ''}</textarea></div>
    <button class="analyze">AI 分析這段　·　約 30 秒 · 區間分析</button>
    <div class="field"><label>摘要</label><textarea rows="2">${clip.summary || ''}</textarea></div>
    <div class="field"><label>標籤</label><div class="chips">${tagChips}</div></div>
    <div class="field"><label>事件日期</label><input type="date" value="${clip.eventDate}">
      ${dateWarn ? '<div class="warn-line">⚠ 與上傳日不同（事件發生日）</div>' : ''}</div>
    <button class="confirm" onclick="closeSheet()">✓ 確認</button>
  `;
  requestAnimationFrame(() => { bd.classList.add('show'); sh.classList.add('show'); });
}
function closeSheet() {
  document.getElementById('sheet-backdrop')?.classList.remove('show');
  document.getElementById('sheet')?.classList.remove('show');
}
window.openSheet = openSheet; window.closeSheet = closeSheet;

/* ───────── 共用月份選擇器（單擊即選，年份/月份皆為 desc）─────────
   opts: { value:'YYYY-MM'|'', title, onPick(v), onClear() } */
const monthLabel = s => { const [y, m] = s.split('-'); return `${y}年${+m}月`; };

function ensureMonthPicker() {
  let bd = document.getElementById('mpBackdrop');
  if (bd) return;
  bd = document.createElement('div'); bd.id = 'mpBackdrop'; bd.className = 'dp-backdrop';
  const panel = document.createElement('div'); panel.id = 'mpPanel'; panel.className = 'dp-panel';
  panel.innerHTML = `<div class="handle"></div>
    <div class="dp-head" id="mpHead"><span id="mpTitle"></span><button class="dp-clear" id="mpClear">清除</button></div>
    <div class="dp-years" id="mpYears"></div>`;
  document.body.append(bd, panel);
  bd.addEventListener('click', closeMonthPicker);
}
function closeMonthPicker() {
  document.getElementById('mpBackdrop')?.classList.remove('show');
  document.getElementById('mpPanel')?.classList.remove('show');
}
function openMonthPicker(opts = {}) {
  ensureMonthPicker();
  const { value = '', title = '', onPick, onClear } = opts;   // title / onClear 都沒給 → 整條標題列隱藏
  // 資料涵蓋年份（以 reviewed clip 為準）
  const months = M.CLIPS.map(c => c.eventDate.slice(0, 7));
  // 年份範圍取資料涵蓋區間，並確保目前選定的月份一定在清單內
  const vY = value ? +value.slice(0, 4) : null;
  const minY = Math.min(+months.reduce((a, b) => a < b ? a : b).slice(0, 4), vY ?? Infinity);
  const maxY = Math.max(+months.reduce((a, b) => a > b ? a : b).slice(0, 4), vY ?? -Infinity);
  // 有資料的月份（無資料者淡化但仍可點）
  const has = new Set(months);

  document.getElementById('mpHead').style.display = (title || onClear) ? '' : 'none';
  document.getElementById('mpTitle').textContent = title;
  document.getElementById('mpClear').style.display = onClear ? '' : 'none';
  const yearsEl = document.getElementById('mpYears');
  yearsEl.innerHTML = '';
  for (let y = maxY; y >= minY; y--) {           // 年份 desc
    const sec = document.createElement('div'); sec.className = 'dp-year';
    sec.innerHTML = `<h4>${y}年</h4><div class="dp-grid"></div>`;
    const grid = sec.querySelector('.dp-grid');
    for (let mo = 12; mo >= 1; mo--) {           // 月份 desc
      const key = `${y}-${String(mo).padStart(2, '0')}`;
      const b = document.createElement('button');
      b.className = 'dp-month' + (key === value ? ' sel' : '') + (has.has(key) ? '' : ' dim');
      b.textContent = mo + '月';
      b.onclick = () => { closeMonthPicker(); onPick?.(key); };   // 單擊即完成
      grid.appendChild(b);
    }
    yearsEl.appendChild(sec);
  }
  document.getElementById('mpClear').onclick = () => { closeMonthPicker(); onClear?.(); };
  document.getElementById('mpBackdrop').classList.add('show');
  document.getElementById('mpPanel').classList.add('show');
  yearsEl.querySelector('.dp-month.sel')?.scrollIntoView({ block: 'center' });
}

window.APP = { svg, ICONS, KIND, kindSvg, tagChip, renderNav, bindPress, top2Tags, openSheet, closeSheet, openMonthPicker, closeMonthPicker, monthLabel };

/* ═══════════ Shot 版共用元件（2026-08-28）═══════════ */

/* 簡易 toast */
function toast(text, ms = 2200) {
  let el = document.getElementById('app-toast');
  if (!el) { el = document.createElement('div'); el.id = 'app-toast'; el.className = 'wz-toast'; document.body.appendChild(el); }
  el.innerHTML = `<span>${text}</span>`;
  el.style.display = 'flex';
  clearTimeout(toast._t);
  toast._t = setTimeout(() => { el.style.display = 'none'; }, ms);
}

/* 數字加千分位：分類張數上千之後，1234 看起來像年份，1,234 才一眼是數量 */
function num(n) { return Number(n).toLocaleString('en-US'); }

/* 泛用底部 sheet（沿用 .sheet 樣式） */
function openPanel(html) {
  let bd = document.getElementById('sheet-backdrop');
  if (!bd) {
    bd = document.createElement('div'); bd.id = 'sheet-backdrop'; bd.className = 'sheet-backdrop';
    const sh = document.createElement('div'); sh.id = 'sheet'; sh.className = 'sheet';
    document.body.append(bd, sh);
    bd.addEventListener('click', closeSheet);
  }
  const sh = document.getElementById('sheet');
  sh.innerHTML = `<div class="handle"></div>` + html;
  requestAnimationFrame(() => { bd.classList.add('show'); sh.classList.add('show'); });
  return sh;
}

/* ── 自繪播放器（詳情頁與取圖精靈第二步共用）──
   opts: { videoId, duration, at, onSeek(sec), onPlay(sec) }
   回傳 { seek(sec), setRegions(clips, selId), time } */
function mountPlayer(host, opts) {
  const D = opts.duration;
  host.classList.add('player');
  // 控制項全部疊在畫面上（比照 YT）：左下時間、底部進度條，沒有另外的黑色控制列
  host.innerHTML = `
    <div class="stage">
      <div class="sb-frame" role="img" aria-label="播放畫面"></div>
      <button class="center-play">${svg(ICONS.play, 'ic', true)}</button>
      <span class="time"><span class="cur">0:00</span> / ${M.fmtTime(D)}</span>
      <div class="track"><div class="rail"></div><div class="fill" style="width:0%"></div><div class="head" style="left:0%"></div></div>
    </div>`;
  const q = sel => host.querySelector(sel);
  let cur = 0;
  function paint() {
    q('.cur').textContent = M.fmtTime(cur);
    q('.sb-frame').style.cssText = M.thumbStyle(opts.videoId, cur);
    q('.fill').style.width = q('.head').style.left = (cur / D * 100) + '%';
  }
  function seek(sec, notify) {
    cur = Math.max(0, Math.min(Math.round(sec), D));
    paint();
    if (notify) opts.onSeek?.(cur);
  }
  q('.track').onclick = e => {
    const r = e.currentTarget.getBoundingClientRect();
    seek((e.clientX - r.left) / r.width * D, true);
  };
  q('.center-play').onclick = () =>
    (opts.onPlay || (() => alert('（原型）播放器會 seekTo 並播放 · 正式版用 YT iframe API')))(cur);

  // 進度條上的收藏標示（詳情頁用；精靈第二步不傳就不畫）
  function setRegions(items, selId) {
    const track = q('.track');
    track.querySelectorAll('.region').forEach(e => e.remove());
    for (const c of items || []) {
      const r = document.createElement('div');
      r.className = 'region' + (c.id === selId ? ' sel' : '');
      r.style.left = (c.start / D * 100) + '%';
      r.style.width = (8 / D * 100) + '%';       // shot 是單一時間點，給它看得見的最小寬度
      track.appendChild(r);
    }
  }
  seek(opts.at || 0);
  return { seek: sec => seek(sec), setRegions, get time() { return cur; } };
}

/* ── 標籤編輯器（chip 形式；就地編輯與精靈第三步共用）──
   host: 容器元素；tags: [{name,kind}]；onChange(tags) 每次變動都會呼叫。
   host.dataset.mixed='1' 代表多張圖的值不一致，先顯示〈多個值〉，動過才開始收集。*/
function tagEditor(host, tags, onChange) {
  let list = tags.map(t => ({ ...t }));
  let open = false, newKind = 'topic';
  const isMixed = () => host.dataset.mixed === '1';
  function touched() { host.dataset.mixed = '0'; onChange?.(list); }

  function render() {
    const known = M.allTags();
    const pool = known.filter(t => !list.some(x => x.name === t.name));
    host.innerHTML = `
      <div class="te-row">
        ${isMixed() ? '<span class="te-mixed">〈多個值〉</span>'
          : list.map(t => `<span class="chip mini te-chip">${kindSvg(t.kind)}<span class="cn">${t.name}</span><button type="button" class="x" data-del="${t.name}">×</button></span>`).join('')}
        <button type="button" class="te-add ${open ? 'on' : ''}">＋</button>
      </div>
      ${open ? `<div class="te-pick">
        <div class="te-pool">${pool.length
          ? pool.map(t => `<span class="chip mini" data-add="${t.name}" data-kind="${t.kind}">${kindSvg(t.kind)}<span class="cn">${t.name}</span></span>`).join('')
          : '<span class="te-hint">既有標籤都加過了</span>'}</div>
        <div class="te-new">
          <input type="text" class="te-name" placeholder="新標籤">
          <button type="button" class="te-ok">新增</button>
        </div>
        <div class="icon-pick te-kind">${Object.entries(KIND).filter(([k]) => k !== 'place')
          .map(([k, v]) => `<button type="button" class="${k === newKind ? 'on' : ''}" data-kind="${k}">${kindSvg(k)}<span>${v.label}</span></button>`).join('')}</div>
      </div>` : ''}`;

    host.querySelectorAll('[data-del]').forEach(b => b.onclick = () => {
      list = list.filter(t => t.name !== b.dataset.del); touched(); render();
    });
    host.querySelector('.te-add').onclick = () => { open = !open; render(); };
    host.querySelectorAll('[data-add]').forEach(el => el.onclick = () => {
      if (isMixed()) list = [];            // 從〈多個值〉開始編輯＝整組換掉
      list.push({ name: el.dataset.add, kind: el.dataset.kind });
      touched(); render();
    });
    const ok = host.querySelector('.te-ok');
    if (ok) ok.onclick = () => {
      const name = host.querySelector('.te-name').value.trim();
      if (!name || list.some(t => t.name === name)) return;
      if (isMixed()) list = [];
      list.push({ name, kind: newKind });
      touched(); render();
    };
    // 新標籤要用哪個圖示（沿用設定頁「顯示圖示」的挑選方式）
    host.querySelectorAll('.te-kind button').forEach(b => b.onclick = () => {
      newKind = b.dataset.kind;
      host.querySelectorAll('.te-kind button').forEach(x => x.classList.toggle('on', x === b));
    });
  }
  render();
}

/* ── 地點建議：點一下既有地點就填入（等同 SELECT DISTINCT place）── */
function placeSuggest(host, input, onPick) {
  // 只列常用的幾個，避免建議列比欄位本身還長
  const places = M.allPlaces().filter(x => x.name !== input.value).slice(0, 8);
  host.innerHTML = places.map(p => `<span class="chip mini" data-p="${p.name}">${kindSvg('place')}<span class="cn">${p.name}</span></span>`).join('');
  host.querySelectorAll('[data-p]').forEach(el => el.onclick = () => {
    input.value = el.dataset.p; onPick?.();
    placeSuggest(host, input, onPick);          // 重畫，讓剛選的那個從建議中消失
  });
}

/* ── shot 就地編輯（時間／地點／標籤／描述）──
   欄位順序與精靈第三步的抽屜一致：時間最上（首頁分組依據），描述最下。*/
function openShotSheet(shot, onSave) {
  const v = M.videoOf(shot);
  const beforeMonth = shot.eventDate.slice(0, 7);
  // 只有真的來自 YT 拍攝日才標示；其餘（上傳日、使用者改過）都不加註解
  const dateNote = shot.dateSrc === 'recorded' ? '<div class="src-line">來自 YT 拍攝日</div>' : '';
  const sh = openPanel(`
    <div class="srow">
      <span class="trange">《${v.title}》</span>
      <button class="close" onclick="closeSheet()">${svg(ICONS.close, 'ic')}</button>
    </div>
    <div class="field"><label>時間（事件發生日）</label><input id="es-date" type="date" value="${shot.eventDate}">${dateNote}</div>
    <div class="field"><label>地點</label><input id="es-place" type="text" value="${shot.place || ''}" placeholder="例：宜蘭">
      <div class="sug-label">用過的地點</div><div class="sug" id="es-places"></div></div>
    <div class="field"><label>標籤</label><div class="tag-editor" id="es-tags"></div></div>
    <div class="field"><label>描述</label><textarea id="es-desc" rows="2" placeholder="留空，之後 AI 補">${shot.summary || ''}</textarea></div>
    <button class="btn primary block confirm" id="es-save">${svg(ICONS.check, 'ic')} 儲存</button>
  `);
  const placeInput = sh.querySelector('#es-place');
  placeSuggest(sh.querySelector('#es-places'), placeInput);
  let tags = shot.tags.map(t => ({ ...t }));
  tagEditor(sh.querySelector('#es-tags'), tags, next => tags = next);

  sh.querySelector('#es-save').onclick = () => {
    shot.summary = sh.querySelector('#es-desc').value.trim();
    shot.place = placeInput.value.trim();
    const d = sh.querySelector('#es-date').value;
    if (d && d !== shot.eventDate) { shot.eventDate = d; shot.dateSrc = 'user'; }
    shot.tags = tags;
    closeSheet();
    const moved = shot.eventDate.slice(0, 7) !== beforeMonth;
    toast(moved ? `已儲存 ・ 已移到 ${monthLabel(shot.eventDate.slice(0, 7))}` : '已儲存');
    onSave?.();
  };
}

/* ── 加入分類（資料夾樹勾選）── */
function openFolderPicker(shot, onChange) {
  function render() {
    const rows = [];
    (function walk(parent, depth) {
      for (const f of M.folderChildren(parent)) {
        const on = (M.FOLDER_SHOTS[f.id] || new Set()).has(shot.id);
        rows.push(`<div class="fp-row ${depth ? 'child' + (depth > 1 ? '2' : '') : ''} ${on ? 'on' : ''}" data-id="${f.id}">
          ${svg(ICONS.folder, 'ic')}<span class="name">${f.name}</span><span class="box">${on ? '✓' : ''}</span></div>`);
        walk(f.id, depth + 1);
      }
    })(null, 0);
    const sh = openPanel(`
      <div class="srow"><span class="trange">加入分類</span>
        <button class="close" onclick="closeSheet()">${svg(ICONS.close, 'ic')}</button></div>
      ${rows.join('') || '<div class="empty">還沒有任何資料夾</div>'}
      <button class="btn secondary block" id="fp-add" style="margin-top:var(--s4)">${svg(ICONS.folderPlus, 'ic')} 新增資料夾</button>
    `);
    sh.querySelectorAll('.fp-row').forEach(row => row.onclick = () => {
      const set = M.FOLDER_SHOTS[row.dataset.id];
      set.has(shot.id) ? set.delete(shot.id) : set.add(shot.id);
      render(); onChange?.();
    });
    sh.querySelector('#fp-add').onclick = () => {
      const name = prompt('資料夾名稱？');
      if (name) { M.addFolder(name.trim(), null); render(); }
    };
  }
  render();
}

/* ── Lightbox 全屏檢視器 ──
   list: shot 陣列（左右滑動範圍）；idx: 起點；opts.onChange: 內容變動時通知頁面重繪 */
function openLightbox(list, idx, opts = {}) {
  let lb = document.getElementById('lb');
  if (!lb) { lb = document.createElement('div'); lb.id = 'lb'; lb.className = 'lb'; document.body.appendChild(lb); }
  let i = idx;

  function close() { lb.classList.remove('show'); opts.onChange?.(); }

  function render() {
    if (!list.length) return close();
    i = Math.max(0, Math.min(i, list.length - 1));
    const s = list[i], v = M.videoOf(s);
    const facetChips = M.shotFacets(s).map(f => tagChip(f)).join('');
    // 第一次開啟才顯示滑動提示（v1 沒有任何提示，也沒有 N/M）
    const showHint = list.length > 1 && !localStorage.getItem('ytspace2_lb_hint');
    lb.innerHTML = `
      <div class="lb-top">
        <button id="lb-close" aria-label="關閉">${svg(ICONS.close, 'ic')}</button>
        <span class="lb-count">${M.fmtDateLabel(s.eventDate)}　·　第 ${i + 1} / 共 ${list.length} 張</span>
        <button id="lb-more" aria-label="更多動作">${svg(ICONS.more, 'ic', true)}</button>
      </div>
      <div class="lb-stage" id="lb-stage">
        <div class="lb-track" id="lb-track">
          <div class="lb-img" style="${list[i - 1] ? M.thumbStyle(list[i - 1].videoId, list[i - 1].start) : ''}"></div>
          <div class="lb-img" style="${M.thumbStyle(s.videoId, s.start)}"></div>
          <div class="lb-img" style="${list[i + 1] ? M.thumbStyle(list[i + 1].videoId, list[i + 1].start) : ''}"></div>
        </div>
        ${showHint ? '<div class="lb-swipe">左右滑動看上一張／下一張</div>' : ''}
      </div>
      <div class="lb-info">
        <div class="d ${s.summary ? '' : 'dim'}">${s.summary || '還沒有描述'}</div>
        <div class="chips">${facetChips}</div>
        <div class="meta"><span class="vt">《${v.title}》</span><span class="at">${M.fmtTime(s.start)}</span></div>
      </div>
      <div class="lb-actions">
        <button class="btn primary" id="lb-play">${svg(ICONS.play, 'ic')} 播放這一段</button>
        <button class="btn on-dark" id="lb-folder" aria-label="加入分類">${svg(ICONS.folderPlus, 'ic')}</button>
        <button class="btn on-dark" id="lb-share" aria-label="分享">${svg(ICONS.share, 'ic')}</button>
      </div>`;
    if (showHint) setTimeout(() => {
      localStorage.setItem('ytspace2_lb_hint', '1');
      lb.querySelector('.lb-swipe')?.remove();
    }, 2600);
    lb.querySelector('#lb-close').onclick = close;
    // 編輯與刪除收進 ⋯，破壞性動作不再與瀏覽動作並排（v1 是 5 顆等權重按鈕）
    lb.querySelector('#lb-more').onclick = () => {
      const sh = openPanel(`
        <div class="srow"><span class="trange">這張圖</span>
          <button class="close" onclick="closeSheet()">${svg(ICONS.close, 'ic')}</button></div>
        <button class="act-row" id="lb-edit">${svg(ICONS.edit, 'ic')} 編輯圖資</button>
        <button class="act-row danger" id="lb-del">${svg(ICONS.trash, 'ic')} 刪除這張收藏</button>`);
      sh.querySelector('#lb-edit').onclick = () => {
        closeSheet();
        openShotSheet(s, () => { render(); opts.onChange?.(); });
      };
      sh.querySelector('#lb-del').onclick = () => {
        if (!confirm('刪除這張收藏？（YouTube 原片不受影響）')) return;
        closeSheet();
        M.removeClip(s.id); list.splice(i, 1); toast('已刪除');
        list.length ? render() : close();
      };
    };
    lb.querySelector('#lb-play').onclick = () => location.href = `detail.html?v=${s.videoId}&c=${s.id}`;
    lb.querySelector('#lb-folder').onclick = () => openFolderPicker(s);
    lb.querySelector('#lb-share').onclick = async () => {
      const url = `https://youtu.be/${M.realId ? M.realId(s.videoId) : s.videoId}?t=${s.start}`;
      if (navigator.share) { try { await navigator.share({ url }); } catch {} }
      else { try { await navigator.clipboard.writeText(url); } catch {} toast('已複製連結：' + url); }
    };
    /* 左右滑動：拖曳時圖片跟著走，放開就滑到下一張（沒有下一張則回彈）*/
    const stage = lb.querySelector('#lb-stage');
    const track = lb.querySelector('#lb-track');
    const W = () => stage.clientWidth;
    let sx = null, dx = 0;
    stage.onpointerdown = e => {
      sx = e.clientX; dx = 0;
      track.style.transition = 'none';
      stage.setPointerCapture(e.pointerId);
    };
    stage.onpointermove = e => {
      if (sx == null) return;
      dx = e.clientX - sx;
      // 到頭到尾時加阻尼，讓使用者感覺得到邊界
      if ((i === 0 && dx > 0) || (i === list.length - 1 && dx < 0)) dx *= 0.35;
      track.style.transform = `translateX(${-W() + dx}px)`;
    };
    stage.onpointerup = () => {
      if (sx == null) return;
      const moved = dx; sx = null;
      if (Math.abs(moved) < 1) return;                       // 純點擊，不動
      const dir = moved < 0 ? 1 : -1;
      const next = Math.abs(moved) > 48 && list[i + dir] ? i + dir : i;
      track.style.transition = 'transform .22s ease-out';
      track.style.transform = `translateX(${-W() - (next - i) * W()}px)`;
      const done = () => { track.removeEventListener('transitionend', done); i = next; render(); };
      track.addEventListener('transitionend', done);
    };
  }
  render();
  lb.classList.add('show');
}

Object.assign(window.APP, { num, toast, openPanel, openShotSheet, mountPlayer, tagEditor, placeSuggest, openFolderPicker, openLightbox });
