document.addEventListener('DOMContentLoaded', function () {
    var createSubmitBtn = document.getElementById('chat-create-submit');
    var currentTab = 'direct';

    function jsonFetch(url, options) {
        return GlobelogCsrf.fetch().then(function (auth) {
            var headers = Object.assign({ 'Content-Type': 'application/json' }, (options && options.headers) || {});
            if (auth) headers[auth.headerName] = auth.token;
            return fetch(url, Object.assign({}, options, { headers: headers }));
        });
    }

    var groupSelectedEl = document.getElementById('chat-group-selected');

    function renderGroupChips(selected) {
        groupSelectedEl.innerHTML = '';
        Object.keys(selected).forEach(function (id) {
            var user = selected[id];
            var chip = document.createElement('span');
            chip.className = 'chat-user-chip';
            chip.textContent = user.nickname;
            var removeBtn = document.createElement('button');
            removeBtn.type = 'button';
            removeBtn.textContent = '×';
            removeBtn.addEventListener('click', function () { groupPicker.deselect(user.id); });
            chip.appendChild(removeBtn);
            groupSelectedEl.appendChild(chip);
        });
    }

    var directPicker = ChatUserPicker.create(
        document.getElementById('chat-direct-search'),
        document.getElementById('chat-direct-list'),
        document.getElementById('chat-direct-list-label'),
        false
    );

    var groupPicker = ChatUserPicker.create(
        document.getElementById('chat-group-search'),
        document.getElementById('chat-group-list'),
        document.getElementById('chat-group-list-label'),
        true,
        renderGroupChips
    );

    document.querySelectorAll('.chat-create-tab').forEach(function (tab) {
        tab.addEventListener('click', function () {
            currentTab = tab.dataset.tab;
            document.querySelectorAll('.chat-create-tab').forEach(function (t) { t.classList.toggle('active', t === tab); });
            document.querySelectorAll('.chat-create-panel').forEach(function (panel) {
                panel.style.display = panel.dataset.panel === currentTab ? '' : 'none';
            });
        });
    });

    createSubmitBtn.addEventListener('click', function () {
        if (currentTab === 'direct') {
            var target = directPicker.getSelected();
            if (!target) { AdminModal.showError('대화할 상대를 선택해주세요.'); return; }
            openRoom(jsonFetch('/api/chat/rooms/direct', { method: 'POST', body: JSON.stringify({ nickname: target.nickname }) }));
        } else if (currentTab === 'group') {
            var name = document.getElementById('chat-group-name').value.trim();
            if (!name) { AdminModal.showError('방 이름을 입력해주세요.'); return; }
            var selectedMembers = groupPicker.getSelected();
            var memberNicknames = Object.keys(selectedMembers).map(function (id) { return selectedMembers[id].nickname; });
            if (!memberNicknames.length) { AdminModal.showError('초대할 멤버를 최소 1명 선택해주세요.'); return; }
            openRoom(jsonFetch('/api/chat/rooms/group', { method: 'POST', body: JSON.stringify({ name: name, memberNicknames: memberNicknames }) }));
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
});
