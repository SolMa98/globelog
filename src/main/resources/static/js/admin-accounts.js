document.addEventListener('DOMContentLoaded', function () {
    var createModal = document.getElementById('account-create-modal');
    var openCreateBtn = document.getElementById('open-account-create');

    if (openCreateBtn && createModal) {
        openCreateBtn.addEventListener('click', function () {
            createModal.querySelector('form').reset();
            AdminModal.open(createModal);
        });
    }

    document.querySelectorAll('.js-delete-account').forEach(function (form) {
        form.addEventListener('submit', function (e) {
            e.preventDefault();
            AdminModal.confirmDanger('계정을 삭제할까요?').then(function (confirmed) {
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