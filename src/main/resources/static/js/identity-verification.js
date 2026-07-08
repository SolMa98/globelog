// PortOne(다날 채널) 본인인증 버튼 공용 로직 — signup.html / signup-social-complete.html이 공유.
// PC는 팝업으로 바로 끝나지만, 모바일은 redirectUrl로 풀페이지 이동 후 돌아오므로
// 이때 폼에 입력해둔 값을 sessionStorage에 잠깐 저장했다가 복귀 시 되살린다.
function initIdentityVerification(options) {
    var FORM_CACHE_KEY = 'identity-verification-form-cache';

    var button = document.getElementById(options.buttonId);
    var hiddenInput = document.getElementById(options.hiddenInputId);
    var submitButton = options.submitButtonId ? document.getElementById(options.submitButtonId) : null;
    if (!button || !hiddenInput) {
        return;
    }

    restoreFormValues();

    var returnedId = new URLSearchParams(window.location.search).get('identityVerificationId');
    if (returnedId) {
        hiddenInput.value = returnedId;
        markVerified();
    }

    button.addEventListener('click', function () {
        if (!window.PortOne) {
            alert('본인인증 모듈을 불러오지 못했습니다. 새로고침 후 다시 시도해주세요.');
            return;
        }
        saveFormValues();

        var identityVerificationId = 'identity-verification-' + crypto.randomUUID();
        button.disabled = true;
        button.textContent = '본인인증 진행 중...';

        PortOne.requestIdentityVerification({
            storeId: options.storeId,
            channelKey: options.channelKey,
            identityVerificationId: identityVerificationId,
            redirectUrl: window.location.href.split('?')[0]
        }).then(function (response) {
            if (response && response.code !== undefined) {
                button.disabled = false;
                button.textContent = '본인인증하기';
                alert(response.message || '본인인증에 실패했습니다.');
                return;
            }
            hiddenInput.value = identityVerificationId;
            markVerified();
        });
    });

    function markVerified() {
        button.disabled = true;
        button.textContent = '본인인증 완료 ✓';
        if (submitButton) {
            submitButton.disabled = false;
        }
    }

    function saveFormValues() {
        if (!options.preserveFieldIds) {
            return;
        }
        var values = {};
        options.preserveFieldIds.forEach(function (id) {
            var el = document.getElementById(id);
            if (el) {
                values[id] = el.value;
            }
        });
        sessionStorage.setItem(FORM_CACHE_KEY, JSON.stringify(values));
    }

    function restoreFormValues() {
        var raw = sessionStorage.getItem(FORM_CACHE_KEY);
        if (!raw) {
            return;
        }
        var values = JSON.parse(raw);
        Object.keys(values).forEach(function (id) {
            var el = document.getElementById(id);
            if (el) {
                el.value = values[id];
            }
        });
        sessionStorage.removeItem(FORM_CACHE_KEY);
    }
}
