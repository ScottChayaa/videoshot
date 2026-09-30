import * as server from '../entries/pages/v/_videoId_/_page.server.ts.js';

export const index = 5;
let component_cache;
export const component = async () => component_cache ??= (await import('../entries/pages/v/_videoId_/_page.svelte.js')).default;
export { server };
export const server_id = "src/routes/v/[videoId]/+page.server.ts";
export const imports = ["_app/immutable/nodes/5.SxrZ2LgJ.js","_app/immutable/chunks/BpLxTbD7.js","_app/immutable/chunks/xihTtKlq.js","_app/immutable/chunks/BSctXP6A.js"];
export const stylesheets = ["_app/immutable/assets/time.BqYLRqTx.css","_app/immutable/assets/5.GrMTOBne.css"];
export const fonts = [];
