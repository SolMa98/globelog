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

    function debounce(fn, waitMs) {
        var timer;
        return function () {
            var args = arguments;
            clearTimeout(timer);
            timer = setTimeout(function () { fn.apply(null, args); }, waitMs);
        };
    }

    // ── 사용자 검색(닉네임) / 팔로잉 목록 기반 대화 상대 선택 ──
    // 검색어가 없으면 팔로잉 목록을, 입력하면 전체 유저 검색 결과를 보여준다(인스타그램 방식).
    var meReady = fetch('/api/me').then(function (res) { return res.json(); });

    function buildUserItem(user, selected) {
        var li = document.createElement('li');
        li.className = 'chat-user-item' + (selected ? ' selected' : '');

        var avatar = document.createElement('div');
        avatar.className = 'chat-user-avatar';
        if (user.profileImageUrl) {
            avatar.style.backgroundImage = 'url("' + user.profileImageUrl + '")';
        } else {
            avatar.textContent = (user.nickname || '?').charAt(0).toUpperCase();
        }
        li.appendChild(avatar);

        var name = document.createElement('span');
        name.className = 'chat-user-name';
        name.textContent = user.nickname;
        li.appendChild(name);

        if (selected) {
            var check = document.createElement('span');
            check.className = 'chat-user-check';
            check.textContent = '✓';
            li.appendChild(check);
        }
        return li;
    }

    // multi=false: 단일 선택(1:1), multi=true: 다중 선택(그룹) 피커를 만든다.
    function createUserPicker(searchInput, listEl, labelEl, multi, onChange) {
        var selected = multi ? {} : null;
        var lastUsers = [];

        function isSelected(user) {
            return multi ? !!selected[user.id] : (selected && selected.id === user.id);
        }

        function render() {
            listEl.innerHTML = '';
            if (!lastUsers.length) {
                var empty = document.createElement('li');
                empty.className = 'chat-user-list-empty';
                empty.textContent = '결과가 없습니다.';
                listEl.appendChild(empty);
                return;
            }
            lastUsers.forEach(function (user) {
                var item = buildUserItem(user, isSelected(user));
                item.addEventListener('click', function () {
                    if (multi) {
                        if (selected[user.id]) { delete selected[user.id]; } else { selected[user.id] = user; }
                    } else {
                        selected = (selected && selected.id === user.id) ? null : user;
                    }
                    render();
                    if (onChange) onChange(selected);
                });
                listEl.appendChild(item);
            });
        }

        function loadFollowing() {
            labelEl.textContent = '팔로잉';
            meReady.then(function (me) {
                if (!me.nickname) return;
                fetch('/api/users/' + encodeURIComponent(me.nickname) + '/following')
                    .then(function (res) { return res.json(); })
                    .then(function (users) { lastUsers = users; render(); })
                    .catch(function () {});
            });
        }

        var search = debounce(function (q) {
            if (!q) { loadFollowing(); return; }
            labelEl.textContent = '검색 결과';
            fetch('/api/users/search?q=' + encodeURIComponent(q))
                .then(function (res) { return res.json(); })
                .then(function (users) { lastUsers = users; render(); })
                .catch(function () {});
        }, 300);

        searchInput.addEventListener('input', function () {
            search(searchInput.value.trim());
        });

        loadFollowing();

        return {
            deselect: function (userId) {
                if (multi) { delete selected[userId]; }
                render();
                if (onChange) onChange(selected);
            },
            getSelected: function () { return selected; }
        };
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

    var directPicker = createUserPicker(
        document.getElementById('chat-direct-search'),
        document.getElementById('chat-direct-list'),
        document.getElementById('chat-direct-list-label'),
        false
    );

    var groupPicker = createUserPicker(
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
