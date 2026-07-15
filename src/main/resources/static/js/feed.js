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

        var metaEl = document.createElement('div');
        metaEl.className = 'feed-card-meta';
        var metaParts = [flagEmoji(post.countryIsoA2), post.countryNameKo];
        if (post.regionNameKo) metaParts.push(post.regionNameKo);
        metaEl.textContent = metaParts.filter(Boolean).join(' · ');
        if (window.GlobelogCountryColor && post.countryIsoA3) {
            metaEl.style.color = GlobelogCountryColor.colorFor(post.countryIsoA3).text;
        }
        body.appendChild(metaEl);

        var titleEl = document.createElement('h3');
        titleEl.className = 'feed-card-title';
        titleEl.textContent = post.title;
        body.appendChild(titleEl);

        var footerEl = document.createElement('div');
        footerEl.className = 'feed-card-footer';

        var authorEl = document.createElement('a');
        authorEl.className = 'feed-card-author';
        authorEl.href = '/u/' + encodeURIComponent(post.authorNickname);
        authorEl.textContent = '@' + post.authorNickname;
        footerEl.appendChild(authorEl);

        var viewsEl = document.createElement('span');
        viewsEl.className = 'feed-card-views';
        viewsEl.textContent = '조회 ' + (post.viewCount || 0);
        footerEl.appendChild(viewsEl);

        body.appendChild(footerEl);

        card.appendChild(body);
        return card;
    }

    // isoA2(ex. "PT") → 국기 이모지. 각 알파벳을 유니코드 Regional Indicator
    // Symbol로 치환하는 표준 트릭(A~Z가 U+1F1E6~U+1F1FF에 순서대로 대응).
    function flagEmoji(isoA2) {
        if (!isoA2 || isoA2.length !== 2) return '';
        var codePoints = isoA2.toUpperCase().split('').map(function (c) {
            return 127397 + c.charCodeAt(0);
        });
        return String.fromCodePoint.apply(String, codePoints);
    }
})();
