import { r as DEV_OWNER_ID, t as getRepo } from "../../../../chunks/repo.js";
import { json } from "@sveltejs/kit";
//#region src/routes/api/search/+server.ts
var GET = async ({ url }) => {
	const tagIds = url.searchParams.get("tagIds");
	const result = await getRepo().searchClips(DEV_OWNER_ID, {
		text: url.searchParams.get("text") ?? void 0,
		dateFrom: url.searchParams.get("dateFrom") ?? void 0,
		dateTo: url.searchParams.get("dateTo") ?? void 0,
		tagIds: tagIds ? tagIds.split(",").filter(Boolean) : void 0
	});
	return json(result);
};
//#endregion
export { GET };
