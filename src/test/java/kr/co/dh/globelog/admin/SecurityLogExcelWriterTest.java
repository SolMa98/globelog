package kr.co.dh.globelog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventLog;
import kr.co.dh.globelog.domain.SecurityEventType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

class SecurityLogExcelWriterTest {

    @Test
    void 헤더와_행이_정확히_기록된다() throws IOException {
        SecurityEventLog log = new SecurityEventLog(
                LocalDateTime.of(2026, 7, 23, 12, 0, 0), SecurityEventType.LOGIN_SUCCESS, SecurityActorType.ADMIN,
                1L, "admin", null, null, null, "127.0.0.1", "TestAgent");

        byte[] bytes = SecurityLogExcelWriter.write(List.of(log));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("발생 시각");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("이벤트");

            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("2026-07-23 12:00:00");
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("로그인 성공");
            assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("관리자");
            assertThat(dataRow.getCell(3).getStringCellValue()).isEqualTo("admin");
            assertThat(dataRow.getCell(7).getStringCellValue()).isEqualTo("127.0.0.1");
        }
    }

    @Test
    void 빈_목록이면_헤더만_있는_시트를_만든다() throws IOException {
        byte[] bytes = SecurityLogExcelWriter.write(List.of());

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isZero();
            assertThat(sheet.getRow(0)).isNotNull();
        }
    }

    @Test
    void targetId가_null이면_해당_셀은_비워둔다() throws IOException {
        SecurityEventLog log = new SecurityEventLog(
                LocalDateTime.now(), SecurityEventType.CHAT_LEAVE, SecurityActorType.USER,
                2L, "tester", "CHAT_ROOM", null, null, null, null);

        byte[] bytes = SecurityLogExcelWriter.write(List.of(log));

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Row dataRow = workbook.getSheetAt(0).getRow(1);
            assertThat(dataRow.getCell(5)).isNull();
        }
    }
}
