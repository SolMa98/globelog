document.addEventListener('DOMContentLoaded', function () {
    var provincesCache = null;

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

    function tsInit(el) {
        if (typeof TomSelect === 'undefined') return;
        if (el.tomselect) el.tomselect.destroy();
        new TomSelect(el, TS_OPTS);
    }

    function parseResponse(res) {
        return res.json().then(function (data) { return { ok: res.ok, data: data }; });
    }

    function loadProvinces() {
        if (provincesCache) return Promise.resolve(provincesCache);
        return fetch('/data/provinces.json')
            .then(function (res) { return res.json(); })
            .then(function (data) { provincesCache = data; return data; });
    }

    function setCoordReadonly(latEl, lngEl, locked) {
        [latEl, lngEl].forEach(function (el) {
            if (locked) { el.setAttribute('readonly', ''); el.classList.add('coord-locked'); }
            else { el.removeAttribute('readonly'); el.classList.remove('coord-locked'); }
        });
    }

    function populateProvinceSelect(selectEl, isoA3, matchNameEn, onDone) {
        if (selectEl.tomselect) selectEl.tomselect.destroy();
        selectEl.innerHTML = '';
        if (!isoA3) {
            selectEl.disabled = true;
            selectEl.innerHTML = '<option value="">먼저 국가를 선택하세요</option>';
            tsInit(selectEl);
            if (onDone) onDone(false);
            return;
        }
        selectEl.innerHTML = '<option value="">불러오는 중...</option>';
        loadProvinces().then(function (provinces) {
            var list = provinces[isoA3] || [];
            selectEl.innerHTML = '<option value="">시/도 선택 (자동입력)</option>';
            selectEl.disabled = false;
            var matched = false;
            list.forEach(function (p) {
                var opt = document.createElement('option');
                opt.value = JSON.stringify(p);
                opt.textContent = p.nameKo !== p.nameEn ? p.nameKo + ' (' + p.nameEn + ')' : p.nameKo;
                if (matchNameEn && p.nameEn === matchNameEn) { opt.selected = true; matched = true; }
                selectEl.appendChild(opt);
            });
            var customOpt = document.createElement('option');
            customOpt.value = '__custom__';
            customOpt.textContent = '✏ 직접 입력 (섬·특수 구역 등)';
            selectEl.appendChild(customOpt);
            tsInit(selectEl);
            if (onDone) onDone(matched);
        }).catch(function () {
            selectEl.innerHTML = '<option value="__custom__">✏ 직접 입력</option>';
            selectEl.disabled = false;
            tsInit(selectEl);
            if (onDone) onDone(false);
        });
    }

    function bindProvinceChange(provinceSelect, nameKoInput, nameEnInput, latInput, lngInput) {
        var prevVal = '';
        provinceSelect.addEventListener('change', function () {
            var val = provinceSelect.value;
            if (val === '__custom__') {
                setCoordReadonly(latInput, lngInput, false);
                nameKoInput.value = '';
                nameEnInput.value = '';
                latInput.value = '';
                lngInput.value = '';
                prevVal = val;
                nameKoInput.focus();
            } else if (val) {
                if (prevVal === '__custom__' && (nameKoInput.value || latInput.value)) {
                    if (!confirm('직접 입력한 값을 선택한 시/도 데이터로 덮어쓸까요?')) {
                        if (provinceSelect.tomselect) provinceSelect.tomselect.setValue('__custom__', true);
                        else provinceSelect.value = '__custom__';
                        return;
                    }
                }
                try {
                    var p = JSON.parse(val);
                    nameKoInput.value = p.nameKo;
                    nameEnInput.value = p.nameEn;
                    latInput.value = p.lat;
                    lngInput.value = p.lng;
                } catch (e) {}
                setCoordReadonly(latInput, lngInput, true);
                prevVal = val;
            } else {
                setCoordReadonly(latInput, lngInput, false);
                prevVal = val;
            }
        });
    }

    // ── 등록 모달 ────────────────────────────────────────────
    var createModal = document.getElementById('region-create-modal');
    var openCreateBtn = document.getElementById('open-region-create');

    if (createModal && openCreateBtn) {
        var createCountrySelect = document.getElementById('create-country-select');
        var createProvinceSelect = document.getElementById('create-province-select');
        var createNameKo = document.getElementById('create-name-ko');
        var createNameEn = document.getElementById('create-name-en');
        var createLat = document.getElementById('create-lat');
        var createLng = document.getElementById('create-lng');

        createCountrySelect.addEventListener('change', function () {
            var selected = createCountrySelect.selectedOptions[0];
            var isoA3 = selected ? selected.dataset.isoA3 : '';
            setCoordReadonly(createLat, createLng, false);
            createNameKo.value = '';
            createNameEn.value = '';
            createLat.value = '';
            createLng.value = '';
            populateProvinceSelect(createProvinceSelect, isoA3, null, null);
        });

        bindProvinceChange(createProvinceSelect, createNameKo, createNameEn, createLat, createLng);

        openCreateBtn.addEventListener('click', function () {
            createModal.querySelector('form').reset();
            // Province select 리셋 (Tom Select 제거)
            if (createProvinceSelect.tomselect) createProvinceSelect.tomselect.destroy();
            createProvinceSelect.innerHTML = '<option value="">먼저 국가를 선택하세요</option>';
            createProvinceSelect.disabled = true;
            // Country select Tom Select 재초기화 (form.reset() 이후 빈 값 읽도록)
            tsInit(createCountrySelect);
            setCoordReadonly(createLat, createLng, false);
            AdminModal.open(createModal);
        });

        createModal.querySelector('form').addEventListener('submit', function (e) {
            e.preventDefault();
            fetch('/admin/regions', { method: 'POST', body: new FormData(e.target) })
                .then(parseResponse)
                .then(function (result) {
                    if (result.ok && result.data.success) {
                        AdminModal.close(createModal);
                        AdminModal.showSuccess('지역이 등록되었습니다.', function () { window.location.reload(); });
                    } else {
                        AdminModal.showError(result.data.message || '등록에 실패했습니다.');
                    }
                })
                .catch(function () { AdminModal.showError(); });
        });
    }

    // ── 수정 모달 ────────────────────────────────────────────
    var editModal = document.getElementById('region-edit-modal');
    if (!editModal) return;

    var editForm = editModal.querySelector('.js-region-edit-form');
    var deleteForm = editModal.querySelector('.js-region-delete-form');
    var countryNameEl = editModal.querySelector('.js-country-name');
    var editProvinceSelect = document.getElementById('edit-province-select');
    var editNameKo = editForm.querySelector('[name=nameKo]');
    var editNameEn = editForm.querySelector('[name=nameEn]');
    var editLat = editForm.querySelector('[name=centerLat]');
    var editLng = editForm.querySelector('[name=centerLng]');

    bindProvinceChange(editProvinceSelect, editNameKo, editNameEn, editLat, editLng);

    document.querySelectorAll('.js-row-edit').forEach(function (row) {
        row.addEventListener('click', function () {
            fetch('/admin/regions/' + row.dataset.id)
                .then(function (res) { return res.json(); })
                .then(function (region) {
                    editForm.action = '/admin/regions/' + region.id;
                    deleteForm.action = '/admin/regions/' + region.id + '/delete';
                    countryNameEl.textContent = region.countryNameKo;
                    editNameKo.value = region.nameKo;
                    editNameEn.value = region.nameEn || '';
                    editLat.value = region.centerLat != null ? region.centerLat : '';
                    editLng.value = region.centerLng != null ? region.centerLng : '';
                    editForm.querySelector('[name=enabled]').checked = region.enabled;
                    setCoordReadonly(editLat, editLng, false);
                    populateProvinceSelect(editProvinceSelect, region.countryIsoA3, region.nameEn,
                        function (matched) {
                            setCoordReadonly(editLat, editLng, matched);
                            if (!matched) {
                                // Tom Select 인스턴스가 있으면 silent 선택 (change 이벤트 미발생)
                                if (editProvinceSelect.tomselect) {
                                    editProvinceSelect.tomselect.setValue('__custom__', true);
                                } else {
                                    var customOpt = editProvinceSelect.querySelector('option[value="__custom__"]');
                                    if (customOpt) customOpt.selected = true;
                                }
                            }
                        });
                    AdminModal.open(editModal);
                })
                .catch(function () { AdminModal.showError('지역 정보를 불러오지 못했습니다.'); });
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
        AdminModal.confirmDanger('지역을 삭제할까요?', '이 지역의 여행 기록도 함께 삭제됩니다.').then(function (confirmed) {
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