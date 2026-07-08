(function () {
    // admin.js와 동일한 localStorage 키를 써서, 관리자/공개 화면 어디서 바꾸든
    // 테마 설정이 사이트 전체에서 하나로 유지되게 한다.
    var THEME_KEY = 'admin-theme';
    var html = document.documentElement;

    function applyTheme(theme) {
        html.setAttribute('data-theme', theme);
        var icon = document.getElementById('theme-toggle-icon');
        if (icon) icon.textContent = theme === 'dark' ? '🌙' : '☀️';
    }

    applyTheme(localStorage.getItem(THEME_KEY) || 'light');

    var btn = document.getElementById('theme-toggle-btn');
    if (btn) {
        btn.addEventListener('click', function () {
            var next = (html.getAttribute('data-theme') === 'dark') ? 'light' : 'dark';
            localStorage.setItem(THEME_KEY, next);
            applyTheme(next);
        });
    }
})();
