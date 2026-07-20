document.addEventListener('DOMContentLoaded', function () {
    var roomId = Number(document.getElementById('chat-room-id').value);
    var messagesEl = document.getElementById('chat-messages');
    var titleEl = document.getElementById('chat-room-title');
    var inviteBtn = document.getElementById('chat-invite-btn');
    var leaveBtn = document.getElementById('chat-leave-btn');
    var sendForm = document.getElementById('chat-send-form');
    var textInput = document.getElementById('chat-text-input');
    var fileInput = document.getElementById('chat-file-input');
    var inviteModal = document.getElementById('chat-invite-modal');
    var inviteInput = document.getElementById('chat-invite-nickname');
    var inviteSubmitBtn = document.getElementById('chat-invite-submit');

    var myUserId = null;
    var oldestId = null;
    var loadingOlder = false;
    var reachedStart = false;

    function jsonFetch(url, options) {
        return GlobelogCsrf.fetch().then(function (auth) {
            var headers = Object.assign({}, (options && options.headers) || {});
            if (auth) headers[auth.headerName] = auth.token;
            return fetch(url, Object.assign({}, options, { headers: headers }));
        });
    }

    function readErrorMessage(res, fallback) {
        return res.json().then(function (data) { return data.message || fallback; }).catch(function () { return fallback; });
    }

    function formatFileSize(bytes) {
        if (bytes == null) return '';
        if (bytes < 1024) return bytes + 'B';
        if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + 'KB';
        return (bytes / 1024 / 1024).toFixed(1) + 'MB';
    }

    function formatTime(iso) {
        var d = new Date(iso);
        return d.getHours().toString().padStart(2, '0') + ':' + d.getMinutes().toString().padStart(2, '0');
    }

    // 서버가 broadcast로 실어보내는 메시지는 "누가 보냈든 그 발신자 기준"으로 만든 것이라
    // self 필드를 그대로 믿을 수 없다(모든 구독자에게 같은 페이로드가 나가므로). 그래서
    // 화면에서는 항상 senderId를 내 user id와 직접 비교해서 판정한다.
    function buildBubble(message) {
        var row = document.createElement('div');
        var mine = message.senderId === myUserId;
        row.className = 'chat-bubble-row' + (mine ? ' mine' : '');
        row.dataset.messageId = message.id;

        if (!mine) {
            var sender = document.createElement('div');
            sender.className = 'chat-bubble-sender';
            sender.textContent = message.senderNickname;
            row.appendChild(sender);
        }

        var bubble = document.createElement('div');
        bubble.className = 'chat-bubble';
        if (message.type === 'FILE') {
            bubble.classList.add('chat-bubble-file');
            if (message.fileExpired) {
                bubble.textContent = '📎 ' + (message.originalFilename || '파일') + ' (보관기간이 지나 삭제됨)';
            } else {
                var icon = document.createElement('span');
                icon.textContent = '📎';
                var link = document.createElement('a');
                link.href = message.fileUrl;
                link.target = '_blank';
                link.rel = 'noopener';
                link.textContent = message.originalFilename + (message.fileSize ? ' (' + formatFileSize(message.fileSize) + ')' : '');
                bubble.appendChild(icon);
                bubble.appendChild(link);
            }
        } else {
            bubble.textContent = message.content;
        }
        row.appendChild(bubble);

        var time = document.createElement('div');
        time.className = 'chat-bubble-time';
        time.textContent = formatTime(message.createdAt);
        row.appendChild(time);

        return row;
    }

    function appendMessage(message) {
        messagesEl.appendChild(buildBubble(message));
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function prependMessages(messages) {
        var prevHeight = messagesEl.scrollHeight;
        var fragment = document.createDocumentFragment();
        messages.forEach(function (m) { fragment.appendChild(buildBubble(m)); });
        messagesEl.insertBefore(fragment, messagesEl.firstChild);
        messagesEl.scrollTop = messagesEl.scrollHeight - prevHeight;
    }

    // 서버는 항상 최신순으로 내려주므로, 화면엔 오래된 것부터 보이게 뒤집어서 그린다.
    function loadHistory(beforeId) {
        var url = '/api/chat/rooms/' + roomId + '/messages' + (beforeId ? ('?beforeId=' + beforeId) : '');
        return fetch(url).then(function (res) { return res.json(); }).then(function (messages) {
            if (messages.length > 0) oldestId = messages[messages.length - 1].id;
            if (messages.length < 30) reachedStart = true;
            var ordered = messages.slice().reverse();
            if (beforeId) {
                prependMessages(ordered);
            } else {
                ordered.forEach(appendMessage);
            }
        });
    }

    messagesEl.addEventListener('scroll', function () {
        if (messagesEl.scrollTop < 40 && !loadingOlder && !reachedStart && oldestId) {
            loadingOlder = true;
            loadHistory(oldestId).finally(function () { loadingOlder = false; });
        }
    });

    function loadDetail() {
        return fetch('/api/chat/rooms/' + roomId).then(function (res) { return res.json(); }).then(function (detail) {
            titleEl.textContent = detail.displayName;
            if (detail.type === 'GROUP') {
                inviteBtn.style.display = '';
                leaveBtn.style.display = '';
            }
        });
    }

    function markRead() {
        jsonFetch('/api/chat/rooms/' + roomId + '/read', { method: 'POST' }).catch(function () {});
    }

    var stompClient = new StompJs.Client({
        brokerURL: (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws-chat',
        reconnectDelay: 3000
    });
    stompClient.onConnect = function () {
        stompClient.subscribe('/topic/chat.' + roomId, function (frame) {
            appendMessage(JSON.parse(frame.body));
            markRead();
        });
    };
    stompClient.activate();

    sendForm.addEventListener('submit', function (e) {
        e.preventDefault();
        var content = textInput.value.trim();
        if (!content || !stompClient.connected) return;
        textInput.value = '';
        stompClient.publish({ destination: '/app/chat.send', body: JSON.stringify({ roomId: roomId, content: content }) });
    });

    fileInput.addEventListener('change', function () {
        var file = fileInput.files[0];
        if (!file) return;
        var body = new FormData();
        body.append('file', file);
        jsonFetch('/api/chat/rooms/' + roomId + '/messages/file', { method: 'POST', body: body })
            .then(function (res) {
                fileInput.value = '';
                if (!res.ok) return readErrorMessage(res, '파일 전송에 실패했습니다.').then(function (msg) { AdminModal.showError(msg); });
            })
            .catch(function () { AdminModal.showError(); });
    });

    if (inviteBtn) {
        inviteBtn.addEventListener('click', function () {
            inviteInput.value = '';
            AdminModal.open(inviteModal);
        });
    }
    inviteSubmitBtn.addEventListener('click', function () {
        var nickname = inviteInput.value.trim();
        if (!nickname) { AdminModal.showError('닉네임을 입력해주세요.'); return; }
        jsonFetch('/api/chat/rooms/' + roomId + '/invite', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nickname: nickname })
        }).then(function (res) {
            if (res.ok) {
                AdminModal.close(inviteModal);
                Swal.fire({ icon: 'success', title: '초대했습니다.', timer: 1200, showConfirmButton: false });
            } else {
                return readErrorMessage(res, '초대에 실패했습니다.').then(function (msg) { AdminModal.showError(msg); });
            }
        }).catch(function () { AdminModal.showError(); });
    });

    if (leaveBtn) {
        leaveBtn.addEventListener('click', function () {
            AdminModal.confirmDanger('채팅방을 나갈까요?').then(function (confirmed) {
                if (!confirmed) return;
                jsonFetch('/api/chat/rooms/' + roomId + '/leave', { method: 'DELETE' }).then(function (res) {
                    if (res.ok) window.location.href = '/my/chat';
                    else AdminModal.showError('나가기에 실패했습니다.');
                }).catch(function () { AdminModal.showError(); });
            });
        });
    }

    fetch('/api/me')
        .then(function (res) { return res.json(); })
        .then(function (me) {
            myUserId = me.id;
            return Promise.all([loadDetail(), loadHistory(null)]);
        })
        .then(markRead)
        .catch(function () {});
});
