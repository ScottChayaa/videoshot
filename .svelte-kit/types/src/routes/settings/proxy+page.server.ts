// @ts-nocheck
import { DEV_OWNER_ID } from '$lib/constants';
import { getRepo } from '$lib/server/repo';
import type { PageServerLoad } from './$types';

export const load = async () => {
	const repo = getRepo();
	return {
		settings: await repo.getSettings(DEV_OWNER_ID),
		tags: await repo.listTags(DEV_OWNER_ID),
		// 第三階段接上真實探測結果；第一階段固定回報正常
		storyboardHealthy: true
	};
};
;null as any as PageServerLoad;