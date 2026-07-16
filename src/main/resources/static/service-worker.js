// 최소한의 오프라인 지원용 서비스워커. 지구본(3D 지도 타일)까지 오프라인으로 띄우는
// 건 범위 밖 — 앱 셸(HTML/CSS/JS/아이콘)만 캐싱해서, 네트워크가 없을 때도 최소한
// 골격 화면은 뜨게 하는 정도로만 둔다.
var CACHE_NAME = 'globelog-shell-v1';
var APP_SHELL = [
    '/',
    '/globe.html',
    '/favicon.svg',
    '/manifest.json',
    '/css/style.css',
    '/css/vendor/maplibre-gl.min.css',
    '/js/vendor/maplibre-gl.min.js',
    '/js/country-color.js',
    '/js/auth.js',
    '/js/feed.js',
    '/js/map.js',
    '/js/globe.js'
];

self.addEventListener('install', function (event) {
    event.waitUntil(
        caches.open(CACHE_NAME).then(function (cache) { return cache.addAll(APP_SHELL); })
    );
    self.skipWaiting();
});

self.addEventListener('activate', function (event) {
    event.waitUntil(
        caches.keys().then(function (keys) {
            return Promise.all(keys.filter(function (k) { return k !== CACHE_NAME; })
                .map(function (k) { return caches.delete(k); }));
        })
    );
    self.clients.claim();
});

self.addEventListener('fetch', function (event) {
    var req = event.request;
    if (req.method !== 'GET') return;

    // 페이지 이동(HTML)은 항상 최신을 우선하고, 오프라인일 때만 캐시된 앱 셸로 대체한다.
    if (req.mode === 'navigate') {
        event.respondWith(
            fetch(req).catch(function () {
                return caches.match(req).then(function (res) { return res || caches.match('/'); });
            })
        );
        return;
    }

    // 정적 자산은 캐시 우선 — 자주 안 바뀌고, 오프라인에서도 화면 골격이 뜨게 하기 위함.
    var url = new URL(req.url);
    if (url.origin === location.origin && APP_SHELL.indexOf(url.pathname) !== -1) {
        event.respondWith(
            caches.match(req).then(function (cached) { return cached || fetch(req); })
        );
    }
});
