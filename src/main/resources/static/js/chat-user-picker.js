// 닉네임 검색 / 팔로잉 목록 기반 사용자 선택 UI(인스타그램 방식) — 새 대화 시작(chat-new.js)과
// 그룹방 멤버 초대(chat-invite.js)에서 공유해서 쓰는 공통 로직.
window.ChatUserPicker = (function () {
    var meReady = fetch('/api/me').then(function (res) { return res.json(); });

    function debounce(fn, waitMs) {
        var timer;
        return function () {
            var args = arguments;
            clearTimeout(timer);
            timer = setTimeout(function () { fn.apply(null, args); }, waitMs);
        };
    }

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

    // multi=false: 단일 선택(1:1/초대), multi=true: 다중 선택(그룹 생성)
    // excludeIds: 목록에서 숨길 user id 목록(그룹 초대 시 이미 참여 중인 멤버 등)
    function create(searchInput, listEl, labelEl, multi, onChange, excludeIds) {
        var excludeSet = new Set(excludeIds || []);
        var selected = multi ? {} : null;
        var lastUsers = [];

        function isSelected(user) {
            return multi ? !!selected[user.id] : (selected && selected.id === user.id);
        }

        function render() {
            listEl.innerHTML = '';
            var visible = lastUsers.filter(function (u) { return !excludeSet.has(u.id); });
            if (!visible.length) {
                var empty = document.createElement('li');
                empty.className = 'chat-user-list-empty';
                empty.textContent = '결과가 없습니다.';
                listEl.appendChild(empty);
                return;
            }
            visible.forEach(function (user) {
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

    return { create: create };
})();
