window.AdminModal = (function () {
    function open(modal) {
        modal.classList.add('open');
        document.body.style.overflow = 'hidden';
    }

    function close(modal) {
        modal.classList.remove('open');
        document.body.style.overflow = '';
    }

    function showError(message) {
        Swal.fire({ icon: 'error', title: '실패', text: message || '요청을 처리하지 못했습니다.' });
    }

    function showSuccess(message, onClose) {
        Swal.fire({ icon: 'success', title: '완료', text: message, timer: 1500, showConfirmButton: false })
            .then(function () {
                if (onClose) onClose();
            });
    }

    function confirmDanger(title, text) {
        return Swal.fire({
            icon: 'warning',
            title: title,
            text: text,
            showCancelButton: true,
            confirmButtonText: '삭제',
            cancelButtonText: '취소',
            confirmButtonColor: '#ff6f59'
        }).then(function (result) {
            return result.isConfirmed;
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-modal-close]').forEach(function (el) {
            el.addEventListener('click', function () {
                close(el.closest('.modal-backdrop'));
            });
        });

        document.querySelectorAll('.modal-backdrop').forEach(function (backdrop) {
            backdrop.addEventListener('click', function (e) {
                if (e.target === backdrop) close(backdrop);
            });
        });
    });

    return { open: open, close: close, showError: showError, showSuccess: showSuccess, confirmDanger: confirmDanger };
})();