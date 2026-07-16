// /api/me 응답에서 CSRF 토큰(+로그인 여부)을 읽어오는 공용 헬퍼. feed.js/map.js가 각자
// fetch('/api/me')를 따로 구현하던 걸 하나로 모았다. 결과를 프라미스로 캐싱해 같은
// 페이지에서 여러 번 불러도 실제 네트워크 요청은 한 번만 나가고, 실패하면 캐시를
// 비워서 다음 호출이 다시 시도할 수 있게 한다(첫 시도가 실패하면 이후 영영 못 쓰게
// 되는 걸 막기 위함).
//
// 쿠키(XSRF-TOKEN)에 든 원본 토큰 값을 그대로 헤더에 넣으면 안 된다 — Spring Security 6
// 기본 핸들러(XorCsrfTokenRequestAttributeHandler)는 응답 바디로 내려준 "마스킹된" 토큰만
// 유효하게 검증한다(쿠키 원본 값은 403). 그래서 이 응답값을 꼭 써야 한다.
window.GlobelogCsrf = (function () {
    var promise = null;

    function fetchCsrf() {
        if (!promise) {
            promise = fetch('/api/me')
                .then(function (res) { return res.json(); })
                .then(function (me) {
                    return { headerName: me.csrfHeaderName, token: me.csrfToken, loggedIn: me.loggedIn };
                })
                .catch(function () { promise = null; return null; });
        }
        return promise;
    }

    return { fetch: fetchCsrf };
})();
