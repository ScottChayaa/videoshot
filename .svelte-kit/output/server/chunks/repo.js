import { n as parseStoryboardSpec } from "./storyboard.js";
//#region src/lib/constants.ts
var DEV_OWNER_ID = "dev@local";
var DEFAULT_SETTINGS = {
	markBeforeSec: 20,
	markAfterSec: 10,
	pauseOnMark: false
};
//#endregion
//#region src/lib/server/repo/mock.ts
var SEED_SB_SPEC = "https://i.ytimg.com/sb/KUdmrPVssFA/storyboard3_L$L/$N.jpg?sqp=-oaymwENSDfyq4qpAwVwAcABBqLzl_8DBgjTpKzTBg==|48#27#100#10#10#0#default#rs$AOn4CLDCQG-jwLOoOGPBLaFWxpqItJgENA|80#45#25#10#10#1000#M$M#rs$AOn4CLAdQajGjXcFllukj8IozdMskyx6Zw|160#90#25#5#5#1000#M$M#rs$AOn4CLDDTrcJY1ywfKuJLuu2E4bctSN8og|320#180#25#3#3#1000#M$M#rs$AOn4CLAF8rkqvc6h6mM0WUjOJy55DJC1vA";
var counter = 0;
var nextId = (prefix) => `${prefix}_${++counter}`;
function seedVideos() {
	return [{
		id: "KUdmrPVssFA",
		ownerId: DEV_OWNER_ID,
		title: "20260726 家庭聚會",
		channelTitle: "Scott Lin",
		publishedAt: "2026-07-26",
		durationSec: 24,
		privacy: "unlisted",
		sbKey: "sb/KUdmrPVssFA",
		sbSpec: parseStoryboardSpec(SEED_SB_SPEC)
	}, {
		id: "dQw4w9WgXcQ",
		ownerId: DEV_OWNER_ID,
		title: "宜蘭兩天一夜",
		channelTitle: "阿明的頻道",
		publishedAt: "2025-07-15",
		durationSec: 1471,
		privacy: "public",
		sbKey: null,
		sbSpec: null
	}];
}
function seedTags() {
	return [
		{
			id: "tag_a",
			ownerId: DEV_OWNER_ID,
			name: "阿明",
			kind: "person",
			aliases: ["明哥"]
		},
		{
			id: "tag_b",
			ownerId: DEV_OWNER_ID,
			name: "宜蘭",
			kind: "place",
			aliases: []
		},
		{
			id: "tag_c",
			ownerId: DEV_OWNER_ID,
			name: "露營",
			kind: "topic",
			aliases: []
		},
		{
			id: "tag_d",
			ownerId: DEV_OWNER_ID,
			name: "家庭聚會",
			kind: "topic",
			aliases: []
		}
	];
}
function seedClips(tags) {
	const base = {
		ownerId: DEV_OWNER_ID,
		thumbKey: null,
		aiRaw: null,
		origin: "web",
		createdAt: "2026-08-01T10:00:00.000Z"
	};
	return [
		{
			...base,
			id: "clip_seed_1",
			videoId: "dQw4w9WgXcQ",
			startSec: 312,
			endSec: 342,
			eventDate: "2025-07-12",
			note: "搭帳篷",
			summary: "一群人在營地手忙腳亂地搭帳篷",
			transcript: "這個角要先拉起來啦",
			visualDesc: "草地上三個人合力撐起一頂綠色帳篷",
			analysisLevel: "L2",
			status: "reviewed",
			tags: [
				{
					tag: tags[0],
					source: "ai"
				},
				{
					tag: tags[1],
					source: "human"
				},
				{
					tag: tags[2],
					source: "human"
				}
			]
		},
		{
			...base,
			id: "clip_seed_2",
			videoId: "dQw4w9WgXcQ",
			startSec: 750,
			endSec: 780,
			eventDate: "2025-07-12",
			note: "阿明跌倒",
			summary: "阿明在溪邊踩滑跌進水裡",
			transcript: "啊啊啊小心",
			visualDesc: "溪流旁的石頭上有人失去平衡",
			analysisLevel: "L2",
			status: "analyzed",
			tags: [{
				tag: tags[0],
				source: "ai"
			}, {
				tag: tags[1],
				source: "ai"
			}]
		},
		{
			...base,
			id: "clip_seed_3",
			videoId: "KUdmrPVssFA",
			startSec: 2,
			endSec: 12,
			eventDate: "2026-07-26",
			note: "",
			summary: "",
			transcript: "",
			visualDesc: "",
			analysisLevel: "L0",
			status: "inbox",
			tags: []
		},
		{
			...base,
			id: "clip_seed_4",
			videoId: "KUdmrPVssFA",
			startSec: 14,
			endSec: 24,
			eventDate: "2026-07-26",
			note: "大合照那段",
			summary: "",
			transcript: "",
			visualDesc: "",
			analysisLevel: "L0",
			status: "inbox",
			tags: []
		}
	];
}
var MockRepo = class {
	videos;
	tags;
	clips;
	settings;
	constructor() {
		this.videos = seedVideos();
		this.tags = seedTags();
		this.clips = seedClips(this.tags);
		this.settings = /* @__PURE__ */ new Map();
	}
	async getVideo(ownerId, videoId) {
		return this.videos.find((v) => v.ownerId === ownerId && v.id === videoId) ?? null;
	}
	async listClipsByVideo(ownerId, videoId) {
		return this.clips.filter((c) => c.ownerId === ownerId && c.videoId === videoId).sort((a, b) => a.startSec - b.startSec);
	}
	async listInbox(ownerId) {
		return this.clips.filter((c) => c.ownerId === ownerId && c.status !== "reviewed").sort((a, b) => b.createdAt.localeCompare(a.createdAt));
	}
	async getClip(ownerId, clipId) {
		return this.clips.find((c) => c.ownerId === ownerId && c.id === clipId) ?? null;
	}
	async createClip(input) {
		const video = await this.getVideo(input.ownerId, input.videoId);
		if (!video) throw new Error(`找不到影片 ${input.videoId}`);
		const clip = {
			id: nextId("clip"),
			videoId: input.videoId,
			ownerId: input.ownerId,
			startSec: input.startSec,
			endSec: input.endSec,
			eventDate: video.publishedAt,
			note: input.note ?? "",
			summary: "",
			transcript: "",
			visualDesc: "",
			thumbKey: null,
			aiRaw: null,
			analysisLevel: "L0",
			status: "inbox",
			origin: input.origin,
			createdAt: (/* @__PURE__ */ new Date()).toISOString(),
			tags: []
		};
		this.clips.push(clip);
		return clip;
	}
	async updateClip(ownerId, clipId, patch) {
		const clip = this.clips.find((c) => c.ownerId === ownerId && c.id === clipId);
		if (!clip) throw new Error(`找不到 clip ${clipId}`);
		const { tagIds, ...fields } = patch;
		Object.assign(clip, fields);
		if (tagIds) clip.tags = tagIds.map((id) => this.tags.find((t) => t.ownerId === ownerId && t.id === id)).filter((t) => Boolean(t)).map((tag) => ({
			tag,
			source: "human"
		}));
		return clip;
	}
	async deleteClip(ownerId, clipId) {
		this.clips = this.clips.filter((c) => !(c.ownerId === ownerId && c.id === clipId));
	}
	async searchClips(ownerId, query) {
		const text = query.text?.trim() ?? "";
		return {
			clips: this.clips.filter((c) => {
				if (c.ownerId !== ownerId || c.status !== "reviewed") return false;
				if (query.dateFrom && c.eventDate < query.dateFrom) return false;
				if (query.dateTo && c.eventDate > query.dateTo) return false;
				if (query.tagIds?.length) {
					const ids = c.tags.map((t) => t.tag.id);
					if (!query.tagIds.every((id) => ids.includes(id))) return false;
				}
				if (text) {
					if (![
						c.note,
						c.summary,
						c.transcript,
						c.visualDesc
					].join(" ").includes(text)) return false;
				}
				return true;
			}).sort((a, b) => b.eventDate.localeCompare(a.eventDate)),
			parsed: {
				dateFrom: query.dateFrom ?? null,
				dateTo: query.dateTo ?? null,
				tagNames: (query.tagIds ?? []).map((id) => this.tags.find((t) => t.id === id)?.name).filter((n) => Boolean(n)),
				keywords: text ? [text] : []
			}
		};
	}
	async listTags(ownerId) {
		return this.tags.filter((t) => t.ownerId === ownerId);
	}
	async getSettings(ownerId) {
		const existing = this.settings.get(ownerId);
		if (existing) return existing;
		const created = {
			ownerId,
			...DEFAULT_SETTINGS
		};
		this.settings.set(ownerId, created);
		return created;
	}
	async updateSettings(ownerId, patch) {
		const updated = {
			...await this.getSettings(ownerId),
			...patch,
			ownerId
		};
		this.settings.set(ownerId, updated);
		return updated;
	}
};
//#endregion
//#region src/lib/server/repo/index.ts
var instance = null;
function getRepo() {
	if (!instance) instance = new MockRepo();
	return instance;
}
function resetRepo() {
	instance = new MockRepo();
}
//#endregion
export { resetRepo as n, DEV_OWNER_ID as r, getRepo as t };
