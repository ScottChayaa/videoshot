import { a as ensure_array_like, b as attr, t as attr_class, x as escape_html } from "../../../chunks/server.js";
//#region src/routes/settings/+page.svelte
function _page($$renderer, $$props) {
	$$renderer.component(($$renderer) => {
		let { data } = $$props;
		let markBefore = data.settings.markBeforeSec;
		let markAfter = data.settings.markAfterSec;
		let pauseOnMark = data.settings.pauseOnMark;
		$$renderer.push(`<section data-testid="page-settings" class="svelte-1i19ct2"><h1 class="svelte-1i19ct2">設定</h1> <h2 class="svelte-1i19ct2">標記行為</h2> <label class="svelte-1i19ct2">標記時往前抓幾秒 <input type="number" data-testid="mark-before" min="0" max="600"${attr("value", markBefore)}/></label> <label class="svelte-1i19ct2">標記時往後抓幾秒 <input type="number" data-testid="mark-after" min="0" max="600"${attr("value", markAfter)}/></label> <label class="inline svelte-1i19ct2"><input type="checkbox" data-testid="pause-on-mark"${attr("checked", pauseOnMark, true)} class="svelte-1i19ct2"/> 標記時自動暫停影片</label> <button data-testid="save-settings">儲存</button> `);
		$$renderer.push("<!--[-1-->");
		$$renderer.push(`<!--]--> <h2 class="svelte-1i19ct2">縮圖服務</h2> <p data-testid="sb-health"${attr_class("svelte-1i19ct2", void 0, { "bad": !data.storyboardHealthy })}>storyboard 解析器：${escape_html(data.storyboardHealthy ? "正常" : "異常（已退回封面模式）")}</p> <h2 class="svelte-1i19ct2">標籤（${escape_html(data.tags.length)}）</h2> <ul class="svelte-1i19ct2"><!--[-->`);
		const each_array = ensure_array_like(data.tags);
		for (let $$index = 0, $$length = each_array.length; $$index < $$length; $$index++) {
			let tag = each_array[$$index];
			$$renderer.push(`<li data-testid="tag-item" class="svelte-1i19ct2"><strong>${escape_html(tag.name)}</strong> <span class="kind svelte-1i19ct2">${escape_html(tag.kind)}</span> `);
			if (tag.aliases.length) {
				$$renderer.push("<!--[0-->");
				$$renderer.push(`<span class="aliases svelte-1i19ct2">別名：${escape_html(tag.aliases.join("、"))}</span>`);
			} else $$renderer.push("<!--[-1-->");
			$$renderer.push(`<!--]--></li>`);
		}
		$$renderer.push(`<!--]--></ul></section>`);
	});
}
//#endregion
export { _page as default };
