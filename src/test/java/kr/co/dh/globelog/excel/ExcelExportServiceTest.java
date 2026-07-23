package kr.co.dh.globelog.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

/**
 * 관리자 화면 어디서든 재사용할 공용 엑셀 내보내기 서비스. 비밀번호 없는 경로와
 * 있는 경로(POI POIFS 암호화)가 각각 정상 동작하는지, 그리고 틀린 비밀번호로는
 * 못 여는지(=실제로 암호화됐는지)를 검증한다.
 */
class ExcelExportServiceTest {

    private final ExcelExportService service = new ExcelExportService();

    @Test
    void 비밀번호_없이_생성하면_바로_열린다() throws Exception {
        byte[] bytes = service.export("시트1", List.of("이름", "나이"),
                List.of(Arrays.asList("홍길동", 30)), List.of(20, 10), null);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("이름");
            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("홍길동");
            assertThat(dataRow.getCell(1).getNumericCellValue()).isEqualTo(30.0);
        }
    }

    @Test
    void 비밀번호를_주면_같은_비밀번호로만_열린다() throws Exception {
        byte[] bytes = service.export("시트1", List.of("이름"), List.of(List.of("홍길동")), List.of(20), "pw1234!");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes), "pw1234!")) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("홍길동");
        }

        assertThatThrownBy(() -> WorkbookFactory.create(new ByteArrayInputStream(bytes), "wrong-password"))
                .isInstanceOf(EncryptedDocumentException.class);
    }

    @Test
    void null_값은_빈_셀로_남는다() throws Exception {
        byte[] bytes = service.export("시트1", List.of("컬럼"), List.of(java.util.Collections.singletonList(null)),
                List.of(20), null);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Row dataRow = workbook.getSheetAt(0).getRow(1);
            assertThat(dataRow.getCell(0)).isNull();
        }
    }
}
