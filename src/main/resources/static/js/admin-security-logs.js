document.addEventListener('DOMContentLoaded', function () {
    var exportModal = document.getElementById('export-modal');
    var openExportBtn = document.getElementById('open-export-modal');

    // 다운로드 폼은 fetch가 아니라 일반 제출로 둔다 — 응답이 Content-Disposition:
    // attachment라 브라우저가 페이지 이동 없이 파일만 내려받는다(admin-modal.js의
    // JSON 기반 fetch 패턴과 다른 이유).
    if (openExportBtn && exportModal) {
        openExportBtn.addEventListener('click', function () {
            exportModal.querySelector('form').reset();
            AdminModal.open(exportModal);
        });
    }
});
