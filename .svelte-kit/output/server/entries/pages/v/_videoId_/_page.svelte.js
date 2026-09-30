import { t as public_env } from "../../../../chunks/shared-server.js";
import { a as ensure_array_like, b as attr, i as derived, n as attr_style, r as bind_props, s as stringify, t as attr_class, x as escape_html } from "../../../../chunks/server.js";
import { n as Thumb, t as secToMMSS } from "../../../../chunks/time.js";
//#region src/lib/components/ClipRow.svelte
function ClipRow($$renderer, $$props) {
	$$renderer.component(($$renderer) => {
		let { clip, video, selected, onselect } = $$props;
		const STATUS_LABEL = {
			inbox: "待分析",
			analyzing: "分析中",
			analyzed: "待校對",
			reviewed: "已完成",
			failed: "分析失敗"
		};
		$$renderer.push(`<button${attr_class("row svelte-1bg0you", void 0, { "selected": selected })} data-testid="clip-row"${attr("data-clip-id", clip.id)}>`);
		Thumb($$renderer, {
			video,
			t: clip.startSec,
			width: 96
		});
		$$renderer.push(`<!----> <div class="meta svelte-1bg0you"><div class="range svelte-1bg0you">${escape_html(secToMMSS(clip.startSec))} – ${escape_html(secToMMSS(clip.endSec))}</div> <div class="title svelte-1bg0you">${escape_html(clip.summary || clip.note || "(未命名)")}</div> <div class="status svelte-1bg0you"${attr("data-status", clip.status)}>${escape_html(STATUS_LABEL[clip.status])}</div></div></button>`);
	});
}
//#endregion
//#region src/lib/components/TagChip.svelte
function TagChip($$renderer, $$props) {
	$$renderer.component(($$renderer) => {
		let { clipTag, onconfirm, onremove } = $$props;
		$$renderer.push(`<span class="chip svelte-1u925xq" data-testid="tag-chip"${attr("data-source", clipTag.source)}><button class="body svelte-1u925xq"${attr("title", clipTag.source === "ai" ? "點一下確認這個標籤" : "已確認")}>${escape_html({
			person: "👤",
			pet: "🐾",
			place: "📍",
			topic: "⌗",
			other: "⌗"
		}[clipTag.tag.kind])}
		${escape_html(clipTag.tag.name)}</button> <button class="x svelte-1u925xq" aria-label="移除標籤">✕</button></span>`);
	});
}
//#endregion
//#region src/lib/components/ClipSheet.svelte
function ClipSheet($$renderer, $$props) {
	$$renderer.component(($$renderer) => {
		let { clip, video, tags, currentTime, onupdate, onclose } = $$props;
		const dateMismatch = derived(() => clip.eventDate !== video.publishedAt);
		const unusedTags = derived(() => tags.filter((t) => !clip.tags.some((ct) => ct.tag.id === t.id)));
		function currentTagIds() {
			return clip.tags.map((ct) => ct.tag.id);
		}
		$$renderer.push(`<div class="sheet svelte-1ho2zup" data-testid="clip-sheet"><button class="handle svelte-1ho2zup" data-testid="sheet-close" aria-label="關閉面板"></button> <div class="range svelte-1ho2zup" data-testid="sheet-range">${escape_html(secToMMSS(clip.startSec))} – ${escape_html(secToMMSS(clip.endSec))}</div> <div class="row svelte-1ho2zup"><span>起</span> <button>設為目前</button> <button data-testid="start-minus">−5s</button> <button data-testid="start-plus">+5s</button></div> <div class="row svelte-1ho2zup"><span>迄</span> <button>設為目前</button> <button data-testid="end-minus">−5s</button> <button data-testid="end-plus">+5s</button></div> <label class="svelte-1ho2zup">備註 <input data-testid="field-note"${attr("value", clip.note)}/></label> <label class="svelte-1ho2zup">摘要 <textarea data-testid="field-summary" rows="2">`);
		const $$body = escape_html(clip.summary);
		if ($$body) $$renderer.push(`${$$body}`);
		$$renderer.push(`</textarea></label> <div class="tags svelte-1ho2zup"><!--[-->`);
		const each_array = ensure_array_like(clip.tags);
		for (let $$index = 0, $$length = each_array.length; $$index < $$length; $$index++) {
			let ct = each_array[$$index];
			TagChip($$renderer, {
				clipTag: ct,
				onconfirm: (id) => onupdate({ tagIds: [.../* @__PURE__ */ new Set([...currentTagIds(), id])] }),
				onremove: (id) => onupdate({ tagIds: currentTagIds().filter((x) => x !== id) })
			});
		}
		$$renderer.push(`<!--]--> `);
		if (unusedTags().length > 0) {
			$$renderer.push("<!--[0-->");
			$$renderer.select({
				"data-testid": "add-tag",
				value: "",
				onchange: (e) => {
					const id = e.currentTarget.value;
					if (id) onupdate({ tagIds: [...currentTagIds(), id] });
					e.currentTarget.value = "";
				}
			}, ($$renderer) => {
				$$renderer.option({ value: "" }, ($$renderer) => {
					$$renderer.push(`＋ 加標籤`);
				});
				$$renderer.push(`<!--[-->`);
				const each_array_1 = ensure_array_like(unusedTags());
				for (let $$index_1 = 0, $$length = each_array_1.length; $$index_1 < $$length; $$index_1++) {
					let t = each_array_1[$$index_1];
					$$renderer.option({ value: t.id }, ($$renderer) => {
						$$renderer.push(`${escape_html(t.name)}`);
					});
				}
				$$renderer.push(`<!--]-->`);
			});
		} else $$renderer.push("<!--[-1-->");
		$$renderer.push(`<!--]--></div> <label class="date svelte-1ho2zup">日期 <input type="date" data-testid="field-date"${attr("value", clip.eventDate)}/> `);
		if (dateMismatch()) {
			$$renderer.push("<!--[0-->");
			$$renderer.push(`<span class="warn svelte-1ho2zup" data-testid="date-mismatch">⚠ 與上傳日 ${escape_html(video.publishedAt)} 不同</span>`);
		} else $$renderer.push("<!--[-1-->");
		$$renderer.push(`<!--]--></label> <button class="toggle svelte-1ho2zup">${escape_html("▸")} 語音逐字 / 畫面描述</button> `);
		$$renderer.push("<!--[-1-->");
		$$renderer.push(`<!--]--> <button class="confirm svelte-1ho2zup" data-testid="confirm-clip">✓ 確認完成</button></div>`);
	});
}
//#endregion
//#region src/lib/components/Player.svelte
function Player($$renderer, $$props) {
	$$renderer.component(($$renderer) => {
		let { videoId, currentTime = 0, onready } = $$props;
		const isFake = public_env.PUBLIC_PLAYER_MODE === "fake";
		$$renderer.push(`<div class="player svelte-nfsfpn">`);
		if (isFake) {
			$$renderer.push("<!--[0-->");
			$$renderer.push(`<div class="fake svelte-nfsfpn" data-testid="fake-player"><div class="fake-id">${escape_html(videoId)}</div> <label>目前時間（秒） <input type="number" data-testid="fake-time"${attr("value", currentTime)} class="svelte-nfsfpn"/></label> <div data-testid="fake-clock">${escape_html(secToMMSS(currentTime))}</div></div>`);
		} else {
			$$renderer.push("<!--[-1-->");
			$$renderer.push(`<iframe data-testid="yt-iframe"${attr("src", `https://www.youtube.com/embed/${stringify(videoId)}?enablejsapi=1&playsinline=1`)} title="YouTube 播放器" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture" allowfullscreen="" class="svelte-nfsfpn"></iframe>`);
		}
		$$renderer.push(`<!--]--></div>`);
		bind_props($$props, { currentTime });
	});
}
//#endregion
//#region src/lib/components/Timeline.svelte
function Timeline($$renderer, $$props) {
	let { duration, clips, currentTime, selectedId } = $$props;
	const pct = (sec) => duration > 0 ? sec / duration * 100 : 0;
	$$renderer.push(`<div class="timeline svelte-112n2zp" data-testid="timeline"><!--[-->`);
	const each_array = ensure_array_like(clips);
	for (let $$index = 0, $$length = each_array.length; $$index < $$length; $$index++) {
		let clip = each_array[$$index];
		$$renderer.push(`<div${attr_class("band svelte-112n2zp", void 0, { "selected": clip.id === selectedId })}${attr_style("", {
			left: `${stringify(pct(clip.startSec))}%`,
			width: `${stringify(Math.max(1, pct(clip.endSec - clip.startSec)))}%`
		})}></div>`);
	}
	$$renderer.push(`<!--]--> <div class="playhead svelte-112n2zp"${attr_style("", { left: `${stringify(pct(currentTime))}%` })}></div></div>`);
}
//#endregion
//#region src/routes/v/[videoId]/+page.svelte
function _page($$renderer, $$props) {
	$$renderer.component(($$renderer) => {
		let { data } = $$props;
		let clips = data.clips;
		let selectedId = null;
		let currentTime = 0;
		let api = null;
		const selected = derived(() => clips.find((c) => c.id === selectedId) ?? null);
		function select(id) {
			selectedId = id;
			const clip = clips.find((c) => c.id === id);
			if (clip) api?.seekTo(clip.startSec);
		}
		async function updateSelected(patch) {
			if (!selectedId) return;
			const res = await fetch(`/api/clips/${selectedId}`, {
				method: "PATCH",
				headers: { "content-type": "application/json" },
				body: JSON.stringify(patch)
			});
			if (!res.ok) return;
			const updated = await res.json();
			clips = clips.map((c) => c.id === updated.id ? updated : c).sort((a, b) => a.startSec - b.startSec);
			if (patch.status === "reviewed") selectedId = null;
		}
		let $$settled = true;
		let $$inner_renderer;
		function $$render_inner($$renderer) {
			$$renderer.push(`<section data-testid="page-studio">`);
			Player($$renderer, {
				videoId: data.video.id,
				onready: (a) => api = a,
				get currentTime() {
					return currentTime;
				},
				set currentTime($$value) {
					currentTime = $$value;
					$$settled = false;
				}
			});
			$$renderer.push(`<!----> `);
			Timeline($$renderer, {
				duration: data.video.durationSec,
				clips,
				currentTime,
				selectedId
			});
			$$renderer.push(`<!----> <header class="svelte-1fxeite"><h1 data-testid="video-title" class="svelte-1fxeite">${escape_html(data.video.title)}</h1> <p data-testid="video-channel" class="svelte-1fxeite">${escape_html(data.video.channelTitle)} ・ ${escape_html(data.video.publishedAt)}</p></header> <div class="list svelte-1fxeite"><h2 class="svelte-1fxeite">Clips (${escape_html(clips.length)})</h2> <!--[-->`);
			const each_array = ensure_array_like(clips);
			for (let $$index = 0, $$length = each_array.length; $$index < $$length; $$index++) {
				let clip = each_array[$$index];
				ClipRow($$renderer, {
					clip,
					video: data.video,
					selected: clip.id === selectedId,
					onselect: select
				});
			}
			$$renderer.push(`<!--]--></div> `);
			if (selected()) {
				$$renderer.push("<!--[0-->");
				ClipSheet($$renderer, {
					clip: selected(),
					video: data.video,
					tags: data.tags,
					currentTime,
					onupdate: updateSelected,
					onclose: () => selectedId = null
				});
			} else $$renderer.push("<!--[-1-->");
			$$renderer.push(`<!--]--> <button class="fab svelte-1fxeite" data-testid="mark-now">⬤ 標記此刻</button></section>`);
		}
		do {
			$$settled = true;
			$$inner_renderer = $$renderer.copy();
			$$render_inner($$inner_renderer);
		} while (!$$settled);
		$$renderer.subsume($$inner_renderer);
	});
}
//#endregion
export { _page as default };
