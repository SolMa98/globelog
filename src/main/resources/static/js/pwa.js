// 홈 화면 설치 + 오프라인 앱 셸 캐싱용 서비스워커 등록. 실패해도(구형 브라우저 등)
// 앱 동작 자체엔 영향 없어 조용히 무시한다.
if ('serviceWorker' in navigator) {
    window.addEventListener('load', function () {
        navigator.serviceWorker.register('/service-worker.js').catch(function () {});
    });
}
