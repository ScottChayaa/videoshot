import { r as DEV_OWNER_ID, t as getRepo } from "../../chunks/repo.js";
//#region src/routes/+page.server.ts
var load = async ({ url }) => {
	const repo = getRepo();
	const tagIds = url.searchParams.get("tagIds");
	const result = await repo.searchClips(DEV_OWNER_ID, {
		text: url.searchParams.get("text") ?? void 0,
		dateFrom: url.searchParams.get("dateFrom") ?? void 0,
		dateTo: url.searchParams.get("dateTo") ?? void 0,
		tagIds: tagIds ? tagIds.split(",").filter(Boolean) : void 0
	});
	const videos = {};
	for (const clip of result.clips) if (!videos[clip.videoId]) {
		const v = await repo.getVideo(DEV_OWNER_ID, clip.videoId);
		if (v) videos[clip.videoId] = v;
	}
	return {
		result,
		videos,
		query: {
			text: url.searchParams.get("text") ?? "",
			dateFrom: url.searchParams.get("dateFrom") ?? "",
			dateTo: url.searchParams.get("dateTo") ?? ""
		}
	};
};
//#endregion
export { load };
