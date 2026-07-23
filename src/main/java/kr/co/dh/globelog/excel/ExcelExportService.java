package kr.co.dh.globelog.excel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.util.List;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * 관리자 화면 어디서든(보안 로그, 향후 계정/여행 목록 등) "표 형태 데이터를 엑셀로
 * 내려받기"에 재사용하는 공용 서비스. 헤더/행 데이터만 넘기면 되고, 비밀번호를 넘기면
 * Excel이 열 때 암호를 요구하는 보호된 파일로 감싼다.
 *
 * 생성은 SXSSFWorkbook(스트리밍)으로 해서 행이 많아져도 힙에 전부 안 올린다. 다만
 * 비밀번호 보호는 완성된 OOXML 패키지 전체를 POIFS(OLE2 컨테이너)로 한 번 더 감싸는
 * 방식이라(POI의 표준 엑셀 암호화 방식) 이 단계에서는 바이트 배열 전체가 메모리에
 * 올라간다 — 사이드 프로젝트 규모의 관리자 다운로드에서는 문제되지 않는 수준.
 */
@Service
public class ExcelExportService {

    public byte[] export(String sheetName, List<String> headers, List<List<Object>> rows,
            List<Integer> columnWidthsChars, String password) {
        byte[] plain = writePlain(sheetName, headers, rows, columnWidthsChars);
        if (password == null || password.isBlank()) {
            return plain;
        }
        return encrypt(plain, password);
    }

    private byte[] writePlain(String sheetName, List<String> headers, List<List<Object>> rows,
            List<Integer> columnWidthsChars) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet(sheetName);
            CellStyle headerStyle = headerStyle(workbook);

            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < headers.size(); col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers.get(col));
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (List<Object> rowValues : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int col = 0; col < rowValues.size(); col++) {
                    Object value = rowValues.get(col);
                    // null은 셀 자체를 만들지 않고 비워둔다(createCell만 하고 값을 안 채우면
                    // "빈 값"이 아니라 빈 문자열 셀이 생겨 호출부의 null 판별 로직과 어긋남).
                    if (value != null) {
                        writeCell(row.createCell(col), value);
                    }
                }
            }

            if (columnWidthsChars != null) {
                for (int col = 0; col < columnWidthsChars.size(); col++) {
                    sheet.setColumnWidth(col, columnWidthsChars.get(col) * 256);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            // POI 5.3부터 close()가 dispose()(임시파일 정리)까지 함께 처리한다.
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("엑셀 생성에 실패했습니다.", e);
        }
    }

    private void writeCell(Cell cell, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    // Excel "통합 문서 암호화"와 동일한 방식(agile 모드) — 파일을 열 때 비밀번호를
    // 요구한다. 완성된 xlsx(OOXML zip) 전체를 POIFS(OLE2) 컨테이너로 감싸는 POI 표준 레시피.
    private byte[] encrypt(byte[] plainXlsx, String password) {
        try (POIFSFileSystem fs = new POIFSFileSystem()) {
            EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile);
            Encryptor encryptor = info.getEncryptor();
            encryptor.confirmPassword(password);
            try (OutputStream os = encryptor.getDataStream(fs);
                    InputStream is = new ByteArrayInputStream(plainXlsx)) {
                is.transferTo(os);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fs.writeFilesystem(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("엑셀 파일 암호화에 실패했습니다.", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("엑셀 파일 암호화에 실패했습니다.", e);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(boldFont);
        return style;
    }
}
