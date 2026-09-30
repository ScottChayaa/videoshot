export const manifest = (() => {
function __memo(fn) {
	let value;
	return () => value ??= (value = fn());
}

return {
	appDir: "_app",
	appPath: "_app",
	assets: new Set(["icons/icon-192.png","icons/icon-512.png","manifest.webmanifest"]),
	mimeTypes: {".png":"image/png",".webmanifest":"application/manifest+json"},
	_: {
		client: {start:"_app/immutable/entry/start.BHBesL6l.js",app:"_app/immutable/entry/app.Cx-mhtPI.js",imports:["_app/immutable/entry/start.BHBesL6l.js","_app/immutable/chunks/DnollhjR.js","_app/immutable/chunks/BpLxTbD7.js","_app/immutable/entry/app.Cx-mhtPI.js","_app/immutable/chunks/BpLxTbD7.js","_app/immutable/chunks/xihTtKlq.js"],stylesheets:[],fonts:[],uses_env_dynamic_public:true},
		nodes: [
			__memo(() => import('./nodes/0.js')),
			__memo(() => import('./nodes/1.js')),
			__memo(() => import('./nodes/2.js')),
			__memo(() => import('./nodes/3.js')),
			__memo(() => import('./nodes/4.js')),
			__memo(() => import('./nodes/5.js'))
		],
		remotes: {
			
		},
		routes: [
			{
				id: "/",
				pattern: /^\/$/,
				params: [],
				page: { layouts: [0,], errors: [1,], leaf: 2 },
				endpoint: null
			},
			{
				id: "/api/__reset",
				pattern: /^\/api\/__reset\/?$/,
				params: [],
				page: null,
				endpoint: __memo(() => import('./entries/endpoints/api/__reset/_server.ts.js'))
			},
			{
				id: "/api/clips",
				pattern: /^\/api\/clips\/?$/,
				params: [],
				page: null,
				endpoint: __memo(() => import('./entries/endpoints/api/clips/_server.ts.js'))
			},
			{
				id: "/api/clips/[id]",
				pattern: /^\/api\/clips\/([^/]+?)\/?$/,
				params: [{"name":"id","optional":false,"rest":false,"chained":false}],
				page: null,
				endpoint: __memo(() => import('./entries/endpoints/api/clips/_id_/_server.ts.js'))
			},
			{
				id: "/api/search",
				pattern: /^\/api\/search\/?$/,
				params: [],
				page: null,
				endpoint: __memo(() => import('./entries/endpoints/api/search/_server.ts.js'))
			},
			{
				id: "/api/settings",
				pattern: /^\/api\/settings\/?$/,
				params: [],
				page: null,
				endpoint: __memo(() => import('./entries/endpoints/api/settings/_server.ts.js'))
			},
			{
				id: "/inbox",
				pattern: /^\/inbox\/?$/,
				params: [],
				page: { layouts: [0,], errors: [1,], leaf: 3 },
				endpoint: null
			},
			{
				id: "/settings",
				pattern: /^\/settings\/?$/,
				params: [],
				page: { layouts: [0,], errors: [1,], leaf: 4 },
				endpoint: null
			},
			{
				id: "/v/[videoId]",
				pattern: /^\/v\/([^/]+?)\/?$/,
				params: [{"name":"videoId","optional":false,"rest":false,"chained":false}],
				page: { layouts: [0,], errors: [1,], leaf: 5 },
				endpoint: null
			}
		],
		prerendered_routes: new Set([]),
		matchers: async () => {
			
			return {  };
		},
		server_assets: {}
	}
}
})();
