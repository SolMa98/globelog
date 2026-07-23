package kr.co.dh.globelog.admin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import kr.co.dh.globelog.domain.SecurityEventLog;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * 관리자 보안 로그 목록(필터링된 결과)을 xlsx로 직렬화한다. SXSSFWorkbook(스트리밍
 * 구현)을 써서 행 전체를 힙에 올리지 않고 임시 파일로 흘려보낸다 — 대량 로그를 통째로
 * 내려받아도 관리자 화면 메모리가 급증하지 않게 하기 위함.
 */
final class SecurityLogExcelWriter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] HEADERS = {
            "발생 시각", "이벤트", "주체 유형", "주체", "대상 유형", "대상 ID", "상세", "IP", "User-Agent"
    };

    private SecurityLogExcelWriter() {
    }

    static byte[] write(List<SecurityEventLog> logs) {
        // 100행마다 디스크로 flush(SXSSF 기본 윈도우) — 옵션을 명시적으로 줘서 기본값에
        // 암묵적으로 기대는 대신 의도를 코드에 남긴다.
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("보안 로그");
            CellStyle headerStyle = headerStyle(workbook);

            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < HEADERS.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(HEADERS[col]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (SecurityEventLog log : logs) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(
                        log.getOccurredAt() != null ? log.getOccurredAt().format(TIMESTAMP_FORMAT) : "");
                row.createCell(1).setCellValue(log.getEventType().getLabel());
                row.createCell(2).setCellValue(actorTypeLabel(log));
                row.createCell(3).setCellValue(nullToEmpty(log.getActorLabel()));
                row.createCell(4).setCellValue(nullToEmpty(log.getTargetType()));
                if (log.getTargetId() != null) {
                    row.createCell(5).setCellValue(log.getTargetId());
                }
                row.createCell(6).setCellValue(nullToEmpty(log.getDetail()));
                row.createCell(7).setCellValue(nullToEmpty(log.getIpAddress()));
                row.createCell(8).setCellValue(nullToEmpty(log.getUserAgent()));
            }

            for (int col = 0; col < HEADERS.length; col++) {
                // SXSSF는 flush된 행의 실제 너비를 계산할 수 없어 autoSizeColumn을 못 쓴다 —
                // 열 성격에 맞춰 고정 폭을 지정한다(문자 수 * 256 = 1/256문자 단위).
                sheet.setColumnWidth(col, fixedColumnWidth(col));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            // POI 5.3부터 close()가 임시 파일 정리(구 dispose())까지 함께 처리한다 —
            // try-with-resources가 블록을 빠져나가며 자동으로 호출해준다.
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("보안 로그 엑셀 생성에 실패했습니다.", e);
        }
    }

    private static int fixedColumnWidth(int col) {
        return switch (col) {
            case 0 -> 20 * 256; // 발생 시각
            case 1, 2 -> 14 * 256; // 이벤트, 주체 유형
            case 3 -> 18 * 256; // 주체
            case 4 -> 14 * 256; // 대상 유형
            case 5 -> 10 * 256; // 대상 ID
            case 6 -> 40 * 256; // 상세
            case 7 -> 16 * 256; // IP
            default -> 30 * 256; // User-Agent
        };
    }

    private static String actorTypeLabel(SecurityEventLog log) {
        return switch (log.getActorType()) {
            case ADMIN -> "관리자";
            case USER -> "일반 사용자";
            case ANONYMOUS -> "비로그인";
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static CellStyle headerStyle(Workbook workbook) {
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(boldFont);
        return style;
    }
}
