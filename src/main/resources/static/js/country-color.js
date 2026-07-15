// 국가 배지에 국가별로 서로 다른 색을 입히기 위한 공용 유틸.
// 국가마다 전용 색을 미리 지정하지 않고, isoA3 코드를 해시해 고정 팔레트에서
// 하나를 고른다 — 같은 국가는 항상 같은 색이 나오고(해시가 결정적), 236개국을
// 다 등록/유지보수할 필요가 없다.
window.GlobelogCountryColor = (function () {
    var PALETTE = [
        { text: '#8fe0f0', bg: 'rgba(79, 195, 222, 0.15)' },
        { text: '#f0c98f', bg: 'rgba(240, 201, 143, 0.15)' },
        { text: '#a8e08f', bg: 'rgba(168, 224, 143, 0.15)' },
        { text: '#e0a8f0', bg: 'rgba(224, 168, 240, 0.15)' },
        { text: '#f0a8a8', bg: 'rgba(240, 168, 168, 0.15)' },
        { text: '#a8c5f0', bg: 'rgba(168, 197, 240, 0.15)' }
    ];

    function colorFor(key) {
        var hash = 0;
        for (var i = 0; i < key.length; i++) {
            hash = (hash * 31 + key.charCodeAt(i)) >>> 0;
        }
        return PALETTE[hash % PALETTE.length];
    }

    // data-iso 속성이 있는 요소들을 찾아 국가별 배지 색을 입힌다.
    function applyBadges(rootEl, selector) {
        (rootEl || document).querySelectorAll(selector).forEach(function (el) {
            var iso = el.dataset.iso;
            if (!iso) return;
            var color = colorFor(iso);
            el.style.color = color.text;
            el.style.background = color.bg;
        });
    }

    return { colorFor: colorFor, applyBadges: applyBadges };
})();
