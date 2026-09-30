import type { FramePos, StoryboardLevel, StoryboardSpec } from './types';

export function parseStoryboardSpec(spec: string): StoryboardSpec | null {
	if (!spec) return null;
	const parts = spec.split('|');
	if (parts.length < 2) return null;

	const [urlPart, ...levelParts] = parts;
	if (!urlPart.startsWith('http')) return null;

	const sqpMatch = urlPart.match(/[?&]sqp=([^&]*)/);
	const baseUrl = urlPart.split('?')[0];

	const levels: StoryboardLevel[] = [];
	for (let i = 0; i < levelParts.length; i++) {
		const f = levelParts[i].split('#');
		if (f.length < 8) continue;
		const [width, height, frameCount, cols, rows, intervalMs] = f.slice(0, 6).map(Number);
		if ([width, height, frameCount, cols, rows, intervalMs].some(Number.isNaN)) continue;
		levels.push({
			level: i,
			width,
			height,
			frameCount,
			cols,
			rows,
			intervalMs,
			sigh: f[7]
		});
	}

	if (levels.length === 0) return null;
	return { baseUrl, sqp: sqpMatch ? sqpMatch[1] : '', levels };
}

export function pickLevel(spec: StoryboardSpec, preferred = 3): StoryboardLevel | null {
	const usable = spec.levels.filter((l) => l.intervalMs > 0 && l.cols > 0 && l.rows > 0 && l.frameCount > 0);
	if (usable.length === 0) return null;
	return usable.find((l) => l.level === preferred) ?? usable[usable.length - 1];
}

// 第 k 格代表的畫面是影片的 t = k × interval，因此要取「最近的一格」而非「之前的一格」。
// 這是對照 YouTube 播放器自己的 hover 預覽驗證出來的：以間隔 5s 的影片實測，
// 播放器在 t=102.5（100 與 105 的中點）才從第 20 格換到第 21 格 —— 是四捨五入，不是無條件捨去。
// 用 floor 誤差落在 −interval~0（10 秒間隔的長片最差差 10 秒），用 round 則是 ±interval/2，
// 且與使用者在 YouTube 上 hover 看到的畫面一致。
export function frameAt(level: StoryboardLevel, t: number): FramePos {
	const perSheet = level.cols * level.rows;
	const raw = Math.round(Math.max(0, t) / (level.intervalMs / 1000));
	const frameIndex = Math.max(0, Math.min(raw, level.frameCount - 1));
	const sheetIndex = Math.floor(frameIndex / perSheet);
	const posInSheet = frameIndex % perSheet;
	const col = posInSheet % level.cols;
	const row = Math.floor(posInSheet / level.cols);

	return {
		sheetIndex,
		col,
		row,
		offsetX: col === 0 ? 0 : -col * level.width,
		offsetY: row === 0 ? 0 : -row * level.height,
		width: level.width,
		height: level.height,
		sheetWidth: level.cols * level.width,
		sheetHeight: level.rows * level.height
	};
}

export function sheetUrl(
	spec: StoryboardSpec,
	level: StoryboardLevel,
	sheetIndex: number
): string {
	const path = spec.baseUrl.replace('$L', String(level.level)).replace('$N', `M${sheetIndex}`);
	return `${path}?sqp=${spec.sqp}&sigh=${level.sigh}`;
}
