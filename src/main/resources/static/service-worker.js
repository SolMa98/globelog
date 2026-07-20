// 최소한의 오프라인 지원용 서비스워커. 지구본(3D 지도 타일)까지 오프라인으로 띄우는
// 건 범위 밖 — 앱 셸(HTML/CSS/JS/아이콘)만 캐싱해서, 네트워크가 없을 때도 최소한
// 골격 화면은 뜨게 하는 정도로만 둔다.
// 정적 자산/아이콘을 새로 추가할 때마다 이 목록도 같이 챙겨야 한다 — 캐시 목록은
// HTML의 <link>/<script>에서 자동으로 뽑아오는 게 아니라 손으로 맞춰야 함.
var CACHE_NAME = 'globelog-shell-v2';
var APP_SHELL = [
    '/',
    '/globe.html',
    '/favicon.svg',
    '/favicon-16.png',
    '/favicon-32.png',
    '/apple-touch-icon-180.png',
    '/manifest.json',
    '/css/style.css',
    '/css/vendor/maplibre-gl.min.css',
    '/js/vendor/maplibre-gl.min.js',
    '/js/country-color.js',
    '/js/auth.js',
    '/js/feed.js',
    '/js/map.js',
    '/js/globe.js',
    '/js/pwa.js'
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

    // 정적 자산은 stale-while-revalidate — 캐시가 있으면 즉시 그걸로 응답해 오프라인에서도
    // 화면 골격이 뜨게 하되, 동시에 네트워크로 최신본을 받아와 캐시를 갱신한다. 순수
    // 캐시 우선(예전 방식)은 CACHE_NAME을 사람이 매번 안 올리면 배포 후에도 옛 자산을
    // 계속 서빙하는 문제가 있었음 — 이렇게 하면 다음 방문부터는 자동으로 최신이 반영된다.
    var url = new URL(req.url);
    if (url.origin === location.origin && APP_SHELL.indexOf(url.pathname) !== -1) {
        event.respondWith(
            caches.open(CACHE_NAME).then(function (cache) {
                return cache.match(req).then(function (cached) {
                    var networkFetch = fetch(req).then(function (res) {
                        if (res.ok) cache.put(req, res.clone());
                        return res;
                    }).catch(function () { return cached; });
                    return cached || networkFetch;
                });
            })
        );
    }
});

// ── 채팅 새 메시지 브라우저/OS 알림 ─────────────────────────────
// 서버(WebPushService)가 { title, body, url } JSON을 페이로드로 보낸다.
self.addEventListener('push', function (event) {
    var data = {};
    try { data = event.data ? event.data.json() : {}; } catch (e) {}
    var title = data.title || 'Globelog';
    event.waitUntil(
        self.registration.showNotification(title, {
            body: data.body || '',
            icon: '/app-icon-192.png',
            badge: '/favicon-32.png',
            data: { url: data.url || '/my/chat' }
        })
    );
});

// 알림을 클릭하면 해당 채팅방 탭이 이미 열려있으면 포커스만, 없으면 새로 연다.
self.addEventListener('notificationclick', function (event) {
    event.notification.close();
    var url = (event.notification.data && event.notification.data.url) || '/my/chat';
    event.waitUntil(
        self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (clients) {
            for (var i = 0; i < clients.length; i++) {
                if (clients[i].url.indexOf(url) !== -1 && 'focus' in clients[i]) {
                    return clients[i].focus();
                }
            }
            return self.clients.openWindow(url);
        })
    );
});
