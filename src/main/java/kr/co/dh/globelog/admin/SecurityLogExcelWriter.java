package kr.co.dh.globelog.admin;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import kr.co.dh.globelog.domain.SecurityEventLog;
import kr.co.dh.globelog.excel.ExcelExportService;
import org.springframework.stereotype.Component;

/**
 * 관리자 보안 로그 목록(필터링된 결과)을 xlsx로 직렬화한다. 실제 워크북 생성/암호화는
 * 공용 ExcelExportService가 맡고, 여기서는 SecurityEventLog → 헤더/행 데이터 변환만 한다.
 */
@Component
class SecurityLogExcelWriter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> HEADERS = List.of(
            "발생 시각", "이벤트", "주체 유형", "주체", "대상 유형", "대상 ID", "상세", "IP", "User-Agent");
    private static final List<Integer> COLUMN_WIDTHS_CHARS = List.of(20, 14, 14, 18, 14, 10, 40, 16, 30);

    private final ExcelExportService excelExportService;

    SecurityLogExcelWriter(ExcelExportService excelExportService) {
        this.excelExportService = excelExportService;
    }

    byte[] write(List<SecurityEventLog> logs, String password) {
        List<List<Object>> rows = logs.stream().map(this::toRow).toList();
        return excelExportService.export("보안 로그", HEADERS, rows, COLUMN_WIDTHS_CHARS, password);
    }

    private List<Object> toRow(SecurityEventLog log) {
        List<Object> row = new ArrayList<>(HEADERS.size());
        row.add(log.getOccurredAt() != null ? log.getOccurredAt().format(TIMESTAMP_FORMAT) : "");
        row.add(log.getEventType().getLabel());
        row.add(actorTypeLabel(log));
        row.add(nullToEmpty(log.getActorLabel()));
        row.add(nullToEmpty(log.getTargetType()));
        row.add(log.getTargetId());
        row.add(nullToEmpty(log.getDetail()));
        row.add(nullToEmpty(log.getIpAddress()));
        row.add(nullToEmpty(log.getUserAgent()));
        return row;
    }

    private String actorTypeLabel(SecurityEventLog log) {
        return switch (log.getActorType()) {
            case ADMIN -> "관리자";
            case USER -> "일반 사용자";
            case ANONYMOUS -> "비로그인";
        };
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
