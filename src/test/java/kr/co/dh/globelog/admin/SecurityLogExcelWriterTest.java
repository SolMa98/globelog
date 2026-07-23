package kr.co.dh.globelog.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import kr.co.dh.globelog.domain.SecurityActorType;
import kr.co.dh.globelog.domain.SecurityEventLog;
import kr.co.dh.globelog.domain.SecurityEventType;
import kr.co.dh.globelog.excel.ExcelExportService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

class SecurityLogExcelWriterTest {

    private final SecurityLogExcelWriter writer = new SecurityLogExcelWriter(new ExcelExportService());

    @Test
    void 헤더와_행이_정확히_기록된다() throws IOException {
        SecurityEventLog log = new SecurityEventLog(
                LocalDateTime.of(2026, 7, 23, 12, 0, 0), SecurityEventType.LOGIN_SUCCESS, SecurityActorType.ADMIN,
                1L, "admin", null, null, null, "127.0.0.1", "TestAgent");

        byte[] bytes = writer.write(List.of(log), null);

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
        byte[] bytes = writer.write(List.of(), null);

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

        byte[] bytes = writer.write(List.of(log), null);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Row dataRow = workbook.getSheetAt(0).getRow(1);
            assertThat(dataRow.getCell(5)).isNull();
        }
    }

    @Test
    void 비밀번호를_주면_암호화된_파일이_생성된다() {
        SecurityEventLog log = new SecurityEventLog(
                LocalDateTime.now(), SecurityEventType.LOGIN_SUCCESS, SecurityActorType.ADMIN,
                1L, "admin", null, null, null, null, null);

        byte[] bytes = writer.write(List.of(log), "secret123!");

        // 비밀번호 없이 열면(WorkbookFactory가 기본 빈 비밀번호로 시도) 실패해야 한다 —
        // 즉 평문 xlsx가 아니라 실제로 암호화된 파일임을 확인.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> WorkbookFactory.create(new ByteArrayInputStream(bytes)))
                .isInstanceOf(Exception.class);
    }
}
