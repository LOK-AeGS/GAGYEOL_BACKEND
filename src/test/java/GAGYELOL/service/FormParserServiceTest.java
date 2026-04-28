package GAGYELOL.service;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class FormParserServiceTest {

    private final FormParserService parser = new FormParserService();

    @Test
    void 현금지출증빙서_지출인_수령인_병합셀_파싱() throws Exception {
        File file = new File("uploads/forms/c7aeb8d4-bf9b-4847-85ee-5ab1cb9e246a_현금지출증빙서.docx");
        if (!file.exists()) {
            System.out.println("파일 없음, 스킵");
            return;
        }

        String text = parser.extractText(file);
        System.out.println("=== 추출된 텍스트 ===");
        System.out.println(text);
        System.out.println("====================");

        // 지출인 레이블이 하위 행들에도 반복되는지 확인
        long 지출인Count = text.lines()
                .filter(line -> line.contains("지출인"))
                .count();
        long 수령인Count = text.lines()
                .filter(line -> line.contains("수령인"))
                .count();

        System.out.println("'지출인' 포함 행 수: " + 지출인Count);
        System.out.println("'수령인' 포함 행 수: " + 수령인Count);

        assertThat(지출인Count).isGreaterThan(1); // 병합 셀이 반복되면 2행 이상
        assertThat(수령인Count).isGreaterThan(1);
    }
}
