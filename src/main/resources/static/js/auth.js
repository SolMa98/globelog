(function () {
    var accountBoxEl = document.getElementById('account-box');
    if (!accountBoxEl) return;

    fetch('/api/me')
        .then(function (res) { return res.json(); })
        .then(render)
        .catch(function () {});

    function render(me) {
        accountBoxEl.innerHTML = '';

        // 지구본은 항상 특정 사용자 소유(/u/{nickname}/globe)라, 그 페이지에서는
        // "피드로"만 보여주고, 그 외(피드 등)에서는 로그인했을 때만 "내 지구본" 링크를 보여줌
        var isGlobePage = /^\/u\/[^/]+\/globe/.test(window.location.pathname);
        if (isGlobePage) {
            var feedLink = document.createElement('a');
            feedLink.href = '/';
            feedLink.textContent = '피드로';
            accountBoxEl.appendChild(feedLink);
            accountBoxEl.appendChild(document.createTextNode(' · '));
        } else if (me.loggedIn) {
            var myGlobeLink = document.createElement('a');
            myGlobeLink.href = '/u/' + encodeURIComponent(me.nickname) + '/globe';
            myGlobeLink.textContent = '내 지구본';
            accountBoxEl.appendChild(myGlobeLink);
            accountBoxEl.appendChild(document.createTextNode(' · '));
        }

        if (!me.loggedIn) {
            var loginLink = document.createElement('a');
            loginLink.href = '/login';
            loginLink.textContent = '로그인';
            var signupLink = document.createElement('a');
            signupLink.href = '/signup';
            signupLink.textContent = '회원가입';

            accountBoxEl.appendChild(loginLink);
            accountBoxEl.appendChild(document.createTextNode(' · '));
            accountBoxEl.appendChild(signupLink);
            return;
        }

        var nicknameEl = document.createElement('span');
        nicknameEl.className = 'account-nickname';
        nicknameEl.textContent = me.nickname;

        var myTripsLink = document.createElement('a');
        myTripsLink.href = '/my/trips';
        myTripsLink.textContent = '내 여행';

        var securityLink = document.createElement('a');
        securityLink.href = '/my/security';
        securityLink.textContent = '보안';

        var logoutBtn = document.createElement('button');
        logoutBtn.type = 'button';
        logoutBtn.textContent = '로그아웃';
        logoutBtn.addEventListener('click', function () { logout(me.csrfHeaderName, me.csrfToken); });

        accountBoxEl.appendChild(nicknameEl);
        accountBoxEl.appendChild(document.createTextNode(' · '));
        accountBoxEl.appendChild(myTripsLink);
        accountBoxEl.appendChild(document.createTextNode(' · '));
        accountBoxEl.appendChild(securityLink);
        accountBoxEl.appendChild(document.createTextNode(' · '));
        accountBoxEl.appendChild(logoutBtn);
    }

    function logout(csrfHeaderName, csrfToken) {
        var headers = {};
        headers[csrfHeaderName] = csrfToken;
        fetch('/logout', { method: 'POST', headers: headers })
            .then(function () { window.location.href = '/'; })
            .catch(function () { window.location.href = '/'; });
    }
})();
