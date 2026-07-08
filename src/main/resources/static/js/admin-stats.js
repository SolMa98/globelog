(function () {
    var CONTINENT_LABELS = {
        ASIA: '아시아',
        EUROPE: '유럽',
        AFRICA: '아프리카',
        NORTH_AMERICA: '북아메리카',
        SOUTH_AMERICA: '남아메리카',
        OCEANIA: '오세아니아',
        ANTARCTICA: '남극'
    };

    // admin.css [data-theme] 변수의 실제 값 (Chart.js는 CSS 변수를 직접 못 읽으므로 하드코딩)
    var PALETTE = {
        light: { accent: '#2f6feb', border: '#e4e7f0', success: '#1ea672', text: '#7b8195', grid: '#e4e7f0' },
        dark: { accent: '#5b8bf5', border: '#333f5c', success: '#2fbf85', text: '#8892a4', grid: 'rgba(255,255,255,0.09)' }
    };

    var charts = [];
    var lastData = null;

    function currentTheme() {
        return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    }

    function destroyCharts() {
        charts.forEach(function (c) { c.destroy(); });
        charts = [];
    }

    function buildCharts(data) {
        destroyCharts();
        var p = PALETTE[currentTheme()];
        var textOption = { color: p.text };
        var gridOption = { color: p.grid };

        charts.push(new Chart(document.getElementById('chart-visited'), {
            type: 'doughnut',
            data: {
                labels: ['방문', '미방문'],
                datasets: [{
                    data: [data.visitedCountryCount, data.totalCountryCount - data.visitedCountryCount],
                    backgroundColor: [p.accent, p.border]
                }]
            },
            options: {
                maintainAspectRatio: false,
                plugins: { legend: { position: 'bottom', labels: { boxWidth: 12, padding: 8, color: p.text } } }
            }
        }));

        charts.push(new Chart(document.getElementById('chart-continent'), {
            type: 'bar',
            data: {
                labels: data.continents.map(function (c) { return CONTINENT_LABELS[c.continent] || c.continent; }),
                datasets: [
                    { label: '방문', data: data.continents.map(function (c) { return c.visited; }), backgroundColor: p.accent },
                    { label: '전체', data: data.continents.map(function (c) { return c.total; }), backgroundColor: p.border }
                ]
            },
            options: {
                maintainAspectRatio: false,
                plugins: { legend: { labels: { color: p.text } } },
                scales: {
                    x: { ticks: textOption, grid: gridOption },
                    y: { beginAtZero: true, ticks: Object.assign({ precision: 0 }, textOption), grid: gridOption }
                }
            }
        }));

        charts.push(new Chart(document.getElementById('chart-yearly'), {
            type: 'bar',
            data: {
                labels: data.yearly.map(function (y) { return y.year; }),
                datasets: [{ label: '여행 횟수', data: data.yearly.map(function (y) { return y.count; }), backgroundColor: p.success }]
            },
            options: {
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                    x: { ticks: textOption, grid: gridOption },
                    y: { beginAtZero: true, ticks: Object.assign({ precision: 0 }, textOption), grid: gridOption }
                }
            }
        }));
    }

    function render(data) {
        lastData = data;
        document.getElementById('stat-visited-count').textContent = data.visitedCountryCount;
        var pct = data.totalCountryCount > 0
            ? Math.round(data.visitedCountryCount / data.totalCountryCount * 100) : 0;
        document.getElementById('stat-visited-sub').textContent =
            data.visitedCountryCount + ' / ' + data.totalCountryCount + '개국 (' + pct + '%)';
        buildCharts(data);
    }

    fetch('/admin/stats/data')
        .then(function (res) { return res.json(); })
        .then(render)
        .catch(function () {});

    // 테마 토글(admin.js가 <html data-theme>을 바꿈) 시 차트 색상을 다시 그린다.
    new MutationObserver(function () {
        if (lastData) buildCharts(lastData);
    }).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
})();
