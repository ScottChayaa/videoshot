
// this file is generated — do not edit it


declare module "svelte/elements" {
	export interface HTMLAttributes<T> {
		'data-sveltekit-keepfocus'?: true | '' | 'off' | undefined | null;
		'data-sveltekit-noscroll'?: true | '' | 'off' | undefined | null;
		'data-sveltekit-preload-code'?:
			| true
			| ''
			| 'eager'
			| 'viewport'
			| 'hover'
			| 'tap'
			| 'off'
			| undefined
			| null;
		'data-sveltekit-preload-data'?: true | '' | 'hover' | 'tap' | 'off' | undefined | null;
		'data-sveltekit-reload'?: true | '' | 'off' | undefined | null;
		'data-sveltekit-replacestate'?: true | '' | 'off' | undefined | null;
	}
}

export {};


declare module "$app/types" {
	type MatcherParam<M> = M extends (param : string) => param is (infer U extends string) ? U : string;

	export interface AppTypes {
		RouteId(): "/" | "/api" | "/api/__reset" | "/api/clips" | "/api/clips/[id]" | "/api/search" | "/api/settings" | "/inbox" | "/settings" | "/v" | "/v/[videoId]";
		RouteParams(): {
			"/api/clips/[id]": { id: string };
			"/v/[videoId]": { videoId: string }
		};
		LayoutParams(): {
			"/": { id?: string | undefined; videoId?: string | undefined };
			"/api": { id?: string | undefined };
			"/api/__reset": Record<string, never>;
			"/api/clips": { id?: string | undefined };
			"/api/clips/[id]": { id: string };
			"/api/search": Record<string, never>;
			"/api/settings": Record<string, never>;
			"/inbox": Record<string, never>;
			"/settings": Record<string, never>;
			"/v": { videoId?: string | undefined };
			"/v/[videoId]": { videoId: string }
		};
		Pathname(): "/" | "/api/__reset" | "/api/clips" | `/api/clips/${string}` & {} | "/api/search" | "/api/settings" | "/inbox" | "/settings" | `/v/${string}` & {};
		ResolvedPathname(): `${"" | `/${string}`}${ReturnType<AppTypes['Pathname']>}`;
		Asset(): "/icons/icon-192.png" | "/icons/icon-512.png" | "/manifest.webmanifest" | string & {};
	}
}