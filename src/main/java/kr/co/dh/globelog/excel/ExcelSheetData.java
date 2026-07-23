package kr.co.dh.globelog.excel;

import java.util.List;

/**
 * ExcelExportService가 시트 하나를 만드는 데 필요한 데이터. columnWidthsChars는
 * null이면 기본 너비를 쓴다(문자 수 단위 — 실제 셀 값은 setCellValue가 알아서 처리).
 */
public record ExcelSheetData(String sheetName, List<String> headers, List<List<Object>> rows,
        List<Integer> columnWidthsChars) {

    public ExcelSheetData(String sheetName, List<String> headers, List<List<Object>> rows) {
        this(sheetName, headers, rows, null);
    }
}
