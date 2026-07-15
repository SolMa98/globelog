(function () {
    var map = null;
    var regionMarkers = [];
    var currentOwnerNickname = null; // 지금 보고 있는 지구본의 소유자 — country/region/trip API 호출마다 실어보냄
    var currentCountryIsoA3 = null; // "지역 미지정 여행" 버튼 클릭 시 어느 국가 기준인지 알아야 해서 저장
    var currentCountryNameKo = null;

    // ── 언어 설정 ─────────────────────────────────────────────
    var LANG_STORAGE_KEY = 'map-lang';
    var currentLang = localStorage.getItem(LANG_STORAGE_KEY) || 'ko';

    // OpenFreeMap — API 키 불필요, 완전 무료 (positron: 레이어 수 적어 초기 로드 빠름)
    var STYLE_URL = 'https://tiles.openfreemap.org/styles/positron';

    // 마스크 · 국경선 소스/레이어 ID
    var MASK_SRC   = 'country-mask-src';
    var MASK_LAYER = 'country-mask-fill';
    var BORDER_SRC   = 'country-border-src';
    var BORDER_LAYER = 'country-border-line';

    // ── DOM 참조 ─────────────────────────────────────────────
    var globeViewEl            = document.getElementById('globe-view');
    var mapViewEl              = document.getElementById('map-view');
    var mapContainerEl         = document.getElementById('map-container');
    var mapCountryNameEl       = document.getElementById('map-country-name');
    var mapFlagEl              = document.getElementById('map-flag');
    var mapBackBtn             = document.getElementById('map-back-btn');
    var mapNoRegionBtnEl       = document.getElementById('map-no-region-btn');
    var mapNoRegionCountEl     = document.getElementById('map-no-region-count');
    var mapTileLoadingEl       = document.getElementById('map-tile-loading');
    var mapLangToggleEl        = document.getElementById('map-lang-toggle');
    var storyViewEl            = document.getElementById('story-view');
    var storyProgressEl        = document.getElementById('story-progress');
    var storyCloseEl           = document.getElementById('story-close');
    var storyLoadingEl         = document.getElementById('story-loading');
    var storyEmptyEl           = document.getElementById('story-empty');
    var storySlideEl           = document.getElementById('story-slide');
    var storyMediaEl           = document.getElementById('story-media');
    var storyNavPrevEl         = document.getElementById('story-nav-prev');
    var storyNavNextEl         = document.getElementById('story-nav-next');
    var storyRegionNameEl      = document.getElementById('story-region-name');
    var storyVisitBadgeEl      = document.getElementById('story-visit-badge');
    var storyTitleEl           = document.getElementById('story-title');
    var storyDateEl            = document.getElementById('story-date');
    var storyDescriptionEl     = document.getElementById('story-description');
    var storyJumpToggleEl      = document.getElementById('story-jump-toggle');
    var storyJumpCloseEl       = document.getElementById('story-jump-close');
    var storyJumpListEl        = document.getElementById('story-jump-list');
    var storyJumpItemsEl       = document.getElementById('story-jump-items');

    var storySlides = [];
    var storyIndex = 0;
    var storyTripEntries = []; // 순차재생 없이 바로 점프할 게시글 목록 — trip당 1건(대표 사진만)

    // ── 스토리 자동 넘김 (인스타그램 스토리 방식: 일정 시간 뒤 자동 다음, 길게 누르면 정지) ──
    var STORY_SLIDE_DURATION = 10000; // 슬라이드당 10초
    var STORY_HOLD_THRESHOLD = 200;   // 이 시간(ms) 이상 누르고 있으면 "길게 누름"(정지)으로 판단
    var storyAutoTimer = null;
    var storyAutoStartedAt = 0;
    var storyAutoRemaining = STORY_SLIDE_DURATION;
    var storyPaused = false;
    var storyCurrentFillEl = null;

    // ── 언어 적용 ─────────────────────────────────────────────
    function applyLanguage(lang) {
        currentLang = lang;
        localStorage.setItem(LANG_STORAGE_KEY, lang);

        mapLangToggleEl.querySelectorAll('.lang-btn').forEach(function (btn) {
            btn.classList.toggle('active', btn.dataset.lang === lang);
        });

        if (!map || !map.isStyleLoaded()) return;

        var nameExpr = lang === 'ko'
            ? ['coalesce', ['get', 'name:ko'], ['get', 'name:en'], ['get', 'name']]
            : ['coalesce', ['get', 'name:en'], ['get', 'name']];

        map.getStyle().layers.forEach(function (layer) {
            if (layer.type !== 'symbol') return;
            if (!layer.layout || !('text-field' in layer.layout)) return;
            try { map.setLayoutProperty(layer.id, 'text-field', nameExpr); } catch (e) {}
        });
    }

    // ── 초기화 ──────────────────────────────────────────────
    function initMap() {
        if (map) return;
        map = new maplibregl.Map({
            container: mapContainerEl,
            style: STYLE_URL,
            maxZoom: 10,
            renderWorldCopies: false,
            attributionControl: true
        });

        map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');

        map.on('dataloading', function () { mapTileLoadingEl.classList.remove('hidden'); });
        map.on('idle',        function () { mapTileLoadingEl.classList.add('hidden'); });

        map.on('load', function () { applyLanguage(currentLang); });
    }

    // ── 폴리곤 방향 보정 (GeoJSON: 외곽 CCW, 구멍 CW) ────────
    function signedArea(ring) {
        var a = 0;
        for (var i = 0, j = ring.length - 1; i < ring.length; j = i++) {
            a += (ring[j][0] + ring[i][0]) * (ring[j][1] - ring[i][1]);
        }
        return a;
    }
    function ensureCCW(ring) { return signedArea(ring) < 0 ? ring.slice().reverse() : ring; }
    function ensureCW(ring)  { return signedArea(ring) > 0 ? ring.slice().reverse() : ring; }

    // ── 레이어 정리 ─────────────────────────────────────────
    function clearRegionMarkers() {
        regionMarkers.forEach(function (m) { m.remove(); });
        regionMarkers = [];
    }

    function clearMapOverlays() {
        [MASK_LAYER, BORDER_LAYER].forEach(function (id) {
            if (map.getLayer(id)) map.removeLayer(id);
        });
        [MASK_SRC, BORDER_SRC].forEach(function (id) {
            if (map.getSource(id)) map.removeSource(id);
        });
    }

    // ── 경도 정규화 (-180 ~ 180) ────────────────────────────
    function normalizeLon(lon) {
        while (lon > 180)  lon -= 360;
        while (lon < -180) lon += 360;
        return lon;
    }

    // ── 국가 경계에 맞게 줌 ──────────────────────────────────
    function fitCountryBounds(polygons, centroid) {
        map.setMinZoom(1);
        var lats = [], lons = [];
        polygons.forEach(function (rings) {
            rings[0].forEach(function (c) {
                lats.push(c[1]);
                lons.push(normalizeLon(c[0]));
            });
        });

        var minLat = Math.min.apply(null, lats), maxLat = Math.max.apply(null, lats);
        var minLon = Math.min.apply(null, lons), maxLon = Math.max.apply(null, lons);

        if (maxLon - minLon > 270) {
            // 날짜변경선을 넘는 나라 (러시아, 미국 등) — 경도가 +쪽/−쪽 두 뭉치로 쪼개져
            // minLon~maxLon을 그대로 못 씀. +쪽 최솟값을 서쪽 경계, −쪽 최댓값을
            // 동쪽 경계로 삼음 (centroid는 정점 단순 평균이라 국경 좌표 밀도에 따라
            // 중심이 엉뚱한 곳으로 쏠릴 수 있어 대상 목적지로 쓰기 부적절함).
            //
            // map.fitBounds()에 이 bounds를 그대로 넘기면 내부 cameraForBounds가 계산한
            // 중심 경도가 -180~180 범위를 벗어난 "unwrap"된 값(예: 232°)으로 나오는데,
            // flyTo가 이 값을 그대로 목적지로 삼아 가까운 서쪽이 아니라 아시아를 가로질러
            // 도는 먼 길로 날아가 버린다. cameraForBounds로 줌/중심만 미리 계산해서
            // 경도를 정규화한 뒤 직접 flyTo로 넘겨서 항상 가까운 경로로 날아가게 한다.
            //
            // 그런데 renderWorldCopies:false 상태에서는, 정규화된 목적지로 flyTo를
            // 시켜도 지도의 "constrain" 로직이 -180/180 경계를 넘지 않도록 카메라를
            // 강제로 안쪽으로 밀어버려서(예: 서쪽 alaska/알류샨열도가 화면 밖으로
            // 밀려나 짤려 보임), 요청한 중심에 도착하지 못한다. 날짜변경선을 넘는
            // 나라를 보는 동안만 renderWorldCopies를 켜서 이 제약을 풀어준다.
            map.setRenderWorldCopies(true);
            var positiveLons = lons.filter(function (l) { return l >= 0; });
            var negativeLons = lons.filter(function (l) { return l < 0; });
            var west = Math.min.apply(null, positiveLons);
            var east = Math.max.apply(null, negativeLons);
            var cam = map.cameraForBounds([[west, minLat], [east, maxLat]], { padding: 40, maxZoom: 6 });
            if (cam) {
                cam.center.lng = normalizeLon(cam.center.lng);
                map.flyTo(cam);
            }
        } else {
            map.setRenderWorldCopies(false);
            map.fitBounds([[minLon, minLat], [maxLon, maxLat]], { padding: 40, maxZoom: 8 });
        }
        map.once('moveend', function () { map.setMinZoom(map.getZoom()); });
    }

    // ── 날짜변경선을 넘는 나라의 경도를 한쪽으로 이어붙이기(unwrap) ──
    // normalizeLon만 적용하면 러시아·미국 같은 나라의 폴리곤 조각이 +180/-180
    // 양쪽으로 갈라져서 화면 양 끝에 뚝뚝 끊어진 것처럼 보인다.
    //
    // 처음엔 "다수 조각의 부호에 맞춰 소수 조각을 ±360 이동"시켰는데, 이러면
    // 미국처럼 다수가 음수인 나라는 소수 쪽(알류샨열도)이 -360 이동해 -180보다
    // 작은 값(예: -188)이 되어버림. MapLibre GeoJSON 소스는 +180을 넘는 좌표는
    // 정상 렌더링하지만 **-180보다 작은 좌표는 도형째로 누락시키는 비대칭 동작**이
    // 있어서, 그 부분이 통째로 사라져 "짤리는" 문제가 생겼다. 그래서 다수/소수와
    // 무관하게 항상 음수 조각만 +360 이동시켜 양수 쪽으로 통일한다(절대 -180
    // 밑으로 내려가지 않게).
    function makeLonUnwrapper(polygons) {
        var lons = [];
        polygons.forEach(function (rings) {
            rings[0].forEach(function (c) { lons.push(normalizeLon(c[0])); });
        });
        var minLon = Math.min.apply(null, lons), maxLon = Math.max.apply(null, lons);
        var crosses = (maxLon - minLon) > 270;
        return function (lon) {
            var n = normalizeLon(lon);
            if (!crosses) return n;
            return n < 0 ? n + 360 : n;
        };
    }

    // ── 선택된 국가 외 영역을 어둡게 마스킹 ─────────────────
    function addCountryMask(polygons) {
        var lonFix = makeLonUnwrapper(polygons);
        var worldBox = ensureCCW([[-360, -90], [-360, 90], [360, 90], [360, -90], [-360, -90]]);
        var coordinates = [worldBox];
        polygons.forEach(function (rings) {
            var ring = rings[0].map(function (c) { return [lonFix(c[0]), c[1]]; });
            coordinates.push(ensureCW(ring));
        });

        map.addSource(MASK_SRC, {
            type: 'geojson',
            data: { type: 'Feature', geometry: { type: 'Polygon', coordinates: coordinates } }
        });
        map.addLayer({
            id: MASK_LAYER,
            type: 'fill',
            source: MASK_SRC,
            paint: { 'fill-color': '#04060f', 'fill-opacity': 0.28 }
        });
    }

    // ── 국가 경계선 표시 ─────────────────────────────────────
    function addCountryBoundary(polygons) {
        var lonFix = makeLonUnwrapper(polygons);
        var coords = polygons.map(function (rings) {
            return rings.map(function (ring) {
                return ring.map(function (c) { return [lonFix(c[0]), c[1]]; });
            });
        });

        map.addSource(BORDER_SRC, {
            type: 'geojson',
            data: {
                type: 'Feature',
                geometry: {
                    type: polygons.length === 1 ? 'Polygon' : 'MultiPolygon',
                    coordinates: polygons.length === 1 ? coords[0] : coords
                }
            }
        });
        map.addLayer({
            id: BORDER_LAYER,
            type: 'line',
            source: BORDER_SRC,
            paint: { 'line-color': '#3aa6c2', 'line-width': 2.5, 'line-opacity': 0.9 }
        });
    }

    // ── 지도 뷰 열기 ─────────────────────────────────────────
    async function openMapView(isoA3, nameKo, polygons, centroid, openOptions, ownerNickname) {
        currentOwnerNickname = ownerNickname;
        currentCountryIsoA3 = isoA3;
        currentCountryNameKo = nameKo;
        globeViewEl.classList.add('hidden');
        mapViewEl.classList.remove('hidden');
        mapCountryNameEl.textContent = nameKo;
        mapFlagEl.style.display = 'none';
        mapNoRegionBtnEl.classList.add('hidden');
        closeStory();
        clearRegionMarkers();

        initMap();
        setTimeout(function () { map.resize(); }, 50);
        // 카메라 이동(포커스)은 스타일 로딩과 무관한 순수 카메라 조작이라 바로 실행—
        // 예전엔 이것도 아래 onMapReady 안에 있어서, 타일 서버 응답이 늦으면 국가가
        // 화면 중앙(이전 뷰 위치)에 그대로 남아 "포커스가 안 된 것처럼" 보이는 문제가 있었다.
        fitCountryBounds(polygons, centroid);

        // addSource/addLayer가 필요한 마스크·국경선만 2D 지도 스타일이 다 로드된 뒤로
        // 미룬다(안 그러면 "Style is not done loading" 예외). 지역 마커·국기·스토리·카메라
        // 이동은 전부 스타일/외부 타일 로딩과 무관하게 map 인스턴스만 있으면 바로 가능하다.
        var overlaysDrawn = false;
        function drawOverlaysOnce() {
            if (overlaysDrawn) return;
            overlaysDrawn = true;
            clearMapOverlays();
            addCountryMask(polygons);
            addCountryBoundary(polygons);
        }

        if (map.isStyleLoaded()) {
            drawOverlaysOnce();
        } else {
            // 'load'는 지도 인스턴스 생애주기에서 딱 한 번만 발생하는 이벤트라, 이 시점
            // 이전에 이미 지나가버렸는데 isStyleLoaded()가 아직 false를 반환하는 경계
            // 상황이면 once('load', ...)가 영원히 안 불릴 수 있다 — 'idle'(렌더링이
            // 안정될 때마다 반복 발생)을 안전망으로 같이 걸어서, 둘 중 먼저 오는 쪽이
            // 그리고 나머지는 가드 플래그로 무시되게 한다.
            map.once('load', drawOverlaysOnce);
            map.once('idle', drawOverlaysOnce);
        }

        loadCountryDetail(isoA3);
        var regions = await loadRegionMarkers(isoA3);
        var noRegionCount = await refreshNoRegionEntry(isoA3);

        if (openOptions && openOptions.openTripId) {
            // 검색 결과처럼 어느 지역인지 이미 아는 경우엔 그대로 쓰고, 피드처럼
            // 지역 정보 없이 tripId만 들어온 경우엔 그 여행이 실제로 어느
            // 지역(또는 지역 없음)인지부터 서버에 물어봐야 한다.
            var regionId = openOptions.openRegionId || await fetchTripRegionId(openOptions.openTripId);
            if (regionId) {
                var target = regions.find(function (r) { return r.id === regionId; });
                if (target) await openStory(target, openOptions.openTripId);
            } else if (noRegionCount > 0) {
                await openStory({ countryIsoA3: isoA3, nameKo: nameKo }, openOptions.openTripId);
            }
        }
    }

    // 지역을 지정하지 않고 국가 단위로만 등록한 여행이 있으면 버튼을 보여준다.
    async function refreshNoRegionEntry(isoA3) {
        try {
            var res = await fetch('/api/countries/' + isoA3 + '/trips?owner=' + encodeURIComponent(currentOwnerNickname));
            var trips = res.ok ? await res.json() : [];
            mapNoRegionCountEl.textContent = trips.length;
            mapNoRegionBtnEl.classList.toggle('hidden', trips.length === 0);
            return trips.length;
        } catch (e) {
            mapNoRegionBtnEl.classList.add('hidden');
            return 0;
        }
    }

    async function fetchTripRegionId(tripId) {
        try {
            var res = await fetch('/api/trips/' + tripId + '?owner=' + encodeURIComponent(currentOwnerNickname));
            if (!res.ok) return null;
            var detail = await res.json();
            return detail.regionId || null;
        } catch (e) {
            return null;
        }
    }

    async function loadCountryDetail(isoA3) {
        try {
            var res = await fetch('/api/countries/' + isoA3);
            if (res.ok) {
                var d = await res.json();
                if (d.flagUrl) { mapFlagEl.src = d.flagUrl; mapFlagEl.style.display = ''; }
            }
        } catch (e) {}
    }

    // 순수 REST 조회만(지도 필요 없음) — 스토리를 열지 판단하는 로직이 2D 지도 스타일/타일
    // 로딩과 무관하게 즉시 쓸 수 있도록 마커 추가와 분리해뒀다.
    async function fetchRegions(isoA3) {
        try {
            var res = await fetch('/api/countries/' + isoA3 + '/regions?owner=' + encodeURIComponent(currentOwnerNickname));
            return res.ok ? await res.json() : [];
        } catch (e) { return []; }
    }

    async function loadRegionMarkers(isoA3) {
        try {
            var regions = await fetchRegions(isoA3);
            regions.forEach(function (region) {
                if (region.centerLat == null || region.centerLng == null) return;

                var el = document.createElement('div');
                el.className = 'region-marker';

                var popup = new maplibregl.Popup({
                    closeButton: false,
                    closeOnClick: false,
                    anchor: 'bottom',
                    offset: [0, -5],
                    className: 'region-popup'
                }).setText(region.nameKo);

                var marker = new maplibregl.Marker({ element: el })
                    .setLngLat([region.centerLng, region.centerLat])
                    .addTo(map);

                el.addEventListener('mouseenter', function () {
                    popup.setLngLat([region.centerLng, region.centerLat]).addTo(map);
                });
                el.addEventListener('mouseleave', function () { popup.remove(); });
                el.addEventListener('click', function () { openStory(region); });

                regionMarkers.push(marker);
            });
            return regions;
        } catch (e) { return []; }
    }

    // ── 여행 스토리 ───────────────────────────────────────────
    // 지역(또는 지역 미지정 국가 단위)의 방문 기록을 인스타그램 스토리처럼 사진 중심
    // 풀스크린 슬라이드로 보여준다. 사진이 여러 장인 방문은 사진 수만큼 슬라이드로
    // 쪼개고(각 슬라이드에 같은 설명을 반복 표시), 사진이 없는 방문은 그라디언트
    // 배경의 슬라이드 1장으로 대체한다.
    // source: 지역 마커에서 왔으면 {id, nameKo}, "지역 미지정" 진입점이면
    // {countryIsoA3, nameKo} — buildSlide는 source.nameKo만 쓰므로 이름은 공용.
    async function openStory(source, focusTripId) {
        clearAutoTimer();
        storyPaused = false;
        storySlides = [];
        storyIndex = 0;
        storyTripEntries = [];
        closeJumpList();
        storySlideEl.classList.add('hidden');
        storyEmptyEl.classList.add('hidden');
        storyProgressEl.innerHTML = '';
        storyLoadingEl.classList.remove('hidden');
        storyViewEl.classList.remove('hidden');

        try {
            var ownerParam = 'owner=' + encodeURIComponent(currentOwnerNickname);
            var listUrl = source.id
                ? '/api/regions/' + source.id + '/trips?' + ownerParam
                : '/api/countries/' + source.countryIsoA3 + '/trips?' + ownerParam;
            var listRes = await fetch(listUrl);
            var trips = listRes.ok ? await listRes.json() : [];

            var details = await Promise.all(trips.map(function (trip) {
                return fetch('/api/trips/' + trip.id + '?' + ownerParam)
                    .then(function (res) { return res.ok ? res.json() : null; })
                    .catch(function () { return null; });
            }));

            trips.forEach(function (trip, i) {
                var detail = details[i];
                var description = detail ? (detail.description || '') : '';
                var images = (detail && detail.images) ? detail.images : [];
                var viewCount = detail ? detail.viewCount : trip.viewCount;
                var firstSlideIndex = storySlides.length;
                if (images.length === 0) {
                    storySlides.push(buildSlide(source, trip, description, null, 1, 1, viewCount));
                } else {
                    images.forEach(function (img, photoIndex) {
                        storySlides.push(buildSlide(source, trip, description, img.url, photoIndex + 1, images.length, viewCount));
                    });
                }
                storyTripEntries.push({
                    tripId: trip.id,
                    title: trip.title,
                    visitedDate: trip.visitedDate,
                    thumbnailUrl: images.length > 0 ? images[0].url : null,
                    firstSlideIndex: firstSlideIndex
                });
            });
        } catch (e) {
            storySlides = [];
            storyTripEntries = [];
        }

        storyLoadingEl.classList.add('hidden');

        if (storySlides.length === 0) {
            storyEmptyEl.classList.remove('hidden');
            return;
        }

        storySlides.forEach(function () {
            var seg = document.createElement('div');
            seg.className = 'story-progress-seg';
            var fill = document.createElement('div');
            fill.className = 'story-progress-fill';
            seg.appendChild(fill);
            storyProgressEl.appendChild(seg);
        });

        var startIndex = 0;
        if (focusTripId) {
            var found = storySlides.findIndex(function (s) { return s.tripId === focusTripId; });
            if (found >= 0) startIndex = found;
        }
        storySlideEl.classList.remove('hidden');
        renderStorySlide(startIndex);
    }

    function buildSlide(region, trip, description, imageUrl, photoIndex, photoCount, viewCount) {
        return {
            tripId: trip.id,
            regionNameKo: region.nameKo,
            visitNumber: trip.visitNumber,
            title: trip.title,
            visitedDate: trip.visitedDate,
            description: description,
            imageUrl: imageUrl,
            photoIndex: photoIndex,
            photoCount: photoCount,
            viewCount: viewCount
        };
    }

    function renderStorySlide(index) {
        if (index < 0 || index >= storySlides.length) return;
        storyIndex = index;
        var slide = storySlides[index];

        var fills = storyProgressEl.querySelectorAll('.story-progress-fill');
        fills.forEach(function (fill, i) {
            fill.style.transition = 'none';
            fill.style.width = (i < storyIndex) ? '100%' : '0%';
        });
        storyCurrentFillEl = fills[storyIndex] || null;
        storyPaused = false;

        // 마지막 슬라이드는 자동 넘길 다음 슬라이드가 없으므로 자동재생을 걸지 않는다
        // (예전엔 여기서 타이머가 끝나면 스토리 자체가 자동으로 닫혀서, 다 읽기도 전에
        // 갑자기 화면이 꺼지는 것처럼 느껴진다는 불만이 있었다). 진행바만 다 찬 채로
        // 멈춰 있고, 닫는 건 사용자가 닫기 버튼을 눌러야만 일어난다.
        if (storyIndex >= storySlides.length - 1) {
            clearAutoTimer();
            if (storyCurrentFillEl) {
                storyCurrentFillEl.style.transition = 'none';
                storyCurrentFillEl.style.width = '100%';
            }
        } else {
            startAutoAdvance();
        }

        if (slide.imageUrl) {
            storyMediaEl.classList.remove('story-media-empty');
            storyMediaEl.style.backgroundImage = 'url("' + slide.imageUrl + '")';
        } else {
            storyMediaEl.classList.add('story-media-empty');
            storyMediaEl.style.backgroundImage = '';
        }

        storyRegionNameEl.textContent = slide.regionNameKo;
        storyVisitBadgeEl.textContent = slide.visitNumber + '번째 방문'
            + (slide.photoCount > 1 ? ' · ' + slide.photoIndex + '/' + slide.photoCount : '');
        storyTitleEl.textContent = slide.title || '(제목 없음)';
        storyDateEl.textContent = [slide.visitedDate, '조회 ' + (slide.viewCount || 0)].filter(Boolean).join(' · ');
        storyDescriptionEl.textContent = slide.description;

        registerView(slide.tripId);
    }

    // 같은 게시글을 여러 번 열어도(사진이 여러 장이라 슬라이드를 오가는 경우 포함)
    // 하루에 한 번만 조회수를 올린다 — 서버가 아니라 클라이언트 localStorage로
    // 가볍게 절충한 것(project_trip_view_count_and_feed_filters 메모리 참고).
    function registerView(tripId) {
        if (!tripId) return;
        var key = 'globelog-viewed-' + tripId;
        var today = new Date().toISOString().slice(0, 10);
        if (localStorage.getItem(key) === today) return;
        fetchCsrf().then(function (csrf) {
            var headers = {};
            if (csrf) headers[csrf.headerName] = csrf.token;
            return fetch('/api/trips/' + tripId + '/view', { method: 'POST', headers: headers });
        }).then(function () { localStorage.setItem(key, today); })
            .catch(function () {});
    }

    // 쿠키(XSRF-TOKEN)에 든 원본 토큰 값을 그대로 헤더에 넣으면 안 된다 — Spring
    // Security 6 기본 핸들러(XorCsrfTokenRequestAttributeHandler)는 응답 바디로
    // 내려준 "마스킹된" 토큰만 유효하게 검증한다(쿠키 원본 값은 403). auth.js 등
    // 다른 화면과 동일하게 /api/me 응답의 csrfToken을 써야 한다.
    var csrfPromise = null;
    function fetchCsrf() {
        if (!csrfPromise) {
            csrfPromise = fetch('/api/me')
                .then(function (res) { return res.json(); })
                .then(function (me) { return { headerName: me.csrfHeaderName, token: me.csrfToken }; })
                .catch(function () { csrfPromise = null; return null; });
        }
        return csrfPromise;
    }

    // ── 게시글 목록으로 바로 점프 ──────────────────────────────
    // 순차재생만 있으면 특정 게시글을 보려고 전체를 다 훑어야 하는 불편함이 있어서
    // (project_story_feature_feedback 메모리 참고), 목록에서 바로 선택할 수 있게 함.
    function renderJumpList() {
        storyJumpItemsEl.innerHTML = '';
        var currentTripId = storySlides[storyIndex] ? storySlides[storyIndex].tripId : null;

        storyTripEntries.forEach(function (entry) {
            var item = document.createElement('li');
            item.className = 'story-jump-item' + (entry.tripId === currentTripId ? ' active' : '');

            var thumb = document.createElement('div');
            thumb.className = 'story-jump-item-thumb';
            if (entry.thumbnailUrl) {
                thumb.style.backgroundImage = 'url("' + entry.thumbnailUrl + '")';
            }
            item.appendChild(thumb);

            var body = document.createElement('div');
            body.className = 'story-jump-item-body';
            var titleEl = document.createElement('div');
            titleEl.className = 'story-jump-item-title';
            titleEl.textContent = entry.title || '(제목 없음)';
            var dateEl = document.createElement('div');
            dateEl.className = 'story-jump-item-date';
            dateEl.textContent = entry.visitedDate || '';
            body.appendChild(titleEl);
            body.appendChild(dateEl);
            item.appendChild(body);

            item.addEventListener('click', function () { jumpToTrip(entry.tripId); });
            storyJumpItemsEl.appendChild(item);
        });
    }

    function jumpToTrip(tripId) {
        var entry = storyTripEntries.find(function (e) { return e.tripId === tripId; });
        if (!entry) return;
        closeJumpList();
        renderStorySlide(entry.firstSlideIndex);
    }

    function openJumpList() {
        pauseAutoAdvance();
        renderJumpList();
        storyJumpListEl.classList.remove('hidden');
    }

    function closeJumpList() {
        storyJumpListEl.classList.add('hidden');
        if (storyPaused) resumeAutoAdvance();
    }

    function storyNext() {
        if (storyIndex >= storySlides.length - 1) { closeStory(); return; }
        renderStorySlide(storyIndex + 1);
    }

    function storyPrev() {
        if (storyIndex <= 0) return;
        renderStorySlide(storyIndex - 1);
    }

    function clearAutoTimer() {
        if (storyAutoTimer) { clearTimeout(storyAutoTimer); storyAutoTimer = null; }
    }

    // 현재 슬라이드를 처음부터 STORY_SLIDE_DURATION 동안 재생 (진행바 채우기 애니메이션 포함)
    function startAutoAdvance() {
        clearAutoTimer();
        storyAutoRemaining = STORY_SLIDE_DURATION;
        storyAutoStartedAt = Date.now();
        storyAutoTimer = setTimeout(storyNext, STORY_SLIDE_DURATION);
        if (storyCurrentFillEl) {
            storyCurrentFillEl.style.transition = 'none';
            storyCurrentFillEl.style.width = '0%';
            void storyCurrentFillEl.offsetWidth; // 강제 리플로우: 다음 transition이 0%부터 다시 시작하도록
            storyCurrentFillEl.style.transition = 'width ' + STORY_SLIDE_DURATION + 'ms linear';
            storyCurrentFillEl.style.width = '100%';
        }
    }

    // 길게 누르는 동안 자동 넘김/진행바를 그 자리에서 멈춘다("감상하기")
    function pauseAutoAdvance() {
        if (storyPaused || !storyAutoTimer) return;
        storyPaused = true;
        clearAutoTimer();
        var elapsed = Date.now() - storyAutoStartedAt;
        storyAutoRemaining = Math.max(0, storyAutoRemaining - elapsed);
        if (storyCurrentFillEl) {
            var progressed = (STORY_SLIDE_DURATION - storyAutoRemaining) / STORY_SLIDE_DURATION * 100;
            storyCurrentFillEl.style.transition = 'none';
            storyCurrentFillEl.style.width = Math.min(100, Math.max(0, progressed)) + '%';
        }
    }

    // 손을 떼면 멈췄던 지점부터 남은 시간만큼 이어서 재생
    function resumeAutoAdvance() {
        if (!storyPaused) return;
        storyPaused = false;
        storyAutoStartedAt = Date.now();
        storyAutoTimer = setTimeout(storyNext, storyAutoRemaining);
        if (storyCurrentFillEl) {
            void storyCurrentFillEl.offsetWidth;
            storyCurrentFillEl.style.transition = 'width ' + storyAutoRemaining + 'ms linear';
            storyCurrentFillEl.style.width = '100%';
        }
    }

    // 탭(짧게 눌렀다 떼기)은 이전/다음 이동, 일정 시간 이상 누르고 있으면 정지("감상하기")
    function bindStoryNavZone(el, onTap) {
        var holdTimer = null;
        var holding = false;
        var isDown = false; // pointerup 이후 마우스가 영역을 벗어나며 또 발생하는 pointerleave를 무시하기 위한 가드

        function onDown() {
            isDown = true;
            holding = false;
            holdTimer = setTimeout(function () {
                holding = true;
                pauseAutoAdvance();
            }, STORY_HOLD_THRESHOLD);
        }

        function finish() {
            if (!isDown) return;
            isDown = false;
            clearTimeout(holdTimer);
            if (holding) {
                resumeAutoAdvance();
            } else {
                onTap();
            }
            holding = false;
        }

        el.addEventListener('pointerdown', onDown);
        el.addEventListener('pointerup', finish);
        el.addEventListener('pointerleave', finish);
        el.addEventListener('pointercancel', finish);
    }

    function closeStory() {
        storyViewEl.classList.add('hidden');
        clearAutoTimer();
        storyPaused = false;
        storyCurrentFillEl = null;
        storySlides = [];
        storyIndex = 0;
        storyTripEntries = [];
        storyJumpListEl.classList.add('hidden');
    }

    function closeMapView() {
        mapViewEl.classList.add('hidden');
        globeViewEl.classList.remove('hidden');
        closeStory();
        if (map) { clearRegionMarkers(); clearMapOverlays(); map.setMinZoom(1); }
        window.dispatchEvent(new CustomEvent('mapViewClosed'));
    }

    // ── 이벤트 바인딩 ────────────────────────────────────────
    mapBackBtn.addEventListener('click', closeMapView);
    mapNoRegionBtnEl.addEventListener('click', function () {
        openStory({ countryIsoA3: currentCountryIsoA3, nameKo: currentCountryNameKo });
    });
    storyCloseEl.addEventListener('click', closeStory);
    storyJumpToggleEl.addEventListener('click', openJumpList);
    storyJumpCloseEl.addEventListener('click', closeJumpList);
    bindStoryNavZone(storyNavPrevEl, storyPrev);
    bindStoryNavZone(storyNavNextEl, storyNext);

    document.addEventListener('keydown', function (e) {
        if (storyViewEl.classList.contains('hidden')) return;
        if (e.key === 'Escape') {
            if (!storyJumpListEl.classList.contains('hidden')) closeJumpList();
            else closeStory();
            return;
        }
        if (!storyJumpListEl.classList.contains('hidden')) return; // 목록 보는 중엔 좌우 이동 비활성
        if (e.key === 'ArrowRight') storyNext();
        else if (e.key === 'ArrowLeft') storyPrev();
    });

    mapLangToggleEl.addEventListener('click', function (e) {
        var btn = e.target.closest('.lang-btn');
        if (!btn || btn.classList.contains('active')) return;
        applyLanguage(btn.dataset.lang);
    });

    // 초기 토글 버튼 상태 반영
    mapLangToggleEl.querySelectorAll('.lang-btn').forEach(function (btn) {
        btn.classList.toggle('active', btn.dataset.lang === currentLang);
    });

    window.addEventListener('countrySelected', function (e) {
        var d = e.detail;
        openMapView(d.isoA3, d.nameKo, d.polygons, d.centroid,
            { openRegionId: d.openRegionId, openTripId: d.openTripId }, d.ownerNickname);
    });

    // 페이지 로드 후 유휴 시간에 지도를 미리 초기화 (국가 클릭 시 즉시 표시)
    if (typeof requestIdleCallback === 'function') {
        requestIdleCallback(initMap, { timeout: 3000 });
    } else {
        setTimeout(initMap, 1500);
    }
})();