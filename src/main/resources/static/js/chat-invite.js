document.addEventListener('DOMContentLoaded', function () {
    var roomId = Number(document.getElementById('chat-invite-room-id').value);
    var submitBtn = document.getElementById('chat-invite-submit');
    var picker = null;

    function jsonFetch(url, options) {
        return GlobelogCsrf.fetch().then(function (auth) {
            var headers = Object.assign({ 'Content-Type': 'application/json' }, (options && options.headers) || {});
            if (auth) headers[auth.headerName] = auth.token;
            return fetch(url, Object.assign({}, options, { headers: headers }));
        });
    }

    function readErrorMessage(res, fallback) {
        return res.json().then(function (data) { return data.message || fallback; }).catch(function () { return fallback; });
    }

    // 이미 참여 중인 멤버는 골라도 어차피 서버가 거부하니, 애초에 목록에서 빼서 보여준다.
    fetch('/api/chat/rooms/' + roomId).then(function (res) { return res.json(); }).then(function (detail) {
        var existingIds = detail.members.map(function (m) { return m.userId; });
        picker = ChatUserPicker.create(
            document.getElementById('chat-invite-search'),
            document.getElementById('chat-invite-list'),
            document.getElementById('chat-invite-list-label'),
            false,
            null,
            existingIds
        );
    }).catch(function () { AdminModal.showError('대화방 정보를 불러오지 못했습니다.'); });

    submitBtn.addEventListener('click', function () {
        var target = picker && picker.getSelected();
        if (!target) { AdminModal.showError('초대할 사람을 선택해주세요.'); return; }
        jsonFetch('/api/chat/rooms/' + roomId + '/invite', {
            method: 'POST',
            body: JSON.stringify({ nickname: target.nickname })
        }).then(function (res) {
            if (res.ok) {
                AdminModal.showSuccess('초대했습니다.', function () { window.location.href = '/my/chat/' + roomId; });
            } else {
                return readErrorMessage(res, '초대에 실패했습니다.').then(function (msg) { AdminModal.showError(msg); });
            }
        }).catch(function () { AdminModal.showError(); });
    });
});
