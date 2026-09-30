import { r as DEV_OWNER_ID, t as getRepo } from "../../../../chunks/repo.js";
import { error, json } from "@sveltejs/kit";
//#region src/routes/api/settings/+server.ts
var PATCH = async ({ request }) => {
	const body = await request.json();
	for (const key of ["markBeforeSec", "markAfterSec"]) {
		const v = body[key];
		if (v !== void 0 && (typeof v !== "number" || v < 0 || v > 600)) error(400, `${key} 必須是 0 到 600 之間的數字`);
	}
	const { ownerId: _ignored, ...patch } = body;
	return json(await getRepo().updateSettings(DEV_OWNER_ID, patch));
};
//#endregion
export { PATCH };
