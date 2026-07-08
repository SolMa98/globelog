(function () {
    // 테마 초기화 (렌더 전 적용으로 깜빡임 최소화)
    var THEME_KEY = 'admin-theme';
    var html = document.documentElement;

    function applyTheme(theme) {
        html.setAttribute('data-theme', theme);
        var icon = document.getElementById('snb-theme-icon');
        if (icon) icon.textContent = theme === 'dark' ? '🌙' : '☀️';
    }

    applyTheme(localStorage.getItem(THEME_KEY) || 'light');

    var themeBtn = document.getElementById('snb-theme-toggle');
    if (themeBtn) {
        themeBtn.addEventListener('click', function () {
            var next = (html.getAttribute('data-theme') === 'dark') ? 'light' : 'dark';
            localStorage.setItem(THEME_KEY, next);
            applyTheme(next);
        });
    }

    var snb = document.querySelector('.snb');
    if (snb) {
        var items = snb.querySelectorAll('.snb-item[data-menu]');
        var closeBtn = document.getElementById('snb-close');

        items.forEach(function (item) {
            item.querySelector('a').addEventListener('click', function (e) {
                e.preventDefault();
                var menuId = item.dataset.menu;
                var submenu = document.getElementById(menuId);
                var isActive = item.classList.contains('active');

                items.forEach(function (i) { i.classList.remove('active'); });
                snb.querySelectorAll('.snb-submenu').forEach(function (m) { m.classList.remove('active'); });

                if (isActive) {
                    snb.classList.remove('sub-opened');
                } else {
                    item.classList.add('active');
                    if (submenu) submenu.classList.add('active');
                    snb.classList.add('sub-opened');
                }
            });
        });

        if (closeBtn) {
            closeBtn.addEventListener('click', closePanel);
        }

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && snb.classList.contains('sub-opened')) {
                closePanel();
            }
        });

        function closePanel() {
            snb.classList.remove('sub-opened');
            items.forEach(function (i) { i.classList.remove('active'); });
            snb.querySelectorAll('.snb-submenu').forEach(function (m) { m.classList.remove('active'); });
        }
    }

    document.querySelectorAll('[data-page-size-input]').forEach(function (input) {
        function applySize() {
            var val = parseInt(input.value, 10);
            if (!val || val < 1) return;
            var url = new URL(window.location.href);
            url.searchParams.set('size', val);
            url.searchParams.set('page', '0');
            window.location.href = url.toString();
        }
        input.addEventListener('change', applySize);
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') applySize();
        });
    });

    document.querySelectorAll('[data-page-size-dec]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var input = btn.closest('.page-size-wrap').querySelector('[data-page-size-input]');
            var val = parseInt(input.value, 10);
            var min = parseInt(input.min, 10) || 5;
            if (val > min) {
                input.value = val - 1;
                input.dispatchEvent(new Event('change'));
            }
        });
    });

    document.querySelectorAll('[data-page-size-inc]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var input = btn.closest('.page-size-wrap').querySelector('[data-page-size-input]');
            var val = parseInt(input.value, 10);
            var max = parseInt(input.max, 10) || 200;
            if (val < max) {
                input.value = val + 1;
                input.dispatchEvent(new Event('change'));
            }
        });
    });
})();