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
        updateCounts(profile);

        var actionsEl = document.getElementById('profile-actions');
        actionsEl.innerHTML = '';

        if (profile.isSelf) {
            return;
        }

        if (!viewerLoggedIn) {
            var loginLink = document.createElement('a');
            loginLink.href = '/login';
            loginLink.className = 'btn btn-primary';
            loginLink.textContent = '로그인하고 팔로우하기';
            actionsEl.appendChild(loginLink);
            return;
        }

        var followBtn = document.createElement('button');
        followBtn.type = 'button';
        followBtn.className = 'btn btn-primary';
        setButtonLabel(followBtn, profile.isFollowing);
        followBtn.addEventListener('click', function () {
            toggleFollow(profile, followBtn);
        });
        actionsEl.appendChild(followBtn);
    }

    function setButtonLabel(btn, isFollowing) {
        btn.textContent = isFollowing ? '언팔로우' : '팔로우';
        btn.className = isFollowing ? 'btn btn-ghost' : 'btn btn-primary';
    }

    function updateCounts(profile) {
        document.getElementById('profile-counts').textContent =
            '팔로워 ' + profile.followerCount + ' · 팔로잉 ' + profile.followingCount;
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
