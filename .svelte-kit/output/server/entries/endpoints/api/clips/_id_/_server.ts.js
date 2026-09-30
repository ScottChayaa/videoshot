import { r as DEV_OWNER_ID, t as getRepo } from "../../../../../chunks/repo.js";
import { error, json } from "@sveltejs/kit";
//#region src/routes/api/clips/[id]/+server.ts
var PATCH = async ({ params, request }) => {
	let patch;
	try {
		patch = await request.json();
	} catch {
		error(400, "請求內容不是有效的 JSON");
	}
	if (patch.startSec !== void 0 && patch.endSec !== void 0 && patch.endSec <= patch.startSec) error(400, "endSec 必須大於 startSec");
	if (!await getRepo().getClip("dev@local", params.id)) error(404, "找不到這個 clip");
	return json(await getRepo().updateClip(DEV_OWNER_ID, params.id, patch));
};
var DELETE = async ({ params }) => {
	await getRepo().deleteClip(DEV_OWNER_ID, params.id);
	return new Response(null, { status: 204 });
};
//#endregion
export { DELETE, PATCH };
