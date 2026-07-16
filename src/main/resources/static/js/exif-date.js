// JPEG 파일의 EXIF에서 촬영일자(DateTimeOriginal, 없으면 DateTime)만 읽어오는 최소 파서.
// 외부 라이브러리 없이 순수 JS로 직접 파싱한다 — 필요한 건 날짜 한 줄뿐이라 전체 EXIF
// 태그를 다 해석하는 무거운 라이브러리를 들일 이유가 없음. JPEG만 지원하며(PNG/HEIC는
// 컨테이너 구조가 달라 대상 밖), 못 읽으면 조용히 null을 돌려준다(호출부가 수동 입력으로
// 대체하면 되므로 에러를 굳이 노출하지 않음).
window.GlobelogExif = (function () {
    function readDateTaken(file, callback) {
        if (!file || file.type !== 'image/jpeg') { callback(null); return; }
        var reader = new FileReader();
        reader.onload = function (e) {
            var date = null;
            try { date = parseDateFromJpeg(e.target.result); } catch (err) { date = null; }
            callback(date);
        };
        reader.onerror = function () { callback(null); };
        reader.readAsArrayBuffer(file);
    }

    function parseDateFromJpeg(buffer) {
        var view = new DataView(buffer);
        if (view.getUint16(0, false) !== 0xFFD8) return null; // JPEG SOI 아님

        var offset = 2;
        while (offset + 4 <= view.byteLength) {
            var marker = view.getUint16(offset, false);
            if ((marker & 0xFF00) !== 0xFF00) break; // 마커가 아니면 구조가 예상과 다름 — 중단
            var segLength = view.getUint16(offset + 2, false);
            if (marker === 0xFFE1) { // APP1 = EXIF
                var date = parseApp1(view, offset + 4, segLength - 2);
                if (date) return date;
            }
            if (marker === 0xFFDA) break; // SOS(스캔 시작) 이후엔 EXIF가 없음
            offset += 2 + segLength;
        }
        return null;
    }

    function parseApp1(view, start, length) {
        // "Exif\0\0" 시그니처 확인 후 TIFF 헤더로 진입
        if (view.getUint32(start, false) !== 0x45786966) return null; // "Exif"
        var tiffStart = start + 6;
        var little = view.getUint16(tiffStart, false) === 0x4949;
        if (view.getUint16(tiffStart + 2, little) !== 0x002A) return null; // TIFF 매직넘버

        var ifd0Offset = tiffStart + view.getUint32(tiffStart + 4, little);
        var ifd0 = readIfd(view, tiffStart, ifd0Offset, little);

        // DateTimeOriginal(0x9003)은 IFD0가 아니라 ExifIFDPointer(0x8769)가 가리키는
        // 서브 IFD 안에 있다.
        if (ifd0.tags[0x8769]) {
            var exifIfd = readIfd(view, tiffStart, tiffStart + ifd0.tags[0x8769].value, little);
            var raw = exifIfd.tags[0x9003] && exifIfd.tags[0x9003].str;
            if (raw) return toYmd(raw);
        }
        // 없으면 IFD0 자체의 DateTime(0x0132)로 대체
        var fallback = ifd0.tags[0x0132] && ifd0.tags[0x0132].str;
        return fallback ? toYmd(fallback) : null;
    }

    // 태그 12바이트: [tag(2)][type(2)][count(4)][value/offset(4)]. ASCII(type=2) 문자열은
    // count<=4면 그 자리에, 아니면 offset 위치에 저장된다. 이 용도로는 ASCII/숫자 값만 필요.
    function readIfd(view, tiffStart, ifdOffset, little) {
        var count = view.getUint16(ifdOffset, little);
        var tags = {};
        for (var i = 0; i < count; i++) {
            var entryOffset = ifdOffset + 2 + i * 12;
            var tag = view.getUint16(entryOffset, little);
            var type = view.getUint16(entryOffset + 2, little);
            var num = view.getUint32(entryOffset + 4, little);
            var valueOffset = entryOffset + 8;
            if (type === 2) { // ASCII
                var strStart = num > 4 ? tiffStart + view.getUint32(valueOffset, little) : valueOffset;
                tags[tag] = { str: readAscii(view, strStart, num) };
            } else if (type === 4 || type === 3) { // LONG/SHORT — 오프셋 계산(ExifIFDPointer)에 필요
                tags[tag] = { value: type === 4 ? view.getUint32(valueOffset, little) : view.getUint16(valueOffset, little) };
            }
        }
        return { tags: tags };
    }

    function readAscii(view, start, len) {
        var chars = [];
        for (var i = 0; i < len; i++) {
            var c = view.getUint8(start + i);
            if (c === 0) break;
            chars.push(String.fromCharCode(c));
        }
        return chars.join('');
    }

    // EXIF 날짜 포맷 "YYYY:MM:DD HH:MM:SS" → "YYYY-MM-DD"
    function toYmd(raw) {
        var datePart = raw.slice(0, 10);
        if (!/^\d{4}:\d{2}:\d{2}$/.test(datePart)) return null;
        return datePart.replace(/:/g, '-');
    }

    return { readDateTaken: readDateTaken };
})();
