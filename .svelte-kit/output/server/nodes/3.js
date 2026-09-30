import * as server from '../entries/pages/inbox/_page.server.ts.js';

export const index = 3;
let component_cache;
export const component = async () => component_cache ??= (await import('../entries/pages/inbox/_page.svelte.js')).default;
export { server };
export const server_id = "src/routes/inbox/+page.server.ts";
export const imports = ["_app/immutable/nodes/3.B3ZA_xwk.js","_app/immutable/chunks/BpLxTbD7.js","_app/immutable/chunks/xihTtKlq.js","_app/immutable/chunks/BSctXP6A.js"];
export const stylesheets = ["_app/immutable/assets/time.BqYLRqTx.css","_app/immutable/assets/3.B7Mf0Nkp.css"];
export const fonts = [];
