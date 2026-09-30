import { a as ensure_array_like, b as attr, i as derived, t as attr_class, x as escape_html } from "../../chunks/server.js";
import { t as page } from "../../chunks/state.js";
//#region src/lib/components/BottomNav.svelte
function BottomNav($$renderer, $$props) {
	$$renderer.component(($$renderer) => {
		const items = [
			{
				href: "/",
				label: "檢索",
				icon: "🔍",
				testid: "nav-search"
			},
			{
				href: "/inbox",
				label: "Inbox",
				icon: "📥",
				testid: "nav-inbox"
			},
			{
				href: "/settings",
				label: "設定",
				icon: "⚙",
				testid: "nav-settings"
			}
		];
		const current = derived(() => page.url.pathname);
		$$renderer.push(`<nav class="svelte-oeh3u8"><!--[-->`);
		const each_array = ensure_array_like(items);
		for (let $$index = 0, $$length = each_array.length; $$index < $$length; $$index++) {
			let item = each_array[$$index];
			$$renderer.push(`<a${attr("href", item.href)}${attr("data-testid", item.testid)}${attr("aria-current", current() === item.href ? "page" : void 0)}${attr_class("svelte-oeh3u8", void 0, { "active": current() === item.href })}><span class="icon svelte-oeh3u8">${escape_html(item.icon)}</span> <span class="label">${escape_html(item.label)}</span></a>`);
		}
		$$renderer.push(`<!--]--></nav>`);
	});
}
//#endregion
//#region src/routes/+layout.svelte
function _layout($$renderer, $$props) {
	let { children } = $$props;
	$$renderer.push(`<main class="app-main">`);
	children($$renderer);
	$$renderer.push(`<!----></main> `);
	BottomNav($$renderer, {});
	$$renderer.push(`<!---->`);
}
//#endregion
export { _layout as default };
