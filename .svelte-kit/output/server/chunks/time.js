import { b as attr, i as derived, n as attr_style, s as stringify } from "./server.js";
import { i as sheetUrl, r as pickLevel, t as frameAt } from "./storyboard.js";
//#region src/lib/components/Thumb.svelte
function Thumb($$renderer, $$props) {
	$$renderer.component(($$renderer) => {
		let { video, t, width = 120 } = $$props;
		const level = derived(() => video.sbSpec ? pickLevel(video.sbSpec) : null);
		const pos = derived(() => level() ? frameAt(level(), t) : null);
		const scale = derived(() => pos() ? width / pos().width : 1);
		const url = derived(() => video.sbSpec && level() && pos() ? sheetUrl(video.sbSpec, level(), pos().sheetIndex) : null);
		const fallback = derived(() => `https://img.youtube.com/vi/${video.id}/mqdefault.jpg`);
		if (url() && pos()) {
			$$renderer.push("<!--[0-->");
			$$renderer.push(`<div class="thumb sb svelte-1kff5n0" data-testid="thumb-storyboard" role="img" aria-label="片段畫面"${attr_style("", {
				width: `${stringify(width)}px`,
				height: `${stringify(pos().height * scale())}px`,
				"background-image": `url(${stringify(url())})`,
				"background-size": `${stringify(pos().sheetWidth * scale())}px ${stringify(pos().sheetHeight * scale())}px`,
				"background-position": `${stringify(pos().offsetX * scale())}px ${stringify(pos().offsetY * scale())}px`
			})}></div>`);
		} else {
			$$renderer.push("<!--[-1-->");
			$$renderer.push(`<img class="thumb svelte-1kff5n0" data-testid="thumb-cover"${attr("src", fallback())} alt="影片封面" loading="lazy"${attr_style("", { width: `${stringify(width)}px` })}/>`);
		}
		$$renderer.push(`<!--]-->`);
	});
}
//#endregion
//#region src/lib/time.ts
function secToMMSS(sec) {
	const total = Math.max(0, Math.floor(sec));
	const h = Math.floor(total / 3600);
	const m = Math.floor(total % 3600 / 60);
	const s = total % 60;
	const pad = (n) => String(n).padStart(2, "0");
	return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}
//#endregion
export { Thumb as n, secToMMSS as t };
