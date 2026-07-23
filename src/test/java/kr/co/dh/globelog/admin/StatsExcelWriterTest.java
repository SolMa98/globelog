package kr.co.dh.globelog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;
import kr.co.dh.globelog.excel.ExcelExportService;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

class StatsExcelWriterTest {

    private final StatsExcelWriter writer = new StatsExcelWriter(new ExcelExportService());

    private AdminStatsResponse coverage() {
        return new AdminStatsResponse(2, 236,
                List.of(new ContinentCoverage("ASIA", 1, 51), new ContinentCoverage("EUROPE", 1, 44)),
                List.of(new YearlyTripCount(2025, 3), new YearlyTripCount(2026, 1)));
    }

    private ActivityStatsResponse activity() {
        return new ActivityStatsResponse(100, 20, 5, 2, 1);
    }

    private FileStorageStatsResponse storage() {
        return new FileStorageStatsResponse(1000, 3, 500, 2, 100, 1);
    }

    @Test
    void 관리자용은_네_개_시트를_모두_만든다() throws Exception {
        byte[] bytes = writer.write(coverage(), activity(), storage());

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(4);
            assertThat(workbook.getSheet("대륙별 커버리지")).isNotNull();
            assertThat(workbook.getSheet("연도별 여행 횟수")).isNotNull();
            assertThat(workbook.getSheet("활동 통계")).isNotNull();
            assertThat(workbook.getSheet("파일 저장 용량")).isNotNull();

            assertThat(workbook.getSheet("대륙별 커버리지").getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("아시아");
            assertThat(workbook.getSheet("활동 통계").getRow(1).getCell(1).getNumericCellValue()).isEqualTo(100.0);
            assertThat(workbook.getSheet("파일 저장 용량").getRow(1).getCell(2).getNumericCellValue())
                    .isEqualTo(1000.0);
        }
    }

    @Test
    void 개인용은_storage가_null이면_파일저장용량_시트를_뺀다() throws Exception {
        byte[] bytes = writer.write(coverage(), activity(), null);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheet("파일 저장 용량")).isNull();
        }
    }
}
