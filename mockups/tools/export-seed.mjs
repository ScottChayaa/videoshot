// 把原型的假資料（mockups/uiux-v2/mock-data.js）轉成 app 開發測試版的匯入檔。
// 用法：pnpm seed   → 寫到 android/app/src/debug/assets/seed/mock-seed.json
// 產生物要 commit：Gradle 建置不依賴 Node。
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const ROOT = fileURLToPath(new URL('../..', import.meta.url));
const SRC = join(ROOT, 'mockups/uiux-v2/mock-data.js');
const OUT = join(ROOT, 'android/app/src/debug/assets/seed/mock-seed.json');

const store = new Map();
const sandbox = {
  window: {},
  localStorage: { getItem: k => store.get(k) ?? null, setItem: (k, v) => store.set(k, String(v)), removeItem: k => store.delete(k) },
  console,
};
vm.createContext(sandbox);
vm.runInContext(await readFile(SRC, 'utf8'), sandbox, { filename: 'mock-data.js' });
const M = sandbox.window.MOCK;

const realId = key => (M.VIDEOS[key] && M.VIDEOS[key].id) || key;
const usedKeys = [...new Set(M.CLIPS.map(c => c.videoId))];

const videos = usedKeys.map(key => {
  const v = M.VIDEOS[key];
  return { id: v.id, title: v.title, channelTitle: M.CHANNELS[v.channel], durationSec: v.duration, sbSpec: M.SB_SPECS[v.id] ?? null };
});
const shots = M.CLIPS.map(c => ({
  key: c.id,
  videoId: realId(c.videoId),
  atSec: c.start,
  eventDate: c.eventDate,
  place: c.place || null,
  description: c.note ? `${c.summary}\n${c.note}` : c.summary,
  tags: c.tags.map(t => ({ name: t.name, kind: t.kind })),
}));
const folders = M.FOLDERS.filter(f => !f.parent).map(f => ({
  key: f.id, name: f.name, shotKeys: [...(M.FOLDER_SHOTS[f.id] || [])].sort(),
}));

await mkdir(dirname(OUT), { recursive: true });
await writeFile(OUT, JSON.stringify({ videos, shots, folders }, null, 2) + '\n');
console.log(`寫入 ${OUT}：${videos.length} 支影片、${shots.length} 張、${folders.length} 個資料夾`);
