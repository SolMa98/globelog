(function () {
    var listEl = document.getElementById('feed-list');
    var loadingEl = document.getElementById('feed-loading');
    var emptyEl = document.getElementById('feed-empty');
    if (!listEl) return;

    fetch('/api/feed')
        .then(function (res) { return res.json(); })
        .then(render)
        .catch(function () {
            loadingEl.textContent = '피드를 불러오지 못했습니다.';
        });

    function render(posts) {
        loadingEl.classList.add('hidden');
        if (!posts.length) {
            emptyEl.classList.remove('hidden');
            return;
        }
        posts.forEach(function (post) {
            listEl.appendChild(buildCard(post));
        });
    }

    function buildCard(post) {
        // 카드 전체(사진/제목) 클릭 = 그 사람 지구본으로 바로 진입하면서, 클릭한 그
        // 게시글(tripId)과 국가(iso)를 쿼리 파라미터로 실어보내 globe.js가 도착하자마자
        // 해당 게시글 스토리를 자동으로 열게 한다(그냥 지구본만 보여주면 어떤 글을
        // 클릭했는지 알 수 없어서 "내용이 안 보인다"는 혼란이 있었음). 작성자명만
        // 별도 링크로 분리해서 프로필(팔로우 버튼 등)로 갈 수 있게 한다. <a> 안에
        // <a>를 중첩할 수 없어 카드는 div + 클릭 이벤트로 이동을 처리한다.
        var card = document.createElement('div');
        card.className = 'feed-card';
        card.setAttribute('role', 'link');
        card.setAttribute('tabindex', '0');
        var globeUrl = '/u/' + encodeURIComponent(post.authorNickname) + '/globe'
            + '?tripId=' + encodeURIComponent(post.tripId)
            + '&iso=' + encodeURIComponent(post.countryIsoA3);
        card.addEventListener('click', function (e) {
            if (e.target.closest('.feed-card-author')) return;
            window.location.href = globeUrl;
        });

        if (post.coverImageUrl) {
            var img = document.createElement('img');
            img.className = 'feed-card-image';
            img.src = post.coverImageUrl;
            img.alt = '';
            card.appendChild(img);
        } else {
            var placeholder = document.createElement('div');
            placeholder.className = 'feed-card-image feed-card-placeholder';
            card.appendChild(placeholder);
        }

        var body = document.createElement('div');
        body.className = 'feed-card-body';

        var titleEl = document.createElement('div');
        titleEl.className = 'feed-card-title';
        titleEl.textContent = post.title;
        body.appendChild(titleEl);

        var metaEl = document.createElement('div');
        metaEl.className = 'feed-card-meta';
        var metaParts = [];
        if (post.regionNameKo) metaParts.push(post.regionNameKo);
        if (post.countryNameKo) metaParts.push(post.countryNameKo);
        if (post.visitedDate) metaParts.push(post.visitedDate);
        metaEl.textContent = metaParts.join(' · ');
        body.appendChild(metaEl);

        var authorEl = document.createElement('a');
        authorEl.className = 'feed-card-author';
        authorEl.href = '/u/' + encodeURIComponent(post.authorNickname);
        authorEl.textContent = '@' + post.authorNickname;
        body.appendChild(authorEl);

        card.appendChild(body);
        return card;
    }
})();
