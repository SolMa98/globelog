document.addEventListener('DOMContentLoaded', function () {
    var roomId = Number(document.getElementById('chat-room-id').value);
    var messagesEl = document.getElementById('chat-messages');
    var titleEl = document.getElementById('chat-room-title');
    var avatarEl = document.getElementById('chat-room-avatar');
    var inviteBtn = document.getElementById('chat-invite-btn');
    var leaveBtn = document.getElementById('chat-leave-btn');
    var sendForm = document.getElementById('chat-send-form');
    var textInput = document.getElementById('chat-text-input');
    var fileInput = document.getElementById('chat-file-input');
    var inviteModal = document.getElementById('chat-invite-modal');
    var inviteInput = document.getElementById('chat-invite-nickname');
    var inviteSubmitBtn = document.getElementById('chat-invite-submit');

    var myUserId = null;
    var myProfileImageUrl = null;
    var roomType = null;
    var oldestId = null;
    var loadingOlder = false;
    var reachedStart = false;

    // 연속된 메시지가 같은 발신자로부터 5분 이내에 온 것이면 한 그룹으로 묶어
    // 붙여보이게 한다(인스타그램 DM 스타일) — 매번 이름/여백을 반복하지 않는다.
    var GROUP_WINDOW_MS = 5 * 60 * 1000;

    function isGroupedWithPrev(prevRow, message) {
        if (!prevRow) return false;
        if (Number(prevRow.dataset.senderId) !== message.senderId) return false;
        var prevTime = new Date(prevRow.dataset.createdAt).getTime();
        var curTime = new Date(message.createdAt).getTime();
        return Math.abs(curTime - prevTime) < GROUP_WINDOW_MS;
    }

    function renderAvatarEl(el, url, fallbackText) {
        if (url) {
            el.style.backgroundImage = 'url("' + url + '")';
            el.textContent = '';
        } else {
            el.style.backgroundImage = '';
            el.textContent = (fallbackText || '?').charAt(0).toUpperCase();
        }
    }

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
    function buildBubble(message, groupedWithPrev) {
        var row = document.createElement('div');
        var mine = message.senderId === myUserId;
        row.className = 'chat-bubble-row' + (mine ? ' mine' : '') + (groupedWithPrev ? ' grouped' : '');
        row.dataset.messageId = message.id;
        row.dataset.senderId = message.senderId;
        row.dataset.createdAt = message.createdAt;

        // 1:1/나와의 채팅은 헤더에 이미 상대가 누군지 나와있어 이름이 불필요하고,
        // 그룹 채팅에서도 같은 그룹으로 묶인 두 번째 메시지부터는 이름을 반복하지 않는다.
        if (!mine && roomType === 'GROUP' && !groupedWithPrev) {
            var sender = document.createElement('div');
            sender.className = 'chat-bubble-sender';
            sender.textContent = message.senderNickname;
            row.appendChild(sender);
        }

        // 시간/수정·삭제 버튼은 기본으로 숨겨두고, 말풍선을 탭(또는 호버)하면 펼쳐 보여준다
        // (인스타그램처럼 항상 노출하지 않아 목록이 덜 산만하게).
        row.addEventListener('click', function (e) {
            if (e.target.closest('a') || e.target.closest('.chat-bubble-edit-form')) return;
            row.classList.toggle('expanded');
        });

        renderBubbleContent(row, message, mine);
        return row;
    }

    // 새로 그릴 때도, 수정/삭제로 기존 말풍선 내용을 갈아끼울 때도 이 함수 하나로 처리한다.
    function renderBubbleContent(row, message, mine) {
        row.querySelectorAll('.chat-bubble, .chat-bubble-time, .chat-bubble-actions').forEach(function (el) { el.remove(); });

        var bubble = document.createElement('div');
        bubble.className = 'chat-bubble';
        if (message.deleted) {
            bubble.classList.add('chat-bubble-deleted');
            bubble.textContent = '삭제된 메시지입니다.';
        } else if (message.type === 'FILE') {
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
        var timeText = formatTime(message.createdAt);
        if (message.edited && !message.deleted) timeText += ' · 수정됨';
        time.textContent = timeText;
        row.appendChild(time);

        // 본인이 쓴, 삭제되지 않은 메시지에만 수정/삭제 버튼을 둔다. 파일 메시지는
        // 캡션이 없어 수정은 못 하고 삭제만 가능.
        if (mine && !message.deleted) {
            var actions = document.createElement('div');
            actions.className = 'chat-bubble-actions';
            if (message.type === 'TEXT') {
                var editBtn = document.createElement('button');
                editBtn.type = 'button';
                editBtn.textContent = '수정';
                editBtn.addEventListener('click', function () { startEdit(row, message); });
                actions.appendChild(editBtn);
            }
            var deleteBtn = document.createElement('button');
            deleteBtn.type = 'button';
            deleteBtn.textContent = '삭제';
            deleteBtn.addEventListener('click', function () { deleteMessage(message.id); });
            actions.appendChild(deleteBtn);
            row.appendChild(actions);
        }
    }

    function startEdit(row, message) {
        row.querySelectorAll('.chat-bubble, .chat-bubble-time, .chat-bubble-actions').forEach(function (el) { el.remove(); });

        var form = document.createElement('form');
        form.className = 'chat-bubble-edit-form';
        var input = document.createElement('input');
        input.type = 'text';
        input.value = message.content;
        input.maxLength = 2000;
        var saveBtn = document.createElement('button');
        saveBtn.type = 'submit';
        saveBtn.textContent = '저장';
        var cancelBtn = document.createElement('button');
        cancelBtn.type = 'button';
        cancelBtn.textContent = '취소';
        cancelBtn.addEventListener('click', function () { renderBubbleContent(row, message, true); });
        form.appendChild(input);
        form.appendChild(saveBtn);
        form.appendChild(cancelBtn);
        form.addEventListener('submit', function (e) {
            e.preventDefault();
            var newContent = input.value.trim();
            if (!newContent) return;
            jsonFetch('/api/chat/rooms/' + roomId + '/messages/' + message.id, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content: newContent })
            }).then(function (res) {
                if (!res.ok) return readErrorMessage(res, '수정에 실패했습니다.').then(function (msg) { AdminModal.showError(msg); });
                // 실제 갱신은 이 요청이 트리거하는 WebSocket EDIT 브로드캐스트로 처리된다
                // (본인 화면도 예외 없이 같은 경로를 타서 서버가 계산한 최종 상태만 반영).
            }).catch(function () { AdminModal.showError(); });
        });
        row.appendChild(form);
        input.focus();
    }

    function deleteMessage(messageId) {
        jsonFetch('/api/chat/rooms/' + roomId + '/messages/' + messageId, { method: 'DELETE' })
            .then(function (res) {
                if (!res.ok) return readErrorMessage(res, '삭제에 실패했습니다.').then(function (msg) { AdminModal.showError(msg); });
            })
            .catch(function () { AdminModal.showError(); });
    }

    function findRow(messageId) {
        return messagesEl.querySelector('.chat-bubble-row[data-message-id="' + messageId + '"]');
    }

    function appendMessage(message) {
        var grouped = isGroupedWithPrev(messagesEl.lastElementChild, message);
        messagesEl.appendChild(buildBubble(message, grouped));
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function updateMessage(message) {
        var row = findRow(message.id);
        if (!row) return;
        renderBubbleContent(row, message, message.senderId === myUserId);
    }

    function prependMessages(messages) {
        var prevHeight = messagesEl.scrollHeight;
        var fragment = document.createDocumentFragment();
        var prevRow = null;
        messages.forEach(function (m) {
            var row = buildBubble(m, isGroupedWithPrev(prevRow, m));
            fragment.appendChild(row);
            prevRow = row;
        });
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
            roomType = detail.type;

            var avatarUrl = null;
            if (detail.type === 'SELF') {
                avatarUrl = myProfileImageUrl;
            } else if (detail.type === 'DIRECT') {
                var other = detail.members.find(function (m) { return m.userId !== myUserId; });
                avatarUrl = other ? other.profileImageUrl : null;
            }
            renderAvatarEl(avatarEl, avatarUrl, detail.displayName);

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
            var event = JSON.parse(frame.body);
            if (event.type === 'EDIT' || event.type === 'DELETE') {
                updateMessage(event.message);
            } else {
                appendMessage(event.message);
                markRead();
            }
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
            myProfileImageUrl = me.profileImageUrl;
            return Promise.all([loadDetail(), loadHistory(null)]);
        })
        .then(markRead)
        .catch(function () {});
});
