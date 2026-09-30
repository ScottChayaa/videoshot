import { a as ensure_array_like, b as attr, i as derived, s as stringify, x as escape_html } from "../../chunks/server.js";
import { n as Thumb, t as secToMMSS } from "../../chunks/time.js";
//#region src/lib/components/ResultCard.svelte
function ResultCard($$renderer, $$props) {
	$$renderer.component(($$renderer) => {
		let { clip, video } = $$props;
		derived(() => `https://www.youtube.com/embed/${video.id}?start=${clip.startSec}&end=${clip.endSec}&autoplay=1&mute=1&playsinline=1`);
		$$renderer.push(`<article class="card svelte-1r7q0ep" data-testid="result-card">`);
		$$renderer.push("<!--[-1-->");
		$$renderer.push(`<button class="cover svelte-1r7q0ep" data-testid="play-clip">`);
		Thumb($$renderer, {
			video,
			t: clip.startSec,
			width: 360
		});
		$$renderer.push(`<!----> <span class="play svelte-1r7q0ep">▶</span></button>`);
		$$renderer.push(`<!--]--> <div class="meta svelte-1r7q0ep"><div class="range svelte-1r7q0ep">${escape_html(secToMMSS(clip.startSec))} – ${escape_html(secToMMSS(clip.endSec))}</div> <div class="summary svelte-1r7q0ep">${escape_html(clip.summary || clip.note || "(未命名)")}</div> <div class="sub svelte-1r7q0ep">${escape_html(clip.eventDate)} ・ @${escape_html(video.channelTitle)}</div> <a class="external svelte-1r7q0ep"${attr("href", `https://youtu.be/${stringify(video.id)}?t=${stringify(clip.startSec)}`)} target="_blank" rel="noreferrer">在 YouTube 開啟 ↗</a></div></article>`);
	});
}
//#endregion
//#region src/routes/+page.svelte
function _page($$renderer, $$props) {
	$$renderer.component(($$renderer) => {
		let { data } = $$props;
		let text = data.query.text;
		let dateFrom = data.query.dateFrom;
		let dateTo = data.query.dateTo;
		const parsedSummary = derived(() => {
			const p = data.result.parsed;
			const bits = [];
			if (p.dateFrom || p.dateTo) bits.push(`${p.dateFrom ?? "不限"} ~ ${p.dateTo ?? "不限"}`);
			if (p.tagNames.length) bits.push(p.tagNames.join("、"));
			if (p.keywords.length) bits.push(p.keywords.join("、"));
			return bits.length ? bits.join(" ・ ") : "沒有條件，列出全部";
		});
		$$renderer.push(`<section data-testid="page-search" class="svelte-1uha8ag"><h1>檢索</h1> <form method="GET" data-testid="search-form" class="svelte-1uha8ag"><input name="text" data-testid="search-input" placeholder="用一句話描述你要找的片段"${attr("value", text)}/> <div class="dates svelte-1uha8ag"><input type="date" name="dateFrom"${attr("value", dateFrom)} aria-label="起始日期"/> <input type="date" name="dateTo"${attr("value", dateTo)} aria-label="結束日期"/> <button type="submit" data-testid="search-submit">搜尋</button></div></form> <p class="parsed svelte-1uha8ag" data-testid="parsed-summary">聽懂了：${escape_html(parsedSummary())}</p> `);
		if (data.result.clips.length === 0) {
			$$renderer.push("<!--[0-->");
			$$renderer.push(`<p class="empty svelte-1uha8ag" data-testid="search-empty">找不到符合的片段。</p>`);
		} else $$renderer.push("<!--[-1-->");
		$$renderer.push(`<!--]--> <!--[-->`);
		const each_array = ensure_array_like(data.result.clips);
		for (let $$index = 0, $$length = each_array.length; $$index < $$length; $$index++) {
			let clip = each_array[$$index];
			if (data.videos[clip.videoId]) {
				$$renderer.push("<!--[0-->");
				ResultCard($$renderer, {
					clip,
					video: data.videos[clip.videoId]
				});
			} else $$renderer.push("<!--[-1-->");
			$$renderer.push(`<!--]-->`);
		}
		$$renderer.push(`<!--]--></section>`);
	});
}
//#endregion
export { _page as default };
