document.addEventListener('DOMContentLoaded', function () {
    var createModal = document.getElementById('ip-create-modal');
    var openCreateBtn = document.getElementById('open-ip-create');

    if (openCreateBtn && createModal) {
        openCreateBtn.addEventListener('click', function () {
            createModal.querySelector('form').reset();
            AdminModal.open(createModal);
        });
    }

    var createForm = createModal ? createModal.querySelector('form') : null;
    if (createForm) {
        createForm.addEventListener('submit', function (e) {
            e.preventDefault();
            fetch(createForm.action, { method: 'POST', body: new FormData(createForm) })
                .then(function (res) { return res.json().then(function (body) { return { ok: res.ok, body: body }; }); })
                .then(function (result) {
                    if (result.ok && result.body.success) {
                        window.location.reload();
                    } else {
                        AdminModal.showError(result.body.message || '추가에 실패했습니다.');
                    }
                })
                .catch(function () { AdminModal.showError(); });
        });
    }

    document.querySelectorAll('.js-delete-ip').forEach(function (form) {
        form.addEventListener('submit', function (e) {
            e.preventDefault();
            AdminModal.confirmDanger('이 IP를 화이트리스트에서 제거할까요?').then(function (confirmed) {
                if (!confirmed) return;
                fetch(form.action, { method: 'POST', body: new FormData(form) })
                    .then(function (res) {
                        if (res.ok) {
                            AdminModal.showSuccess('삭제되었습니다.', function () { window.location.reload(); });
                        } else {
                            AdminModal.showError('삭제에 실패했습니다.');
                        }
                    })
                    .catch(function () { AdminModal.showError(); });
            });
        });
    });
});
