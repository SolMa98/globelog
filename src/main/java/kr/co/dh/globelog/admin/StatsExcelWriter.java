package kr.co.dh.globelog.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.co.dh.globelog.excel.ExcelExportService;
import kr.co.dh.globelog.excel.ExcelSheetData;
import org.springframework.stereotype.Component;

/**
 * 통계 화면(/my/stats, /admin/stats)의 엑셀 다운로드. 화면에 보이는 카드/차트와 같은
 * 데이터를 시트별로 나눠 담는다 — 대륙별 커버리지, 연도별 여행 횟수, 활동 통계(+ 관리자만
 * 파일 저장 용량). 실제 워크북 생성은 공용 ExcelExportService에 위임한다.
 */
@Component
public class StatsExcelWriter {

    private static final Map<String, String> CONTINENT_LABELS = Map.of(
            "ASIA", "아시아",
            "EUROPE", "유럽",
            "AFRICA", "아프리카",
            "NORTH_AMERICA", "북아메리카",
            "SOUTH_AMERICA", "남아메리카",
            "OCEANIA", "오세아니아",
            "ANTARCTICA", "남극");

    private final ExcelExportService excelExportService;

    public StatsExcelWriter(ExcelExportService excelExportService) {
        this.excelExportService = excelExportService;
    }

    // storage가 null이면(개인 통계) 파일 저장 용량 시트를 뺀다 — 관리자만 보는 데이터라서.
    public byte[] write(AdminStatsResponse coverage, ActivityStatsResponse activity, FileStorageStatsResponse storage) {
        List<ExcelSheetData> sheets = new ArrayList<>();
        sheets.add(continentSheet(coverage));
        sheets.add(yearlySheet(coverage));
        sheets.add(activitySheet(activity));
        if (storage != null) {
            sheets.add(storageSheet(storage));
        }
        return excelExportService.export(sheets, null);
    }

    private ExcelSheetData continentSheet(AdminStatsResponse coverage) {
        List<List<Object>> rows = coverage.continents().stream()
                .map(c -> List.<Object>of(
                        CONTINENT_LABELS.getOrDefault(c.continent(), c.continent()), c.visited(), c.total()))
                .toList();
        return new ExcelSheetData("대륙별 커버리지", List.of("대륙", "방문", "전체"), rows, List.of(16, 10, 10));
    }

    private ExcelSheetData yearlySheet(AdminStatsResponse coverage) {
        List<List<Object>> rows = coverage.yearly().stream()
                .map(y -> List.<Object>of(y.year(), y.count()))
                .toList();
        return new ExcelSheetData("연도별 여행 횟수", List.of("연도", "여행 횟수"), rows, List.of(10, 12));
    }

    private ExcelSheetData activitySheet(ActivityStatsResponse activity) {
        List<List<Object>> rows = List.of(
                List.<Object>of("조회수", activity.viewCount()),
                List.<Object>of("채팅 메시지", activity.chatMessageCount()),
                List.<Object>of("게시글 등록", activity.tripCreateCount()),
                List.<Object>of("게시글 수정", activity.tripUpdateCount()),
                List.<Object>of("게시글 삭제", activity.tripDeleteCount()));
        return new ExcelSheetData("활동 통계", List.of("항목", "값"), rows, List.of(16, 12));
    }

    private ExcelSheetData storageSheet(FileStorageStatsResponse storage) {
        List<List<Object>> rows = List.of(
                List.<Object>of("영구 저장 파일(게시글 사진)", storage.permanentCount(), storage.permanentBytes()),
                List.<Object>of("채팅 첨부파일(3개월 후 삭제)", storage.chatAttachmentCount(), storage.chatAttachmentBytes()),
                List.<Object>of("일주일 내 삭제 예정", storage.expiringSoonCount(), storage.expiringSoonBytes()));
        return new ExcelSheetData("파일 저장 용량", List.of("항목", "파일 수", "용량(바이트)"), rows, List.of(28, 10, 14));
    }
}
