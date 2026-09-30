// @ts-nocheck
import { error } from '@sveltejs/kit';
import { DEV_OWNER_ID } from '$lib/constants';
import { getRepo } from '$lib/server/repo';
import type { PageServerLoad } from './$types';

export const load = async ({ params }: Parameters<PageServerLoad>[0]) => {
	const repo = getRepo();
	const video = await repo.getVideo(DEV_OWNER_ID, params.videoId);
	if (!video) error(404, '找不到這支影片');

	return {
		video,
		clips: await repo.listClipsByVideo(DEV_OWNER_ID, params.videoId),
		tags: await repo.listTags(DEV_OWNER_ID),
		settings: await repo.getSettings(DEV_OWNER_ID)
	};
};
