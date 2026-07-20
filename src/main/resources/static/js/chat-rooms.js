document.addEventListener('DOMContentLoaded', function () {
    var listEl = document.getElementById('chat-room-list');
    var emptyEl = document.getElementById('chat-room-empty');
    var createModal = document.getElementById('chat-create-modal');
    var openCreateBtn = document.getElementById('open-chat-create');
    var createSubmitBtn = document.getElementById('chat-create-submit');
    var currentTab = 'direct';

    function jsonFetch(url, options) {
        return GlobelogCsrf.fetch().then(function (auth) {
            var headers = Object.assign({ 'Content-Type': 'application/json' }, (options && options.headers) || {});
            if (auth) headers[auth.headerName] = auth.token;
            return fetch(url, Object.assign({}, options, { headers: headers }));
        });
    }

    // 알림 배너 — 아직 권한을 묻지도, 거부하지도 않은 상태(default)일 때만 보여준다.
    // 이미 허용/거부한 사용자에게 매번 다시 권하면 성가시기만 하다.
    var pushBanner = document.getElementById('chat-push-banner');
    var pushEnableBtn = document.getElementById('chat-push-enable-btn');
    if (pushBanner && window.GlobelogPush && GlobelogPush.isSupported() && GlobelogPush.permission() === 'default') {
        pushBanner.style.display = '';
    }
    if (pushEnableBtn) {
        pushEnableBtn.addEventListener('click', function () {
            GlobelogPush.subscribe().then(function (ok) {
                if (ok) {
                    pushBanner.style.display = 'none';
                    Swal.fire({ icon: 'success', title: '알림이 켜졌습니다.', timer: 1200, showConfirmButton: false });
                } else {
                    AdminModal.showError('알림 권한이 거부되었거나 지원되지 않는 브라우저입니다.');
                }
            });
        });
    }

    function loadRooms() {
        fetch('/api/chat/rooms')
            .then(function (res) { return res.json(); })
            .then(renderRooms)
            .catch(function () {});
    }

    function renderRooms(rooms) {
        listEl.innerHTML = '';
        emptyEl.style.display = rooms.length ? 'none' : '';
        rooms.forEach(function (room) {
            listEl.appendChild(buildRoomItem(room));
        });
    }

    function buildRoomItem(room) {
        var li = document.createElement('li');
        var link = document.createElement('a');
        link.className = 'chat-room-item';
        link.href = '/my/chat/' + room.id;

        var avatar = document.createElement('div');
        avatar.className = 'chat-room-avatar';
        if (room.displayImageUrl) {
            avatar.style.backgroundImage = 'url("' + room.displayImageUrl + '")';
        } else {
            avatar.textContent = (room.displayName || '?').charAt(0).toUpperCase();
        }
        link.appendChild(avatar);

        var body = document.createElement('div');
        body.className = 'chat-room-body';
        var name = document.createElement('div');
        name.className = 'chat-room-name';
        name.textContent = room.displayName;
        var preview = document.createElement('div');
        preview.className = 'chat-room-preview';
        preview.textContent = room.lastMessagePreview || '아직 메시지가 없습니다.';
        body.appendChild(name);
        body.appendChild(preview);
        link.appendChild(body);

        var meta = document.createElement('div');
        meta.className = 'chat-room-meta';
        if (room.lastMessageAt) {
            var time = document.createElement('div');
            time.className = 'chat-room-time';
            time.textContent = formatTime(room.lastMessageAt);
            meta.appendChild(time);
        }
        if (room.unreadCount > 0) {
            var badge = document.createElement('span');
            badge.className = 'chat-unread-badge';
            badge.textContent = room.unreadCount > 99 ? '99+' : room.unreadCount;
            meta.appendChild(badge);
        }
        link.appendChild(meta);

        li.appendChild(link);
        return li;
    }

    function formatTime(iso) {
        var d = new Date(iso);
        var now = new Date();
        if (d.toDateString() === now.toDateString()) {
            return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0');
        }
        return (d.getMonth() + 1) + '.' + d.getDate();
    }

    if (openCreateBtn) {
        openCreateBtn.addEventListener('click', function () {
            document.getElementById('chat-direct-nickname').value = '';
            document.getElementById('chat-group-name').value = '';
            document.getElementById('chat-group-members').value = '';
            AdminModal.open(createModal);
        });
    }

    createModal.querySelectorAll('.chat-create-tab').forEach(function (tab) {
        tab.addEventListener('click', function () {
            currentTab = tab.dataset.tab;
            createModal.querySelectorAll('.chat-create-tab').forEach(function (t) { t.classList.toggle('active', t === tab); });
            createModal.querySelectorAll('.chat-create-panel').forEach(function (panel) {
                panel.style.display = panel.dataset.panel === currentTab ? '' : 'none';
            });
        });
    });

    createSubmitBtn.addEventListener('click', function () {
        if (currentTab === 'direct') {
            var nickname = document.getElementById('chat-direct-nickname').value.trim();
            if (!nickname) { AdminModal.showError('닉네임을 입력해주세요.'); return; }
            openRoom(jsonFetch('/api/chat/rooms/direct', { method: 'POST', body: JSON.stringify({ nickname: nickname }) }));
        } else if (currentTab === 'group') {
            var name = document.getElementById('chat-group-name').value.trim();
            if (!name) { AdminModal.showError('방 이름을 입력해주세요.'); return; }
            var members = document.getElementById('chat-group-members').value
                .split(',').map(function (s) { return s.trim(); }).filter(Boolean);
            openRoom(jsonFetch('/api/chat/rooms/group', { method: 'POST', body: JSON.stringify({ name: name, memberNicknames: members }) }));
        } else {
            openRoom(jsonFetch('/api/chat/rooms/self', { method: 'POST' }));
        }
    });

    function openRoom(fetchPromise) {
        fetchPromise
            .then(function (res) { return res.json().then(function (data) { return { ok: res.ok, data: data }; }); })
            .then(function (result) {
                if (result.ok) {
                    window.location.href = '/my/chat/' + result.data.id;
                } else {
                    AdminModal.showError(result.data.message || '대화를 시작하지 못했습니다.');
                }
            })
            .catch(function () { AdminModal.showError(); });
    }

    loadRooms();
});
