// @ts-nocheck
import { DEV_OWNER_ID } from '$lib/constants';
import { getRepo } from '$lib/server/repo';
import type { Video } from '$lib/types';
import type { PageServerLoad } from './$types';

export const load = async ({ url }: Parameters<PageServerLoad>[0]) => {
	const repo = getRepo();
	const tagIds = url.searchParams.get('tagIds');

	const result = await repo.searchClips(DEV_OWNER_ID, {
		text: url.searchParams.get('text') ?? undefined,
		dateFrom: url.searchParams.get('dateFrom') ?? undefined,
		dateTo: url.searchParams.get('dateTo') ?? undefined,
		tagIds: tagIds ? tagIds.split(',').filter(Boolean) : undefined
	});

	const videos: Record<string, Video> = {};
	for (const clip of result.clips) {
		if (!videos[clip.videoId]) {
			const v = await repo.getVideo(DEV_OWNER_ID, clip.videoId);
			if (v) videos[clip.videoId] = v;
		}
	}

	return {
		result,
		videos,
		query: {
			text: url.searchParams.get('text') ?? '',
			dateFrom: url.searchParams.get('dateFrom') ?? '',
			dateTo: url.searchParams.get('dateTo') ?? ''
		}
	};
};
