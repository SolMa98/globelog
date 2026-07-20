(function () {
    // OpenFreeMap — map.js와 동일한 스타일 (API 키 불필요, 완전 무료)
    var GLOBE_STYLE_URL = 'https://tiles.openfreemap.org/styles/positron';

    var COUNTRY_SRC   = 'globe-country-src';
    var FILL_LAYER    = 'globe-country-fill';
    var BORDER_LAYER  = 'globe-country-border';

    var DATA_COLOR     = '#5fa256'; // 여행 기록이 있는 국가
    var HOVER_COLOR    = '#ffd166';
    var SELECTED_COLOR = '#ff6f59';
    var BORDER_COLOR   = '#33312e';

    // ── 언어 설정 (map.js와 동일한 키를 사용해 두 뷰의 언어 선택을 공유) ──
    var LANG_STORAGE_KEY = 'map-lang';
    var currentLang = localStorage.getItem(LANG_STORAGE_KEY) || 'ko';

    // ── DOM 참조 ─────────────────────────────────────────────
    var globeContainerEl = document.getElementById('globe-container');
    var loadingEl        = document.getElementById('loading');
    var hintEl            = document.getElementById('hint');
    var searchInputEl    = document.getElementById('search-input');
    var searchResultsEl  = document.getElementById('search-results');
    var globeLangToggleEl = document.getElementById('globe-lang-toggle');
    var globeOwnerBadgeEl = document.getElementById('globe-owner-badge');

    var globeMap = null;
    var countryInfoByIso = new Map(); // isoA3 -> { isoA3, nameKo, nameEn, polygons, centroid }
    var dataIsoSet = new Set();
    var hoveredIso = null;
    var selectedIso = null;

    // 지구본은 항상 특정 사용자 소유 — URL이 /u/{nickname}/globe 형태라 여기서 뽑는다.
    var ownerNickname = parseOwnerNickname();

    function parseOwnerNickname() {
        var match = window.location.pathname.match(/^\/u\/([^/]+)\/globe/);
        return match ? decodeURIComponent(match[1]) : null;
    }

    init();

    function init() {
        if (!ownerNickname) {
            // /u/{nickname}/globe가 아닌 경로로 이 스크립트가 로드될 일은 없어야
            // 하지만(라우팅이 항상 이 형태로만 서빙), 방어적으로 피드로 돌려보낸다.
            window.location.href = '/';
            return;
        }

        // 모바일(style.css의 반응형 기준과 동일한 640px)에서는 지구본이 화면을 꽉
        // 채워 답답해 보여서 한 단계(zoom -1) 축소된 상태로 시작한다.
        var isMobile = window.matchMedia('(max-width: 640px)').matches;
        var initialZoom = isMobile ? 1.1 : 2.1;

        globeMap = new maplibregl.Map({
            container: globeContainerEl,
            style: GLOBE_STYLE_URL,
            zoom: initialZoom,
            center: [127, 20],
            renderWorldCopies: false,
            attributionControl: false
        });

        // 지도 저작권 표기는 기본적으로 (i) 아이콘으로 접어두고(compact: true 고정),
        // 클릭해서 펼쳤을 때만 전체 문구를 보여준다. 화면 폭에 따라 라이브러리가
        // 알아서 접고 펴는 "auto" 동작에 맡기면 일부 폭에서는 항상 펼쳐진 채로
        // 나와 힌트/계정 위젯과 겹치므로 명시적으로 강제한다.
        globeMap.addControl(new maplibregl.AttributionControl({ compact: true }), 'bottom-right');
        globeMap.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');
        globeMap.on('load', onMapLoad);
        watchAttributionExpanded();
    }

    // 저작권 표기가 접혀있을 땐 그만큼 화면 하단이 비므로 힌트/계정 위젯을
    // 아래로 내려주고, 펼쳐지면 다시 위로 올려 겹치지 않게 한다.
    function watchAttributionExpanded() {
        var attribEl = document.querySelector('.maplibregl-ctrl-attrib');
        if (!attribEl) return;

        var globeViewEl = document.getElementById('globe-view');
        // MapLibre는 compact:true여도 지도 소스가 로드되면서 저작권 문구가
        // 채워지는 시점에 한 번 자동으로 펼쳤다가, 사용자가 지도를 드래그해야
        // 접어주는 게 기본 동작이다(비동기라 addControl 직후에 강제로 접어봐야
        // 소용없음). 우리는 처음부터 접힌 상태로 시작하길 원하므로, 그 최초
        // 1회의 자동 펼침만 감지해서 즉시 다시 접고, 이후 사용자가 직접
        // 클릭해서 펼치는 건 그대로 둔다.
        var forcedInitialCollapse = false;
        var sync = function () {
            globeViewEl.classList.toggle('attrib-expanded', attribEl.classList.contains('maplibregl-compact-show'));
        };
        var observer = new MutationObserver(function () {
            if (!forcedInitialCollapse && attribEl.classList.contains('maplibregl-compact-show')) {
                forcedInitialCollapse = true;
                attribEl.classList.remove('maplibregl-compact-show');
                attribEl.removeAttribute('open');
                return; // 위 두 줄이 또 mutation을 발생시켜 sync가 다시 호출된다
            }
            sync();
        });
        sync();
        observer.observe(attribEl, { attributes: true, attributeFilter: ['class'] });
    }

    // ── 폴리곤 형식 통일 (GeoJSON Polygon/MultiPolygon → 폴리곤 배열) ──
    function toPolygons(geometry) {
        return geometry.type === 'Polygon' ? [geometry.coordinates] : geometry.coordinates;
    }

    function computeCentroid(polygons) {
        var outerRing = polygons[0][0];
        var sum = outerRing.reduce(function (acc, c) { return [acc[0] + c[0], acc[1] + c[1]]; }, [0, 0]);
        return { lon: sum[0] / outerRing.length, lat: sum[1] / outerRing.length };
    }

    function indexCountries(geojson) {
        geojson.features.forEach(function (feature) {
            var isoA3 = feature.properties.iso_a3;
            var polygons = toPolygons(feature.geometry);
            countryInfoByIso.set(isoA3, {
                isoA3: isoA3,
                nameKo: feature.properties.name_ko,
                nameEn: feature.properties.name_en,
                polygons: polygons,
                centroid: computeCentroid(polygons)
            });
        });
    }

    function renderOwnerBadge(me) {
        var isSelf = me.loggedIn && me.nickname === ownerNickname;
        globeOwnerBadgeEl.textContent = isSelf ? '내 지구본' : ownerNickname + '님의 지구본';
        globeOwnerBadgeEl.classList.remove('hidden');
    }

    async function onMapLoad() {
        // MapOptions에는 projection 필드가 없어 생성자 옵션으로는 적용되지 않음.
        // 스타일 로딩이 끝난 뒤(load 이벤트)에 호출해야 "Style is not done loading" 예외 없이 적용됨.
        globeMap.setProjection({ type: 'globe' });

        var results = await Promise.all([
            fetch('/data/countries.geojson').then(function (res) { return res.json(); }),
            fetch('/api/countries?owner=' + encodeURIComponent(ownerNickname))
                .then(function (res) { return res.ok ? res.json() : []; }),
            fetch('/api/me').then(function (res) { return res.json(); })
        ]);
        var geojson = results[0];
        var summaries = results[1];
        var me = results[2];

        renderOwnerBadge(me);
        summaries.forEach(function (summary) { dataIsoSet.add(summary.isoA3); });
        indexCountries(geojson);

        globeMap.addSource(COUNTRY_SRC, {
            type: 'geojson',
            data: geojson,
            promoteId: 'iso_a3'
        });

        globeMap.addLayer({
            id: FILL_LAYER,
            type: 'fill',
            source: COUNTRY_SRC,
            paint: {
                'fill-color': [
                    'case',
                    ['boolean', ['feature-state', 'selected'], false], SELECTED_COLOR,
                    ['boolean', ['feature-state', 'hover'], false], HOVER_COLOR,
                    ['boolean', ['feature-state', 'hasData'], false], DATA_COLOR,
                    'rgba(0,0,0,0)'
                ],
                'fill-opacity': [
                    'case',
                    ['boolean', ['feature-state', 'selected'], false], 0.55,
                    ['boolean', ['feature-state', 'hover'], false], 0.45,
                    ['boolean', ['feature-state', 'hasData'], false], 0.35,
                    0
                ]
            }
        });

        globeMap.addLayer({
            id: BORDER_LAYER,
            type: 'line',
            source: COUNTRY_SRC,
            paint: { 'line-color': BORDER_COLOR, 'line-width': 1, 'line-opacity': 0.6 }
        });

        applyDataTint();
        // globe projection 전환 파이프라인이 load 이후에도 비동기로 계속 초기화되면서
        // 이 시점에 건 text-field 변경이 되돌아가는 경우가 있어, 지도가 완전히 정착하는
        // idle 시점까지 기다렸다가 적용한다.
        globeMap.once('idle', function () { applyLanguage(currentLang); });

        loadingEl.classList.add('hidden');
        bindInteractions();
        bindSearch();
        handleDeepLink();
    }

    // 피드 카드 클릭(feed.js)이 ?tripId=&iso= 쿼리로 넘어온 경우, 도착하자마자 해당
    // 게시글의 스토리를 자동으로 연다 — 그냥 지구본만 보여주면 어떤 글을 눌렀는지 알 수
    // 없어서 "게시글 내용이 안 보인다"는 혼란이 있었음.
    function handleDeepLink() {
        var params = new URLSearchParams(window.location.search);
        var tripId = params.get('tripId');
        var iso = params.get('iso');
        if (!tripId || !iso) return;
        var info = countryInfoByIso.get(iso.toUpperCase());
        if (!info) return;
        focusOnCountry(info, { openTripId: Number(tripId) });
    }

    // ── 언어 적용 (map.js의 applyLanguage와 동일한 방식) ──
    function applyLanguage(lang) {
        currentLang = lang;
        localStorage.setItem(LANG_STORAGE_KEY, lang);

        globeLangToggleEl.querySelectorAll('.lang-btn').forEach(function (btn) {
            btn.classList.toggle('active', btn.dataset.lang === lang);
        });

        if (!globeMap || !globeMap.isStyleLoaded()) return;

        var nameExpr = lang === 'ko'
            ? ['coalesce', ['get', 'name:ko'], ['get', 'name:en'], ['get', 'name']]
            : ['coalesce', ['get', 'name:en'], ['get', 'name']];

        globeMap.getStyle().layers.forEach(function (layer) {
            if (layer.type !== 'symbol') return;
            if (!layer.layout || !('text-field' in layer.layout)) return;
            try { globeMap.setLayoutProperty(layer.id, 'text-field', nameExpr); } catch (e) {}
        });
    }

    function applyDataTint() {
        dataIsoSet.forEach(function (isoA3) {
            if (!countryInfoByIso.has(isoA3)) return;
            globeMap.setFeatureState({ source: COUNTRY_SRC, id: isoA3 }, { hasData: true });
        });
    }

    // ── hover / 선택 상태 ────────────────────────────────────
    function setHover(isoA3) {
        if (isoA3 === hoveredIso) return;
        if (hoveredIso) globeMap.setFeatureState({ source: COUNTRY_SRC, id: hoveredIso }, { hover: false });
        hoveredIso = isoA3;
        if (hoveredIso) globeMap.setFeatureState({ source: COUNTRY_SRC, id: hoveredIso }, { hover: true });
    }

    function setSelected(isoA3) {
        if (selectedIso) globeMap.setFeatureState({ source: COUNTRY_SRC, id: selectedIso }, { selected: false });
        selectedIso = isoA3;
        if (selectedIso) globeMap.setFeatureState({ source: COUNTRY_SRC, id: selectedIso }, { selected: true });
    }

    function selectCountryByIso(isoA3, options) {
        var info = countryInfoByIso.get(isoA3);
        if (!info) return;
        setSelected(isoA3);
        hintEl.classList.add('hidden');
        window.dispatchEvent(new CustomEvent('countrySelected', {
            detail: {
                isoA3: info.isoA3, nameKo: info.nameKo, polygons: info.polygons, centroid: info.centroid,
                ownerNickname: ownerNickname,
                openRegionId: (options && options.openRegionId) || null,
                openTripId: (options && options.openTripId) || null
            }
        }));
    }

    function bindInteractions() {
        globeMap.on('mousemove', FILL_LAYER, function (e) {
            var feature = e.features && e.features[0];
            var isoA3 = feature ? feature.properties.iso_a3 : null;
            setHover(isoA3);
            globeMap.getCanvas().style.cursor = isoA3 ? 'pointer' : '';
        });

        globeMap.on('mouseleave', FILL_LAYER, function () {
            setHover(null);
            globeMap.getCanvas().style.cursor = '';
        });

        globeMap.on('click', FILL_LAYER, function (e) {
            var feature = e.features && e.features[0];
            var isoA3 = feature ? feature.properties.iso_a3 : null;
            if (!isoA3) return;
            selectCountryByIso(isoA3);
        });
    }

    // 지도 뷰에서 돌아올 때 힌트 복원 + 선택 상태 초기화
    window.addEventListener('mapViewClosed', function () {
        hintEl.classList.remove('hidden');
        setSelected(null);
    });

    // ── 검색 ─────────────────────────────────────────────────
    function matchesQuery(info, query) {
        return (info.nameKo && info.nameKo.toLowerCase().includes(query))
            || (info.nameEn && info.nameEn.toLowerCase().includes(query));
    }

    function focusOnCountry(info, options) {
        globeMap.flyTo({ center: [info.centroid.lon, info.centroid.lat], zoom: 3, duration: 700 });
        selectCountryByIso(info.isoA3, options);
    }

    function clearSection(name) {
        searchResultsEl.querySelectorAll('[data-section="' + name + '"]').forEach(function (el) {
            el.remove();
        });
    }

    function updateResultsVisibility() {
        if (searchResultsEl.children.length === 0) {
            searchResultsEl.classList.add('hidden');
        } else {
            searchResultsEl.classList.remove('hidden');
        }
    }

    // ── 국가 검색 (클라이언트 사이드, countries.geojson 기반 — DB 미등록국도 포함) ──
    function renderCountrySection(query) {
        clearSection('country');
        var matches = Array.from(countryInfoByIso.values())
            .filter(function (info) { return matchesQuery(info, query); })
            .slice(0, 8);

        matches.forEach(function (info) {
            var item = document.createElement('li');
            item.dataset.section = 'country';
            if (dataIsoSet.has(info.isoA3)) {
                var dot = document.createElement('span');
                dot.className = 'dot';
                item.appendChild(dot);
            }
            item.appendChild(document.createTextNode(info.nameKo));
            item.addEventListener('click', function () {
                focusOnCountry(info);
                searchInputEl.value = '';
                searchResultsEl.classList.add('hidden');
            });
            searchResultsEl.appendChild(item);
        });
        updateResultsVisibility();
    }

    // ── 지역/여행 검색 (서버 검색, /api/search) ──
    function addSectionHeader(name, label) {
        var header = document.createElement('li');
        header.className = 'search-section-header';
        header.dataset.section = name;
        header.textContent = label;
        searchResultsEl.appendChild(header);
    }

    function renderRegionSection(regions) {
        clearSection('region');
        if (regions.length === 0) return;
        addSectionHeader('region', '지역');
        regions.forEach(function (r) {
            var item = document.createElement('li');
            item.dataset.section = 'region';
            item.appendChild(document.createTextNode(r.nameKo));
            var sub = document.createElement('span');
            sub.className = 'search-item-sub';
            sub.appendChild(document.createTextNode(r.countryNameKo));
            item.appendChild(sub);
            item.addEventListener('click', function () {
                selectCountryByIso(r.countryIsoA3, { openRegionId: r.regionId });
                searchInputEl.value = '';
                searchResultsEl.classList.add('hidden');
            });
            searchResultsEl.appendChild(item);
        });
        updateResultsVisibility();
    }

    function renderTripSection(trips) {
        clearSection('trip');
        if (trips.length === 0) return;
        addSectionHeader('trip', '여행');
        trips.forEach(function (t) {
            var item = document.createElement('li');
            item.dataset.section = 'trip';
            item.appendChild(document.createTextNode(t.title));
            var sub = document.createElement('span');
            sub.className = 'search-item-sub';
            sub.appendChild(document.createTextNode(t.regionNameKo ? t.regionNameKo : t.countryNameKo));
            item.appendChild(sub);
            item.addEventListener('click', function () {
                selectCountryByIso(t.countryIsoA3, { openRegionId: t.regionId, openTripId: t.tripId });
                searchInputEl.value = '';
                searchResultsEl.classList.add('hidden');
            });
            searchResultsEl.appendChild(item);
        });
        updateResultsVisibility();
    }

    var searchDebounceTimer = null;
    var searchRequestSeq = 0;

    function renderSearchResults(query) {
        if (searchDebounceTimer) { clearTimeout(searchDebounceTimer); searchDebounceTimer = null; }

        if (!query) {
            searchResultsEl.innerHTML = '';
            searchResultsEl.classList.add('hidden');
            return;
        }

        renderCountrySection(query);
        // 이전 검색어의 지역/여행 결과 잔상 제거 (새 서버 응답 오기 전까지 비워둠)
        clearSection('region');
        clearSection('trip');
        updateResultsVisibility();

        var mySeq = ++searchRequestSeq;
        searchDebounceTimer = setTimeout(function () {
            fetch('/api/search?q=' + encodeURIComponent(query) + '&owner=' + encodeURIComponent(ownerNickname))
                .then(function (res) { return res.ok ? res.json() : null; })
                .then(function (data) {
                    if (!data || mySeq !== searchRequestSeq) return; // 낡은 응답 무시
                    renderRegionSection(data.regions || []);
                    renderTripSection(data.trips || []);
                })
                .catch(function () {});
        }, 250);
    }

    function bindSearch() {
        searchInputEl.addEventListener('input', function () {
            renderSearchResults(searchInputEl.value.trim().toLowerCase());
        });
    }

    // ── 언어 토글 이벤트 바인딩 ──────────────────────────────
    globeLangToggleEl.addEventListener('click', function (e) {
        var btn = e.target.closest('.lang-btn');
        if (!btn || btn.classList.contains('active')) return;
        applyLanguage(btn.dataset.lang);
    });

    // 초기 토글 버튼 상태 반영 (지도 로드 전에도 즉시 표시)
    globeLangToggleEl.querySelectorAll('.lang-btn').forEach(function (btn) {
        btn.classList.toggle('active', btn.dataset.lang === currentLang);
    });
})();
