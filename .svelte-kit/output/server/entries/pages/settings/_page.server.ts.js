import { r as DEV_OWNER_ID, t as getRepo } from "../../../chunks/repo.js";
//#region src/routes/settings/+page.server.ts
var load = async () => {
	const repo = getRepo();
	return {
		settings: await repo.getSettings(DEV_OWNER_ID),
		tags: await repo.listTags(DEV_OWNER_ID),
		storyboardHealthy: true
	};
};
//#endregion
export { load };
