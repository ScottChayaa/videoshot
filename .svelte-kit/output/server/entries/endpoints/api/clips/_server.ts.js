import { r as DEV_OWNER_ID, t as getRepo } from "../../../../chunks/repo.js";
import { error, json } from "@sveltejs/kit";
//#region src/routes/api/clips/+server.ts
var POST = async ({ request }) => {
	const body = await request.json();
	if (!body.videoId || typeof body.startSec !== "number" || typeof body.endSec !== "number") error(400, "videoId、startSec、endSec 為必填");
	if (body.endSec <= body.startSec) error(400, "endSec 必須大於 startSec");
	const clip = await getRepo().createClip({
		ownerId: DEV_OWNER_ID,
		videoId: body.videoId,
		startSec: body.startSec,
		endSec: body.endSec,
		note: body.note ?? "",
		origin: body.origin ?? "web"
	});
	return json(clip, { status: 201 });
};
var GET = async () => {
	return json(await getRepo().listInbox(DEV_OWNER_ID));
};
//#endregion
export { GET, POST };
