import { t as public_env } from "../../../../chunks/shared-server.js";
import { n as resetRepo } from "../../../../chunks/repo.js";
import { error, json } from "@sveltejs/kit";
//#region src/routes/api/__reset/+server.ts
var POST = async () => {
	if (public_env.PUBLIC_PLAYER_MODE !== "fake") error(403, "重置端點僅在測試模式可用");
	resetRepo();
	return json({ ok: true });
};
//#endregion
export { POST };
