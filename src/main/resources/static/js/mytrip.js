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
    var editCountrySelect = document.getElementById('edit-country');
    var editRegionSelect = document.getElementById('edit-region');

    // countryId가 바뀔 때(등록/수정 공통) 그 국가의 지역 목록으로 select를 다시 채운다.
    // preselectRegionId를 주면(수정 화면이 기존 값을 불러올 때) 목록이 로드된 뒤 그 값을 선택해둔다.
    function loadRegionOptions(regionSelectEl, countryId, preselectRegionId) {
        regionSelectEl.innerHTML = '<option value="">지역 선택 (선택 사항)</option>';
        if (!countryId) return;
        fetch('/my/trips/regions?countryId=' + encodeURIComponent(countryId))
            .then(function (res) { return res.json(); })
            .then(function (regions) {
                regions.forEach(function (region) {
                    var opt = document.createElement('option');
                    opt.value = region.id;
                    opt.textContent = region.nameKo;
                    regionSelectEl.appendChild(opt);
                });
                if (preselectRegionId) regionSelectEl.value = preselectRegionId;
            })
            .catch(function () {});
    }

    if (createCountrySelect) {
        createCountrySelect.addEventListener('change', function () {
            loadRegionOptions(createRegionSelect, createCountrySelect.value);
        });
    }

    if (editCountrySelect) {
        editCountrySelect.addEventListener('change', function () {
            loadRegionOptions(editRegionSelect, editCountrySelect.value);
        });
    }

    // 국가 236개를 그냥 <select>로 두면 스크롤이 끔찍해서, 등록/수정 화면 둘 다
    // 검색 가능한 TomSelect로 감싼다. 모달을 열 때마다 다시 만드는 이유는 이전에 남은
    // tomselect 인스턴스가 있으면(수정→등록처럼 모달을 번갈아 열 때) 중복 초기화 에러가 나서.
    function initCountryTomSelect(selectEl) {
        if (!selectEl || typeof TomSelect === 'undefined') return;
        if (selectEl.tomselect) selectEl.tomselect.destroy();
        new TomSelect(selectEl, {
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

    if (openCreateBtn && createModal) {
        var createDateRangeInput = document.getElementById('create-date-range');
        var createVisitedDate = document.getElementById('create-visited-date');
        var createEndDate = document.getElementById('create-end-date');

        openCreateBtn.addEventListener('click', function () {
            createModal.querySelector('form').reset();
            createVisitedDate.value = '';
            createEndDate.value = '';
            if (createRegionSelect) createRegionSelect.innerHTML = '<option value="">지역 선택 (선택 사항)</option>';
            initCountryTomSelect(createCountrySelect);
            initRangePicker(createDateRangeInput, createVisitedDate, createEndDate, null);
            AdminModal.open(createModal);
        });

        createModal.querySelector('form').addEventListener('submit', function (e) {
            e.preventDefault();
            if (!createVisitedDate.value) {
                AdminModal.showError('여행 기간을 선택해주세요.');
                return;
            }
            fetch('/my/trips', { method: 'POST', body: new FormData(e.target) })
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
    var editVisibility = document.getElementById('edit-visibility');
    var imageGrid = editModal.querySelector('.js-image-grid');
    var imageEmpty = editModal.querySelector('.js-image-empty');
    var imageUploadForm = editModal.querySelector('.js-image-upload-form');
    var deleteForm = editModal.querySelector('.js-trip-delete-form');
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
        fetch('/my/trips/' + currentId)
            .then(function (res) { return res.json(); })
            .then(function (trip) { renderImages(trip.images); });
    }

    document.querySelectorAll('.js-row-edit').forEach(function (row) {
        row.addEventListener('click', function () {
            fetch('/my/trips/' + row.dataset.id)
                .then(function (res) { return res.json(); })
                .then(function (trip) {
                    currentId = trip.id;
                    editForm.action = '/my/trips/' + trip.id;
                    imageUploadForm.action = '/my/trips/' + trip.id + '/images';
                    deleteForm.action = '/my/trips/' + trip.id + '/delete';
                    editCountrySelect.value = trip.countryId;
                    initCountryTomSelect(editCountrySelect);
                    loadRegionOptions(editRegionSelect, trip.countryId, trip.regionId);
                    editForm.querySelector('[name=title]').value = trip.title;
                    editForm.querySelector('[name=description]').value = trip.description || '';
                    editVisibility.value = trip.visibility;

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
            fetch('/my/trips/' + currentId + '/images/' + imageId + '/delete', { method: 'POST', body: body })
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
