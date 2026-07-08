(function () {
    var map = null;
    var regionMarkers = [];
    var currentOwnerNickname = null; // 지금 보고 있는 지구본의 소유자 — country/region/trip API 호출마다 실어보냄

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
        globeViewEl.classList.add('hidden');
        mapViewEl.classList.remove('hidden');

        initMap();

        async function onReady() {
            setTimeout(function () { map.resize(); }, 50);
            mapCountryNameEl.textContent = nameKo;
            mapFlagEl.style.display = 'none';

            clearRegionMarkers();
            clearMapOverlays();
            closeStory();

            fitCountryBounds(polygons, centroid);
            addCountryMask(polygons);
            addCountryBoundary(polygons);

            loadCountryDetail(isoA3);

            var regions = await loadRegionMarkers(isoA3);
            if (openOptions && openOptions.openRegionId) {
                var target = regions.find(function (r) { return r.id === openOptions.openRegionId; });
                if (target) {
                    await openStory(target, openOptions.openTripId);
                }
            }
        }

        if (map.isStyleLoaded()) {
            onReady();
        } else {
            map.once('load', onReady);
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

    async function loadRegionMarkers(isoA3) {
        try {
            var res = await fetch('/api/countries/' + isoA3 + '/regions?owner=' + encodeURIComponent(currentOwnerNickname));
            if (!res.ok) return [];
            var regions = await res.json();
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
    // 지역의 방문 기록을 인스타그램 스토리처럼 사진 중심 풀스크린 슬라이드로 보여준다.
    // 사진이 여러 장인 방문은 사진 수만큼 슬라이드로 쪼개고(각 슬라이드에 같은 설명을
    // 반복 표시), 사진이 없는 방문은 그라디언트 배경의 슬라이드 1장으로 대체한다.
    async function openStory(region, focusTripId) {
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
            var listRes = await fetch('/api/regions/' + region.id + '/trips?' + ownerParam);
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
                var firstSlideIndex = storySlides.length;
                if (images.length === 0) {
                    storySlides.push(buildSlide(region, trip, description, null, 1, 1));
                } else {
                    images.forEach(function (img, photoIndex) {
                        storySlides.push(buildSlide(region, trip, description, img.url, photoIndex + 1, images.length));
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

    function buildSlide(region, trip, description, imageUrl, photoIndex, photoCount) {
        return {
            tripId: trip.id,
            regionNameKo: region.nameKo,
            visitNumber: trip.visitNumber,
            title: trip.title,
            visitedDate: trip.visitedDate,
            description: description,
            imageUrl: imageUrl,
            photoIndex: photoIndex,
            photoCount: photoCount
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
        startAutoAdvance();

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
        storyDateEl.textContent = slide.visitedDate || '';
        storyDescriptionEl.textContent = slide.description;
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