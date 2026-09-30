import * as server from '../entries/pages/settings/_page.server.ts.js';

export const index = 4;
let component_cache;
export const component = async () => component_cache ??= (await import('../entries/pages/settings/_page.svelte.js')).default;
export { server };
export const server_id = "src/routes/settings/+page.server.ts";
export const imports = ["_app/immutable/nodes/4.DM5q0c8I.js","_app/immutable/chunks/BpLxTbD7.js","_app/immutable/chunks/xihTtKlq.js"];
export const stylesheets = ["_app/immutable/assets/4.Dn4QZVdW.css"];
export const fonts = [];
