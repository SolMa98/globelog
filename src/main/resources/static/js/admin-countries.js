document.addEventListener('DOMContentLoaded', function () {
    var createModal = document.getElementById('country-create-modal');
    var editModal = document.getElementById('country-edit-modal');
    var openCreateBtn = document.getElementById('open-country-create');

    var countriesRefCache = null;

    function parseResponse(res) {
        return res.json().then(function (data) { return { ok: res.ok, data: data }; });
    }

    function loadCountriesRef() {
        if (countriesRefCache) return Promise.resolve(countriesRefCache);
        return fetch('/data/countries-ref.json')
            .then(function (res) { return res.json(); })
            .then(function (data) { countriesRefCache = data; return data; });
    }

    var TS_OPTS = {
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
    };

    function populateCountrySelect(selectEl, matchIsoA3) {
        if (typeof TomSelect !== 'undefined' && selectEl.tomselect) selectEl.tomselect.destroy();
        selectEl.innerHTML = '<option value="">불러오는 중...</option>';
        loadCountriesRef().then(function (list) {
            selectEl.innerHTML = '<option value="">국가를 선택하세요</option>';
            list.forEach(function (c) {
                var opt = document.createElement('option');
                opt.value = JSON.stringify(c);
                opt.textContent = c.nameKo + ' (' + c.nameEn + ')';
                if (matchIsoA3 && c.isoA3 === matchIsoA3) opt.selected = true;
                selectEl.appendChild(opt);
            });
            if (typeof TomSelect !== 'undefined') new TomSelect(selectEl, TS_OPTS);
        }).catch(function () {
            selectEl.innerHTML = '<option value="">국가 목록을 불러오지 못했습니다.</option>';
            if (typeof TomSelect !== 'undefined') new TomSelect(selectEl, TS_OPTS);
        });
    }

    // ── 등록 모달 ────────────────────────────────────────────
    if (openCreateBtn && createModal) {
        var createCountrySelect = document.getElementById('create-country-ref-select');
        var createIsoA3 = document.getElementById('create-iso-a3');
        var createIsoA2 = document.getElementById('create-iso-a2');
        var createNameKo = document.getElementById('create-name-ko');
        var createNameEn = document.getElementById('create-name-en');
        var createFlagRow = document.getElementById('create-flag-row');
        var createFlagPreview = document.getElementById('create-flag-preview');

        createCountrySelect.addEventListener('change', function () {
            var val = createCountrySelect.value;
            if (!val) {
                createIsoA3.value = '';
                createIsoA2.value = '';
                createNameKo.value = '';
                createNameEn.value = '';
                createFlagRow.style.display = 'none';
                return;
            }
            try {
                var c = JSON.parse(val);
                createIsoA3.value = c.isoA3;
                createIsoA2.value = c.isoA2 || '';
                createNameKo.value = c.nameKo;
                createNameEn.value = c.nameEn;
                if (c.isoA2) {
                    createFlagPreview.src = 'https://flagcdn.com/w160/' + c.isoA2.toLowerCase() + '.png';
                    createFlagRow.style.display = '';
                } else {
                    createFlagRow.style.display = 'none';
                }
            } catch (e) {}
        });

        openCreateBtn.addEventListener('click', function () {
            createModal.querySelector('form').reset();
            createFlagRow.style.display = 'none';
            populateCountrySelect(createCountrySelect, null);
            AdminModal.open(createModal);
        });

        createModal.querySelector('form').addEventListener('submit', function (e) {
            e.preventDefault();
            if (!createIsoA3.value) {
                AdminModal.showError('국가를 선택해주세요.');
                return;
            }
            fetch('/admin/countries', { method: 'POST', body: new FormData(e.target) })
                .then(parseResponse)
                .then(function (result) {
                    if (result.ok && result.data.success) {
                        AdminModal.close(createModal);
                        AdminModal.showSuccess('국가가 등록되었습니다.', function () { window.location.reload(); });
                    } else {
                        AdminModal.showError(result.data.message);
                    }
                })
                .catch(function () { AdminModal.showError(); });
        });
    }

    // ── 수정 모달 ────────────────────────────────────────────
    if (!editModal) return;

    var editForm = editModal.querySelector('.js-country-edit-form');
    var deleteForm = editModal.querySelector('.js-country-delete-form');
    var flagPreviewRow = editModal.querySelector('.js-flag-preview-row');
    var flagPreview = editModal.querySelector('.js-flag-preview');
    var isoA2Input = editForm.querySelector('[name=isoA2]');

    isoA2Input.addEventListener('input', function () {
        var val = isoA2Input.value.trim().toLowerCase();
        if (val.length === 2) {
            flagPreview.src = 'https://flagcdn.com/w160/' + val + '.png';
            flagPreviewRow.style.display = '';
        } else {
            flagPreviewRow.style.display = 'none';
        }
    });

    document.querySelectorAll('.js-row-edit').forEach(function (row) {
        row.addEventListener('click', function () {
            fetch('/admin/countries/' + row.dataset.id)
                .then(function (res) { return res.json(); })
                .then(function (country) {
                    editForm.action = '/admin/countries/' + country.id;
                    deleteForm.action = '/admin/countries/' + country.id + '/delete';
                    editModal.querySelector('.js-iso').textContent = country.isoA3;
                    editForm.querySelector('[name=nameKo]').value = country.nameKo;
                    editForm.querySelector('[name=nameEn]').value = country.nameEn;
                    editForm.querySelector('[name=description]').value = country.description || '';
                    editForm.querySelector('[name=enabled]').checked = country.enabled;

                    var storedIsoA2 = country.isoA2 || '';
                    if (storedIsoA2) {
                        isoA2Input.value = storedIsoA2;
                        flagPreview.src = country.flagUrl || '';
                        flagPreviewRow.style.display = country.flagUrl ? '' : 'none';
                    } else {
                        loadCountriesRef().then(function (list) {
                            var ref = list.find(function (c) { return c.isoA3 === country.isoA3; });
                            if (ref && ref.isoA2) {
                                isoA2Input.value = ref.isoA2;
                                flagPreview.src = 'https://flagcdn.com/w160/' + ref.isoA2.toLowerCase() + '.png';
                                flagPreviewRow.style.display = '';
                            } else {
                                isoA2Input.value = '';
                                flagPreviewRow.style.display = 'none';
                            }
                        });
                    }
                    AdminModal.open(editModal);
                })
                .catch(function () { AdminModal.showError('국가 정보를 불러오지 못했습니다.'); });
        });
    });

    editForm.addEventListener('submit', function (e) {
        e.preventDefault();
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

    deleteForm.addEventListener('submit', function (e) {
        e.preventDefault();
        AdminModal.confirmDanger('국가를 삭제할까요?', '등록된 지역과 여행 기록도 함께 삭제됩니다.').then(function (confirmed) {
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