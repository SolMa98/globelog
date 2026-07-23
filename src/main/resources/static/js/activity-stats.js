// /my/stats(개인)와 /admin/stats(전체 합산)가 공유하는 "활동 통계"/"파일 저장 용량"
// 타일 렌더러. stats-charts.js(차트 3종)와 달리 숫자만 표시하는 카드라 Chart.js 없이 끝난다.
window.GlobelogActivityStats = (function () {
    function formatBytes(bytes) {
        if (bytes < 1024) return bytes + ' B';
        var units = ['KB', 'MB', 'GB', 'TB'];
        var value = bytes;
        for (var i = 0; i < units.length; i++) {
            value /= 1024;
            if (value < 1024 || i === units.length - 1) {
                return value.toFixed(1) + ' ' + units[i];
            }
        }
        return value.toFixed(1) + ' ' + units[units.length - 1];
    }

    function initActivity(dataUrl) {
        fetch(dataUrl)
            .then(function (res) { return res.json(); })
            .then(function (data) {
                document.getElementById('activity-view-count').textContent = data.viewCount.toLocaleString();
                document.getElementById('activity-chat-count').textContent = data.chatMessageCount.toLocaleString();
                document.getElementById('activity-trip-create').textContent = data.tripCreateCount.toLocaleString();
                document.getElementById('activity-trip-update').textContent = data.tripUpdateCount.toLocaleString();
                document.getElementById('activity-trip-delete').textContent = data.tripDeleteCount.toLocaleString();
            })
            .catch(function () {});
    }

    function initStorage(dataUrl) {
        fetch(dataUrl)
            .then(function (res) { return res.json(); })
            .then(function (data) {
                document.getElementById('storage-permanent').textContent = formatBytes(data.permanentBytes);
                document.getElementById('storage-permanent-sub').textContent = data.permanentCount.toLocaleString() + '개 파일';
                document.getElementById('storage-chat').textContent = formatBytes(data.chatAttachmentBytes);
                document.getElementById('storage-chat-sub').textContent = data.chatAttachmentCount.toLocaleString() + '개 파일 · 3개월 후 자동 삭제';
                document.getElementById('storage-expiring').textContent = formatBytes(data.expiringSoonBytes);
                document.getElementById('storage-expiring-sub').textContent = data.expiringSoonCount.toLocaleString() + '개 파일 · 7일 내 삭제 예정';
            })
            .catch(function () {});
    }

    return { initActivity: initActivity, initStorage: initStorage };
})();
