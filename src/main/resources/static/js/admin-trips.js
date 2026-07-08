document.addEventListener('DOMContentLoaded', function () {
    var createModal = document.getElementById('trip-create-modal');
    var editModal = document.getElementById('trip-edit-modal');
    var openCreateBtn = document.getElementById('open-trip-create');

    function parseResponse(res) {
        return res.json().then(function (data) { return { ok: res.ok, data: data }; });
    }

    var FP_OPTS = {
        mode: 'range',
        locale: 'ko',
        dateFormat: 'Y-m-d',
        allowInput: false,
        disableMobile: true
    };

    function toYMD(d) {
        return flatpickr.formatDate(d, 'Y-m-d');
    }

    function initRangePicker(inputEl, startHidden, endHidden, defaultDates) {
        if (inputEl._flatpickr) inputEl._flatpickr.destroy();
        var fp = flatpickr(inputEl, Object.assign({}, FP_OPTS, {
            appendTo: document.body,
            onChange: function (selectedDates) {
                startHidden.value = selectedDates[0] ? toYMD(selectedDates[0]) : '';
                endHidden.value   = selectedDates[1] ? toYMD(selectedDates[1]) : '';
            }
        }));
        if (defaultDates && defaultDates[0]) {
            fp.setDate(defaultDates.filter(Boolean), false);
        } else {
            fp.clear();
        }
        return fp;
    }

    var createCountrySelect = document.getElementById('create-country');
    var createRegionSelect = document.getElementById('create-region');
    if (createCountrySelect) {
        createCountrySelect.addEventListener('change', function () {
            createRegionSelect.innerHTML = '<option value="">지역 선택 (선택 사항)</option>';
        });
    }

    if (openCreateBtn && createModal) {
        var createDateRangeInput = document.getElementById('create-date-range');
        var createVisitedDate = document.getElementById('create-visited-date');
        var createEndDate = document.getElementById('create-end-date');

        openCreateBtn.addEventListener('click', function () {
            createModal.querySelector('form').reset();
            createVisitedDate.value = '';
            createEndDate.value = '';
            if (createRegionSelect) createRegionSelect.innerHTML = '<option value="">지역 선택 (선택 사항)</option>';
            if (createCountrySelect && typeof TomSelect !== 'undefined') {
                if (createCountrySelect.tomselect) createCountrySelect.tomselect.destroy();
                new TomSelect(createCountrySelect, {
                    maxOptions: null,
                    allowEmptyOption: true,
                    create: false,
                    sortField: false,
                    dropdownParent: 'body',
                    render: {
                        no_results: function () {
                            return '<div class="no-results">검색 결과가 없습니다.</div>';
                        }
                    }
                });
            }
            initRangePicker(createDateRangeInput, createVisitedDate, createEndDate, null);
            AdminModal.open(createModal);
        });

        createModal.querySelector('form').addEventListener('submit', function (e) {
            e.preventDefault();
            if (!createVisitedDate.value) {
                AdminModal.showError('여행 기간을 선택해주세요.');
                return;
            }
            fetch('/admin/trips', { method: 'POST', body: new FormData(e.target) })
                .then(parseResponse)
                .then(function (result) {
                    if (result.ok && result.data.success) {
                        AdminModal.close(createModal);
                        AdminModal.showSuccess('여행이 등록되었습니다.', function () { window.location.reload(); });
                    } else {
                        AdminModal.showError(result.data.message || '등록에 실패했습니다.');
                    }
                })
                .catch(function () { AdminModal.showError(); });
        });
    }

    if (!editModal) return;

    var editForm = editModal.querySelector('.js-trip-edit-form');
    var editDateRangeInput = document.getElementById('edit-date-range');
    var editVisitedDate = document.getElementById('edit-visited-date');
    var editEndDate = document.getElementById('edit-end-date');
    var imageGrid = editModal.querySelector('.js-image-grid');
    var imageEmpty = editModal.querySelector('.js-image-empty');
    var imageUploadForm = editModal.querySelector('.js-image-upload-form');
    var deleteForm = editModal.querySelector('.js-trip-delete-form');
    var tripCountryEl = editModal.querySelector('.js-trip-country');
    var tripRegionEl = editModal.querySelector('.js-trip-region');
    var currentId = null;

    function renderImages(images) {
        imageGrid.innerHTML = '';
        imageEmpty.style.display = images.length === 0 ? '' : 'none';
        images.forEach(function (image) {
            var li = document.createElement('li');
            li.className = 'image-tile';
            li.dataset.imageId = image.id;
            var img = document.createElement('img');
            img.src = image.url;
            img.alt = '';
            var deleteWrap = document.createElement('span');
            deleteWrap.className = 'image-tile-delete';
            var deleteBtn = document.createElement('button');
            deleteBtn.type = 'button';
            deleteBtn.className = 'js-image-delete';
            deleteBtn.setAttribute('aria-label', '사진 삭제');
            deleteBtn.textContent = '×';
            deleteWrap.appendChild(deleteBtn);
            li.appendChild(img);
            li.appendChild(deleteWrap);
            imageGrid.appendChild(li);
        });
    }

    function refreshImages() {
        fetch('/admin/trips/' + currentId)
            .then(function (res) { return res.json(); })
            .then(function (trip) { renderImages(trip.images); });
    }

    document.querySelectorAll('.js-row-edit').forEach(function (row) {
        row.addEventListener('click', function () {
            fetch('/admin/trips/' + row.dataset.id)
                .then(function (res) { return res.json(); })
                .then(function (trip) {
                    currentId = trip.id;
                    editForm.action = '/admin/trips/' + trip.id;
                    imageUploadForm.action = '/admin/trips/' + trip.id + '/images';
                    deleteForm.action = '/admin/trips/' + trip.id + '/delete';
                    tripCountryEl.textContent = trip.countryNameKo;
                    tripRegionEl.textContent = trip.regionNameKo ? '› ' + trip.regionNameKo : '';
                    editForm.querySelector('[name=title]').value = trip.title;
                    editForm.querySelector('[name=description]').value = trip.description || '';
                    editForm.querySelector('[name=priority]').value = trip.priority || 0;

                    editVisitedDate.value = trip.visitedDate || '';
                    editEndDate.value = trip.endDate || '';
                    initRangePicker(editDateRangeInput, editVisitedDate, editEndDate,
                        [trip.visitedDate, trip.endDate]);

                    renderImages(trip.images);
                    AdminModal.open(editModal);
                })
                .catch(function () { AdminModal.showError('여행 정보를 불러오지 못했습니다.'); });
        });
    });

    editForm.addEventListener('submit', function (e) {
        e.preventDefault();
        if (!editVisitedDate.value) {
            AdminModal.showError('여행 기간을 선택해주세요.');
            return;
        }
        fetch(editForm.action, { method: 'POST', body: new FormData(editForm) })
            .then(parseResponse)
            .then(function (result) {
                if (result.ok && result.data.success) {
                    AdminModal.showSuccess('수정되었습니다.', function () { window.location.reload(); });
                } else {
                    AdminModal.showError(result.data.message);
                }
            })
            .catch(function () { AdminModal.showError(); });
    });

    imageUploadForm.addEventListener('submit', function (e) {
        e.preventDefault();
        var fileInput = imageUploadForm.querySelector('input[type=file]');
        if (!fileInput.files.length) return;
        fetch(imageUploadForm.action, { method: 'POST', body: new FormData(imageUploadForm) })
            .then(parseResponse)
            .then(function (result) {
                if (result.ok && result.data.success) {
                    fileInput.value = '';
                    refreshImages();
                    Swal.fire({ icon: 'success', title: '사진이 추가되었습니다.', timer: 1200, showConfirmButton: false });
                } else {
                    AdminModal.showError(result.data.message);
                }
            })
            .catch(function () { AdminModal.showError(); });
    });

    imageGrid.addEventListener('click', function (e) {
        if (!e.target.classList.contains('js-image-delete')) return;
        var tile = e.target.closest('.image-tile');
        var imageId = tile.dataset.imageId;
        AdminModal.confirmDanger('사진을 삭제할까요?').then(function (confirmed) {
            if (!confirmed) return;
            var csrfInput = editForm.querySelector('input[type=hidden]');
            var body = new FormData();
            body.append(csrfInput.name, csrfInput.value);
            fetch('/admin/trips/' + currentId + '/images/' + imageId + '/delete', { method: 'POST', body: body })
                .then(function (res) {
                    if (res.ok) {
                        tile.remove();
                        if (!imageGrid.children.length) imageEmpty.style.display = '';
                    } else {
                        AdminModal.showError('사진 삭제에 실패했습니다.');
                    }
                })
                .catch(function () { AdminModal.showError(); });
        });
    });

    deleteForm.addEventListener('submit', function (e) {
        e.preventDefault();
        AdminModal.confirmDanger('여행을 삭제할까요?', '등록된 사진도 함께 삭제됩니다.').then(function (confirmed) {
            if (!confirmed) return;
            fetch(deleteForm.action, { method: 'POST', body: new FormData(deleteForm) })
                .then(parseResponse)
                .then(function (result) {
                    if (result.ok && result.data.success) {
                        AdminModal.showSuccess('삭제되었습니다.', function () { window.location.reload(); });
                    } else {
                        AdminModal.showError(result.data.message);
                    }
                })
                .catch(function () { AdminModal.showError(); });
        });
    });
});