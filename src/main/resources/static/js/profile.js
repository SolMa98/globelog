(function () {
    var cardEl = document.getElementById('profile-card');
    if (!cardEl) return;

    var nickname = cardEl.dataset.nickname;
    var csrfHeaderName = null;
    var csrfToken = null;
    var viewerLoggedIn = false;

    Promise.all([
        fetch('/api/me').then(function (res) { return res.json(); }),
        fetch('/api/users/' + encodeURIComponent(nickname)).then(function (res) {
            if (!res.ok) throw new Error('not found');
            return res.json();
        })
    ]).then(function (results) {
        var me = results[0];
        var profile = results[1];
        csrfHeaderName = me.csrfHeaderName;
        csrfToken = me.csrfToken;
        viewerLoggedIn = me.loggedIn;
        render(profile);
    }).catch(function () {
        document.getElementById('profile-nickname').textContent = '존재하지 않는 사용자입니다';
    });

    function render(profile) {
        document.getElementById('profile-nickname').textContent = profile.nickname;
        document.getElementById('profile-subtitle').textContent = '여행자 · ' + profile.joinYear + '년부터';
        renderAvatar(profile);
        updateCounts(profile);
        renderActions(profile);
    }

    function renderAvatar(profile) {
        var avatarEl = document.getElementById('profile-avatar');
        if (profile.profileImageUrl) {
            avatarEl.style.backgroundImage = 'url(' + profile.profileImageUrl + ')';
        }
    }

    function renderActions(profile) {
        var actionsEl = document.getElementById('profile-actions');
        actionsEl.innerHTML = '';

        if (profile.isSelf) {
            var globeLink = document.createElement('a');
            globeLink.href = '/u/' + encodeURIComponent(profile.nickname) + '/globe';
            globeLink.className = 'btn btn-primary btn-block';
            globeLink.textContent = '내 지구본 보기';

            var manageLink = document.createElement('a');
            manageLink.href = '/my/trips';
            manageLink.className = 'btn btn-ghost btn-block';
            manageLink.textContent = '여행 관리';

            actionsEl.appendChild(globeLink);
            actionsEl.appendChild(manageLink);
            return;
        }

        if (!viewerLoggedIn) {
            var loginLink = document.createElement('a');
            loginLink.href = '/login';
            loginLink.className = 'btn btn-primary btn-block';
            loginLink.textContent = '로그인하고 팔로우하기';
            actionsEl.appendChild(loginLink);
            return;
        }

        var followBtn = document.createElement('button');
        followBtn.type = 'button';
        setButtonLabel(followBtn, profile.isFollowing);
        followBtn.addEventListener('click', function () {
            toggleFollow(profile, followBtn);
        });
        actionsEl.appendChild(followBtn);
    }

    function setButtonLabel(btn, isFollowing) {
        btn.textContent = isFollowing ? '언팔로우' : '팔로우';
        btn.className = isFollowing ? 'btn btn-ghost btn-block' : 'btn btn-primary btn-block';
    }

    function updateCounts(profile) {
        document.getElementById('stat-country').textContent = profile.visitedCountryCount;
        document.getElementById('stat-trips').textContent = profile.tripCount;
        document.getElementById('stat-followers').textContent = profile.followerCount;
    }

    function toggleFollow(profile, btn) {
        var method = profile.isFollowing ? 'DELETE' : 'POST';
        var headers = {};
        headers[csrfHeaderName] = csrfToken;
        fetch('/api/users/' + encodeURIComponent(nickname) + '/follow', { method: method, headers: headers })
            .then(function (res) { return res.json(); })
            .then(function (updated) {
                profile.isFollowing = updated.isFollowing;
                profile.followerCount = updated.followerCount;
                profile.followingCount = updated.followingCount;
                updateCounts(profile);
                setButtonLabel(btn, profile.isFollowing);
            });
    }
})();
