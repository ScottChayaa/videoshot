// videoshot mockup 假資料
// 3 個真實頻道，各 5 筆 clip，時間錯開不同月份（測時間軸）。
// videoId / 標題 為真實抓取；縮圖用 img.youtube.com。clip 細節為造假展示用。

const CHANNELS = {
  meow: '喵遊記',
  yao: '十三要和拳頭',
  okge: 'OK哥環球探海記'
};

// ── YouTube storyboard spec（真實抓取，2026-08-24）──
// 來源：watch page 的 playerStoryboardSpecRenderer.spec。正式版由後端動態抓取並把 sprite
// 轉存 R2；mockup 寫死一份即可，sprite 直接連 i.ytimg.com（載圖不受 CORS 限制）。
// 若簽章過期導致縮圖失效，會自動退回封面圖 —— 這條 fallback 路徑本來就要驗收。
const SB_SPECS = {
  'Pe226YsYVWk': 'https://i.ytimg.com/sb/Pe226YsYVWk/storyboard3_L$L/$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgjOws_TBg==|48#27#100#10#10#0#default#rs$AOn4CLCc-2MzDeOZ2KxhMsO2lxg6n3eP6w|80#45#146#10#10#10000#M$M#rs$AOn4CLAGIKYpgi6RypZo9RU-m9OXbiT1tQ|160#90#146#5#5#10000#M$M#rs$AOn4CLCG1ArzhpTwSRa2dMDEJLQaSI8RYQ|320#180#146#3#3#10000#M$M#rs$AOn4CLAFiMfmM5hu6l7YEkikZNJR20hqrw',
  'r4YnYow7L-4': 'https://i.ytimg.com/sb/r4YnYow7L-4/storyboard3_L$L/$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgiv97rTBg==|48#27#100#10#10#0#default#rs$AOn4CLBMPJslkkqpIznIgb0lu1O-ij7PYA|80#45#181#10#10#5000#M$M#rs$AOn4CLCIrLkBrqptkBiVECTECXO_J5PFFA|160#90#181#5#5#5000#M$M#rs$AOn4CLD7Hu3liPZX6QzqkdlS0TE30AIlew|320#180#181#3#3#5000#M$M#rs$AOn4CLDr_slqyqXJpntxh9WTUSm-C-kH3g',
  'AovUL5FMYnc': 'https://i.ytimg.com/sb/AovUL5FMYnc/storyboard3_L$L/$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgj96azTBg==|48#27#100#10#10#0#default#rs$AOn4CLCCLzWFGZ3JkwKaXI7J8uz9DSPL9Q|80#45#144#10#10#10000#M$M#rs$AOn4CLBU19lbWEOJN9MxSKNiifkqIAFcgw|160#90#144#5#5#10000#M$M#rs$AOn4CLAP5RzHttaz60IC7lVwlF58Iz4A4Q|320#180#144#3#3#10000#M$M#rs$AOn4CLDoxkM0Eg4ZVX8l9XNoTNeWhF5rfg',
  'DUmtBh5jUjk': 'https://i.ytimg.com/sb/DUmtBh5jUjk/storyboard3_L$L/$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgjO_-XSBg==|48#27#100#10#10#0#default#rs$AOn4CLAdmwkLOBGw5EcJa7YZWe4Y5MpZww|80#45#92#10#10#10000#M$M#rs$AOn4CLBBUAuk3gv9_rLwqptJrpvzrSqX_g|160#90#92#5#5#10000#M$M#rs$AOn4CLCqf5gnor2MHWo9_Em9ggAsWA0hVQ|320#180#92#3#3#10000#M$M#rs$AOn4CLABhfTWALzg6j3_IgTSQT65msTdzg',
  'nNLvL1z_mjE': 'https://i.ytimg.com/sb/nNLvL1z_mjE/storyboard3_L$L/$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgiO-7HTBg==|48#27#100#10#10#0#default#rs$AOn4CLDzoqlfEqBbNRfwhTZrgdG5ICupuQ|80#45#150#10#10#5000#M$M#rs$AOn4CLCsmvHm0vi1aYmTdYvdduJvKmUfZg|160#90#150#5#5#5000#M$M#rs$AOn4CLAWBavbEAmoFt-CYbTMn1kXUb431Q|320#180#150#3#3#5000#M$M#rs$AOn4CLCbFt08J_6ti2GFIsx9AHE1_pY0EQ',
  '1NJZKzeWbg8': 'https://i.ytimg.com/sb/1NJZKzeWbg8/storyboard3_L$L/$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgi3zJfSBg==|48#27#100#10#10#0#default#rs$AOn4CLBFqCQAHVtzwKeGRauJy6WQR2t_Jw|80#45#143#10#10#5000#M$M#rs$AOn4CLAvAdUjKVwTXSHrAYVbQ1u8QGa3mw|160#90#143#5#5#5000#M$M#rs$AOn4CLDP4_J1VJduVy_s3YPhAXEp43u-Sw|320#180#143#3#3#5000#M$M#rs$AOn4CLCk8iboiQF5VIvsToYlH-U9l6zAxQ',
  'ee9JlJh5mVw': 'https://i.ytimg.com/sb/ee9JlJh5mVw/storyboard3_L$L/$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgjI4L3RBg==|48#27#100#10#10#0#default#rs$AOn4CLCHuG7u2mubVhu5LwIdQgueuMC8NA|80#45#116#10#10#5000#M$M#rs$AOn4CLCPzj_B0duraB0JzL9oe7iQjo3ePg|160#90#116#5#5#5000#M$M#rs$AOn4CLAJgTp1loLnFRWUS_fK0mz--mhf5g|320#180#116#3#3#5000#M$M#rs$AOn4CLCbeTxyUc-sc_XMJwexbyRRC1MPNw',
  '8QwV1fcYtfs': 'https://i.ytimg.com/sb/8QwV1fcYtfs/storyboard3_L$L/$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgikv5bRBg==|48#27#100#10#10#0#default#rs$AOn4CLASypTKoRxV-avLOJlDqwp3pu6xdw|80#45#93#10#10#5000#M$M#rs$AOn4CLA2kgCtYbywxWlZ2nHOS4ulnOgyvQ|160#90#93#5#5#5000#M$M#rs$AOn4CLDM09a_tdszEg0TSpv9K2oJYBspzg|320#180#93#3#3#5000#M$M#rs$AOn4CLClO4J9Q27ORPRTzgXrIu66X3S-fQ',
  'mdOEkhlmKsM': 'https://i.ytimg.com/sb/mdOEkhlmKsM/storyboard3_L$L/$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgi_pInRBg==|48#27#100#10#10#0#default#rs$AOn4CLAVJ0Tg4mh4PA8i4x2EblE0z92prw|80#45#119#10#10#5000#M$M#rs$AOn4CLBQvGRyp1Zw0QOUfxQFS6OhixRoBg|160#90#119#5#5#5000#M$M#rs$AOn4CLBZzI1ib8ZUN8xJqoZaXX1RWXwq4g|320#180#119#3#3#5000#M$M#rs$AOn4CLAUPxMPJmN4kPfb5HDpthHt1Eetnw',
  'Ea8ICLXTDaU': 'https://i.ytimg.com/sb/Ea8ICLXTDaU/storyboard3_L$L/$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgj1zcbTBg==|48#27#100#10#10#0#default#rs$AOn4CLANyXQdRel7BByDxPNCnJBA-OQwUg|80#45#149#10#10#10000#M$M#rs$AOn4CLDHvxHPgPuyGxiRcvOGLjkkZxJxbg|160#90#149#5#5#10000#M$M#rs$AOn4CLAYUq36P4W4VdbjMiqRQ9z6JSu_MQ|320#180#149#3#3#10000#M$M#rs$AOn4CLBZDR9goiFwb7nbg91qeOSF-FkeUA',
  'dnsK8HWD8us': 'https://i.ytimg.com/sb/dnsK8HWD8us/storyboard3_L$L/$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgiOrKbSBg==|48#27#100#10#10#0#default#rs$AOn4CLAb3a-yTRHdYLGq7ejTnEpAG2VQgA|80#45#134#10#10#10000#M$M#rs$AOn4CLC5ElrC7Krqkz4oBmgQbMUInIlu-Q|160#90#134#5#5#10000#M$M#rs$AOn4CLA8LdmGvxd9ZD_JiW7PJxR3iWQC6A|320#180#134#3#3#10000#M$M#rs$AOn4CLB168l4Yi8yoPj49qiynL4Nf8l4EQ',
  '7TIoqV_J8zg': 'https://i.ytimg.com/sb/7TIoqV_J8zg/storyboard3_L$L/$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgj8zKLRBg==|48#27#100#10#10#0#default#rs$AOn4CLCjJy40cMQvra56zzLeEmzmAncG9Q|80#45#140#10#10#10000#M$M#rs$AOn4CLAEVFfy9KKXKSr0-J_M9Pwxzmyp5w|160#90#140#5#5#10000#M$M#rs$AOn4CLCnryj-vkCOXATS9BredYwkCYmk_Q|320#180#140#3#3#10000#M$M#rs$AOn4CLDaGRYf4-IB0y6Lx-olm_prmNVn-w',
  'OVmxey333G0': 'https://i.ytimg.com/sb/OVmxey333G0/storyboard3_L$L/$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgiS-bPQBg==|48#27#100#10#10#0#default#rs$AOn4CLDE-lVUYTG3xl7zkDBvWv10XBN6DQ|79#45#138#10#10#10000#M$M#rs$AOn4CLDNz5-sRPVrty0zzR7xrwMCY_mTFg|159#90#138#5#5#10000#M$M#rs$AOn4CLAmHKwBcAfuLIbX6grKnB9WW32jrA|319#180#138#3#3#10000#M$M#rs$AOn4CLC5jkzO7JSD-L6HVG4Wq-g6KcQcQw'
};

// 影片（部分影片含多個 clip，供詳細頁展示）
const VIDEOS = {
  // 喵遊記
  Pe226YsYVWk: { id: 'Pe226YsYVWk', channel: 'meow', title: '廣西柳州24小時7家店，108元第一螺螄鴨腳煲 VS 210元深夜大排檔', duration: 1449 },
  r4YnYow7L_4: { id: 'r4YnYow7L-4', channel: 'meow', title: '廣西街頭最火爆7種米粉，3.5元卷筒粉 VS 130元海鮮粉', duration: 900 },
  AovUL5FMYnc: { id: 'AovUL5FMYnc', channel: 'meow', title: '廣西南寧最火爆大排檔，15元海鮮粉 VS 108元第一燒鵝', duration: 1424 },
  DUmtBh5jUjk: { id: 'DUmtBh5jUjk', channel: 'meow', title: '廣西南寧解暑美食之王，55一斤博白白切 VS 4元果醬燒烤', duration: 907 },
  // 十三要
  nNLvL1z_mjE: { id: 'nNLvL1z_mjE', channel: 'yao', title: '中國即將消失的縣城！發展卻越來越好！', duration: 743 },
  '1NJZKzeWbg8': { id: '1NJZKzeWbg8', channel: 'yao', title: '中國最誇張的斷崖式交界帶！一座城裝下盆地和高原！', duration: 709 },
  ee9JlJh5mVw: { id: 'ee9JlJh5mVw', channel: 'yao', title: '中國最擠的縣城，竟然是西遊記裡的「女兒國」？！', duration: 574 },
  '8QwV1fcYtfs': { id: '8QwV1fcYtfs', channel: 'yao', title: '中國最有壓迫感的南北分界線！4小時橫穿秦嶺鰲太線！', duration: 460 },
  mdOEkhlmKsM: { id: 'mdOEkhlmKsM', channel: 'yao', title: '最愛中文的歐洲國家！高考竟然考中文！', duration: 588 },
  // OK哥
  Ea8ICLXTDaU: { id: 'Ea8ICLXTDaU', channel: 'okge', title: '80後老登勇闖加勒比海無人島，夜裡的海底都是巨型犀牛蝦！', duration: 1470 },
  dnsK8HWD8us: { id: 'dnsK8HWD8us', channel: 'okge', title: '為了稀有的粉色螺珠，我在加勒比海與2米長大鯊魚搏鬥許久！', duration: 1330 },
  '7TIoqV_J8zg': { id: '7TIoqV_J8zg', channel: 'okge', title: '探索美國第4集：佛羅里達找到史前巨齒鯊的大牙齒！', duration: 1388 }
};

// 13 個 clip，全部為 reviewed（已整理），會進首頁／檢索。
// 目前沒有其他 status；未整理／代辦清單這條流程尚未設計，勿臆造。
const CLIPS = [
  // ── OK哥 Ea8ICLXTDaU：一支影片 3 個 clip（詳細頁主角）──
  { id: 'c01', videoId: 'Ea8ICLXTDaU', start: 132, end: 168, eventDate: '2026-08-03', status: 'reviewed',
    summary: '夜潛第一次見到巨型犀牛蝦，藏在礁石縫裡', note: '犀牛蝦好大',
    tags: [{ name: '加勒比海', kind: 'place' }, { name: '夜潛', kind: 'topic' }, { name: '龍蝦', kind: 'other' }] },
  { id: 'c02', videoId: 'Ea8ICLXTDaU', start: 612, end: 650, eventDate: '2026-08-03', status: 'reviewed',
    summary: '在無人島沙灘生火煮海鮮，畫面超療癒', note: '',
    tags: [{ name: '無人島', kind: 'place' }, { name: '露營', kind: 'topic' }] },
  { id: 'c03', videoId: 'Ea8ICLXTDaU', start: 1140, end: 1176, eventDate: '2026-08-03', status: 'reviewed',
    summary: '海底遇到一群魚群，能見度極佳', note: '這段風景最美',
    tags: [{ name: '潛水', kind: 'topic' }, { name: '魚群', kind: 'other' }] },

  // ── 其餘首頁 clip（reviewed，跨不同月份）──
  { id: 'c04', videoId: 'dnsK8HWD8us', start: 480, end: 520, eventDate: '2026-06-18', status: 'reviewed',
    summary: '與2米長大鯊魚近距離對峙，超緊張', note: '差點掉線',
    tags: [{ name: '加勒比海', kind: 'place' }, { name: '鯊魚', kind: 'other' }, { name: '粉色螺珠', kind: 'topic' }] },
  { id: 'c05', videoId: '7TIoqV_J8zg', start: 300, end: 342, eventDate: '2026-05-09', status: 'reviewed',
    summary: '在河床挖到史前巨齒鯊的大牙齒', note: '',
    tags: [{ name: '佛羅里達', kind: 'place' }, { name: '化石', kind: 'topic' }, { name: '巨齒鯊', kind: 'other' }] },

  { id: 'c06', videoId: 'Pe226YsYVWk', start: 210, end: 250, eventDate: '2026-07-21', status: 'reviewed',
    summary: '108元的第一螺螄鴨腳煲，湯頭濃郁', note: '鴨腳很入味',
    tags: [{ name: '柳州', kind: 'place' }, { name: '螺螄粉', kind: 'topic' }, { name: '宵夜', kind: 'topic' }] },
  { id: 'c07', videoId: 'r4YnYow7L_4', start: 156, end: 192, eventDate: '2026-04-14', status: 'reviewed',
    summary: '3.5元的卷筒粉，CP值爆表', note: '',
    tags: [{ name: '廣西', kind: 'place' }, { name: '米粉', kind: 'topic' }] },
  { id: 'c08', videoId: 'AovUL5FMYnc', start: 420, end: 456, eventDate: '2026-02-27', status: 'reviewed',
    summary: '南寧大排檔的15元海鮮粉，料多實在', note: '老闆超豪邁',
    tags: [{ name: '南寧', kind: 'place' }, { name: '大排檔', kind: 'topic' }, { name: '海鮮', kind: 'topic' }] },
  { id: 'c09', videoId: 'DUmtBh5jUjk', start: 260, end: 300, eventDate: '2025-12-30', status: 'reviewed',
    summary: '博白白切，55元一斤肉質鮮嫩', note: '',
    tags: [{ name: '博白', kind: 'place' }, { name: '白切', kind: 'topic' }] },

  { id: 'c10', videoId: 'nNLvL1z_mjE', start: 540, end: 580, eventDate: '2026-03-11', status: 'reviewed',
    summary: '即將消失的縣城，老街依然熱鬧', note: '很有時代感',
    tags: [{ name: '縣城', kind: 'place' }, { name: '老街', kind: 'topic' }] },
  { id: 'c11', videoId: '1NJZKzeWbg8', start: 420, end: 464, eventDate: '2026-01-08', status: 'reviewed',
    summary: '斷崖式交界帶，一邊盆地一邊高原', note: '地形超震撼',
    tags: [{ name: '高原', kind: 'place' }, { name: '地理', kind: 'topic' }] },
  { id: 'c12', videoId: 'ee9JlJh5mVw', start: 330, end: 366, eventDate: '2025-11-19', status: 'reviewed',
    summary: '西遊記「女兒國」原型縣城，人口密度驚人', note: '',
    tags: [{ name: '縣城', kind: 'place' }, { name: '西遊記', kind: 'topic' }] },
  { id: 'c13', videoId: '8QwV1fcYtfs', start: 300, end: 348, eventDate: '2025-09-05', status: 'reviewed',
    summary: '橫穿秦嶺鰲太線，雲海壯闊', note: '走了4小時',
    tags: [{ name: '秦嶺', kind: 'place' }, { name: '登山', kind: 'topic' }, { name: '雲海', kind: 'other' }] },

];

// ── 輔助函式 ──
function thumbUrl(videoId) {
  // 真實影片縮圖（16:9）；videoId 可能是 JS-safe key，需還原成真實 id
  const realId = (VIDEOS[videoId] && VIDEOS[videoId].id) || videoId;
  return `https://i.ytimg.com/vi/${realId}/mqdefault.jpg`;
}

function realId(videoId) {
  return (VIDEOS[videoId] && VIDEOS[videoId].id) || videoId;
}
// 指定時間點的畫面樣式字串（storyboard sprite 切格）；無 spec 或解析失敗時退回封面圖。
function thumbStyle(videoId, t) {
  const id = realId(videoId);
  const spec = SB_SPECS[id];
  const style = spec && window.SB ? window.SB.frameStyle(spec, t) : null;
  if (style) return style;
  return `background-image:url('https://i.ytimg.com/vi/${id}/mqdefault.jpg');background-size:cover;background-position:center`;
}
function fmtTime(sec) {
  const m = Math.floor(sec / 60), s = Math.floor(sec % 60);
  return `${m}:${String(s).padStart(2, '0')}`;
}
function fmtDateLabel(iso) {
  const [y, m, d] = iso.split('-');
  return `${y}年${parseInt(m)}月${parseInt(d)}日`;
}
function fmtMonthLabel(iso) {
  const [y, m] = iso.split('-');
  return `${y}年${parseInt(m)}月`;
}
function videoOf(clip) { return VIDEOS[clip.videoId]; }
function channelOf(clip) { return CHANNELS[VIDEOS[clip.videoId].channel]; }

// 所有出現過的標籤（去重）→ 標籤頁使用
function allTags() {
  const map = new Map();
  for (const c of CLIPS) for (const t of c.tags) {
    if (!map.has(t.name)) map.set(t.name, { name: t.name, kind: t.kind, count: 0 });
    map.get(t.name).count++;
  }
  return [...map.values()].sort((a, b) => b.count - a.count);
}

const KIND_ICON = { person: '👤', pet: '🐾', place: '📍', topic: '#', other: '◆' };

window.MOCK = { CHANNELS, VIDEOS, CLIPS, SB_SPECS, thumbUrl, thumbStyle, fmtTime, fmtDateLabel, fmtMonthLabel, videoOf, channelOf, allTags, KIND_ICON };

// ═══════════ Shot 版新增（2026-08-28）═══════════
// 地點改為獨立欄位（shot.place）；標籤僅保留 person/pet/topic/other。
// mockup 的 CLIPS 物件即 shot（start 視為 at_sec），為避免大改既有頁面而沿用命名。

const PLACES = {
  c01: '加勒比海', c02: '加勒比海無人島', c03: '加勒比海', c04: '加勒比海',
  c05: '佛羅里達', c06: '柳州', c07: '廣西', c08: '南寧', c09: '博白',
  c10: '雲南', c11: '青藏高原', c12: '女兒國縣城', c13: '秦嶺'
};
for (const c of CLIPS) c.place = PLACES[c.id] || '';

// 地點已是獨立欄位，tag.kind 不再有 'place'（規格第四節）；舊的地點標籤在此清掉
for (const c of CLIPS) c.tags = c.tags.filter(t => t.kind !== 'place');

/* 查詢用的 facet：地點與標籤混在同一排（規格第六節 /api/facets）
   facet='place' 走 shot.place 比對，facet='tag' 走 shot_tag */
function shotFacets(clip) {
  return [
    ...(clip.place ? [{ name: clip.place, kind: 'place', facet: 'place' }] : []),
    ...clip.tags.map(t => ({ ...t, facet: 'tag' }))
  ];
}
function facetKey(f) { return f.facet + ':' + f.name; }
// 一批 shot 的 facet 統計，依張數由多到少
function allFacets(list) {
  const map = new Map();
  for (const c of list) for (const f of shotFacets(c)) {
    const k = facetKey(f);
    if (!map.has(k)) map.set(k, { ...f, count: 0 });
    map.get(k).count++;
  }
  return [...map.values()].sort((a, b) => b.count - a.count);
}
function matchesFacet(clip, f) {
  return f.facet === 'place' ? clip.place === f.name : clip.tags.some(t => t.name === f.name);
}

// event_date 的來源（見 spec 第四節預設值鏈）：
// recordingDate 多數影片為空，所以「來自上傳日」才是常態，只有它需要 ⚠ 提示。
const DATE_SRC = { c01: 'recorded', c05: 'recorded', c11: 'recorded' };
for (const c of CLIPS) c.dateSrc = DATE_SRC[c.id] || 'published';

// 既有地點（去重，依使用次數）→ 編輯時給建議，等同 SELECT DISTINCT place
// 設定頁上方的三格統計。
// 「本月」以 eventDate 的年月比對，與首頁的月份分組同一套口徑。
function stats() {
  const ym = new Date().toISOString().slice(0, 7);
  return {
    shots: CLIPS.length,
    thisMonth: CLIPS.filter(c => c.eventDate.slice(0, 7) === ym).length,
    videos: new Set(CLIPS.map(c => c.videoId)).size
  };
}

function allPlaces() {
  const map = new Map();
  for (const c of CLIPS) {
    if (!c.place) continue;
    map.set(c.place, (map.get(c.place) || 0) + 1);
  }
  return [...map.entries()].map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count);
}

// 分類資料夾（樹狀，深度上限 5；mockup 造 2 層示意）
const FOLDERS = [
  { id: 'f1', name: '馬拉松', parent: null },
  { id: 'f2', name: '旅遊', parent: null },
  { id: 'f3', name: '加勒比海之旅', parent: null },
  { id: 'f4', name: '廣西美食', parent: null },
  { id: 'f5', name: '風景精選', parent: null }
];
// folderId -> Set(clipId)
const FOLDER_SHOTS = {
  f1: new Set(),
  f2: new Set(['c05']),
  f3: new Set(['c01', 'c02', 'c03', 'c04']),
  f4: new Set(['c06', 'c07', 'c08']),
  f5: new Set(['c03', 'c11', 'c13'])
};

function folderChildren(parentId) {
  return FOLDERS.filter(f => f.parent === parentId);
}
function folderById(id) { return FOLDERS.find(f => f.id === id); }
// 含子孫層的張數
function folderCount(id) {
  let n = (FOLDER_SHOTS[id] || new Set()).size;
  for (const ch of folderChildren(id)) n += folderCount(ch.id);
  return n;
}
function folderDepth(id) {
  let d = 1, f = folderById(id);
  while (f && f.parent) { d++; f = folderById(f.parent); }
  return d;
}
function shotFolders(clipId) {
  return FOLDERS.filter(f => (FOLDER_SHOTS[f.id] || new Set()).has(clipId));
}
function addFolder(name, parent) {
  const id = 'f' + (FOLDERS.length + 1) + '_' + Date.now().toString(36);
  FOLDERS.push({ id, name, parent: parent || null });
  FOLDER_SHOTS[id] = new Set();
  return id;
}
function removeClip(clipId) {
  const i = CLIPS.findIndex(c => c.id === clipId);
  if (i >= 0) CLIPS.splice(i, 1);
  for (const k in FOLDER_SHOTS) FOLDER_SHOTS[k].delete(clipId);
}

/* 原型的資料只活在記憶體裡，換一頁就重置。
   資料夾的改名與刪除是跨頁看得到的操作，所以額外記在 sessionStorage，
   載入時再套回來（關掉分頁就恢復成預設 mock 資料）。 */
const EDIT_KEY = 'ytspace2_folder_edits';
function folderEdits() {
  try { return JSON.parse(sessionStorage.getItem(EDIT_KEY)) || {}; } catch { return {}; }
}
function saveFolderEdits(e) { sessionStorage.setItem(EDIT_KEY, JSON.stringify(e)); }
function renameFolder(id, name) {
  const f = folderById(id);
  if (f) f.name = name;
  const e = folderEdits();
  e.renamed = { ...(e.renamed || {}), [id]: name };
  saveFolderEdits(e);
}
function deleteFolder(id) {
  const gone = [];
  (function del(fid) {
    for (const ch of folderChildren(fid)) del(ch.id);
    const i = FOLDERS.findIndex(x => x.id === fid);
    if (i >= 0) FOLDERS.splice(i, 1);
    delete FOLDER_SHOTS[fid];
    gone.push(fid);
  })(id);
  const e = folderEdits();
  e.deleted = [...new Set([...(e.deleted || []), ...gone])];
  saveFolderEdits(e);
}
function applyFolderEdits() {
  const e = folderEdits();
  for (const [id, name] of Object.entries(e.renamed || {})) {
    const f = folderById(id);
    if (f) f.name = name;
  }
  for (const id of e.deleted || []) {
    const i = FOLDERS.findIndex(x => x.id === id);
    if (i >= 0) FOLDERS.splice(i, 1);
    delete FOLDER_SHOTS[id];
  }
}
applyFolderEdits();

Object.assign(window.MOCK, {
  FOLDERS, FOLDER_SHOTS, folderChildren, folderById, folderCount,
  folderDepth, shotFolders, addFolder, removeClip, allPlaces, stats,
  shotFacets, facetKey, allFacets, matchesFacet,
  renameFolder, deleteFolder
});
