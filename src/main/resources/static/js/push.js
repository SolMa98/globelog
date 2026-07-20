// 채팅 새 메시지 브라우저/OS 알림 구독. 페이지 로드 시 자동으로 권한을 묻지 않고,
// 사용자가 명시적으로 버튼을 눌렀을 때만 Notification.requestPermission()을 호출한다
// (브라우저들이 자동 권한요청 UX를 나쁘게 취급해서 거의 다 차단/무시하기 때문).
window.GlobelogPush = (function () {
    function urlBase64ToUint8Array(base64String) {
        var padding = '='.repeat((4 - base64String.length % 4) % 4);
        var base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
        var rawData = atob(base64);
        var outputArray = new Uint8Array(rawData.length);
        for (var i = 0; i < rawData.length; i++) outputArray[i] = rawData.charCodeAt(i);
        return outputArray;
    }

    function isSupported() {
        return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
    }

    function permission() {
        return isSupported() ? Notification.permission : 'unsupported';
    }

    function sendSubscriptionToServer(subscription) {
        return GlobelogCsrf.fetch().then(function (auth) {
            var headers = { 'Content-Type': 'application/json' };
            if (auth) headers[auth.headerName] = auth.token;
            return fetch('/api/push/subscribe', { method: 'POST', headers: headers, body: JSON.stringify(subscription.toJSON()) });
        });
    }

    function subscribe() {
        if (!isSupported()) return Promise.resolve(false);
        return Notification.requestPermission().then(function (perm) {
            if (perm !== 'granted') return false;
            return navigator.serviceWorker.ready.then(function (registration) {
                return fetch('/api/push/vapid-public-key')
                    .then(function (res) { return res.text(); })
                    .then(function (publicKey) {
                        if (!publicKey) return false; // 서버에 VAPID 키가 아직 설정 안 된 상태
                        return registration.pushManager.subscribe({
                            userVisibleOnly: true,
                            applicationServerKey: urlBase64ToUint8Array(publicKey)
                        }).then(function (subscription) {
                            return sendSubscriptionToServer(subscription).then(function () { return true; });
                        });
                    });
            });
        }).catch(function () { return false; });
    }

    return { isSupported: isSupported, permission: permission, subscribe: subscribe };
})();
