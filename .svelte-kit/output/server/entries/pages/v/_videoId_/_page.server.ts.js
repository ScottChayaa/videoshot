import { r as DEV_OWNER_ID, t as getRepo } from "../../../../chunks/repo.js";
import { error } from "@sveltejs/kit";
//#region src/routes/v/[videoId]/+page.server.ts
var load = async ({ params }) => {
	const repo = getRepo();
	const video = await repo.getVideo(DEV_OWNER_ID, params.videoId);
	if (!video) error(404, "找不到這支影片");
	return {
		video,
		clips: await repo.listClipsByVideo(DEV_OWNER_ID, params.videoId),
		tags: await repo.listTags(DEV_OWNER_ID),
		settings: await repo.getSettings(DEV_OWNER_ID)
	};
};
//#endregion
export { load };
