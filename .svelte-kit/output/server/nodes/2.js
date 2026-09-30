import * as server from '../entries/pages/_page.server.ts.js';

export const index = 2;
let component_cache;
export const component = async () => component_cache ??= (await import('../entries/pages/_page.svelte.js')).default;
export { server };
export const server_id = "src/routes/+page.server.ts";
export const imports = ["_app/immutable/nodes/2.DS2m2lEd.js","_app/immutable/chunks/BpLxTbD7.js","_app/immutable/chunks/xihTtKlq.js","_app/immutable/chunks/BSctXP6A.js"];
export const stylesheets = ["_app/immutable/assets/time.BqYLRqTx.css","_app/immutable/assets/2.GlL487Mn.css"];
export const fonts = [];
