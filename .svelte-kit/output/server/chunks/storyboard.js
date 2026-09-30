//#region src/lib/storyboard.ts
function parseStoryboardSpec(spec) {
	if (!spec) return null;
	const parts = spec.split("|");
	if (parts.length < 2) return null;
	const [urlPart, ...levelParts] = parts;
	if (!urlPart.startsWith("http")) return null;
	const sqpMatch = urlPart.match(/[?&]sqp=([^&]*)/);
	const baseUrl = urlPart.split("?")[0];
	const levels = [];
	for (let i = 0; i < levelParts.length; i++) {
		const f = levelParts[i].split("#");
		if (f.length < 8) continue;
		const [width, height, frameCount, cols, rows, intervalMs] = f.slice(0, 6).map(Number);
		if ([
			width,
			height,
			frameCount,
			cols,
			rows,
			intervalMs
		].some(Number.isNaN)) continue;
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
	return {
		baseUrl,
		sqp: sqpMatch ? sqpMatch[1] : "",
		levels
	};
}
function pickLevel(spec, preferred = 3) {
	const usable = spec.levels.filter((l) => l.intervalMs > 0 && l.cols > 0 && l.rows > 0 && l.frameCount > 0);
	if (usable.length === 0) return null;
	return usable.find((l) => l.level === preferred) ?? usable[usable.length - 1];
}
function frameAt(level, t) {
	const perSheet = level.cols * level.rows;
	const raw = Math.floor(Math.max(0, t) / (level.intervalMs / 1e3));
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
function sheetUrl(spec, level, sheetIndex) {
	return `${spec.baseUrl.replace("$L", String(level.level)).replace("$N", `M${sheetIndex}`)}?sqp=${spec.sqp}&sigh=${level.sigh}`;
}
//#endregion
export { sheetUrl as i, parseStoryboardSpec as n, pickLevel as r, frameAt as t };
