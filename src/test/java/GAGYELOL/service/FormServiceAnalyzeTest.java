package GAGYELOL.service;

import GAGYELOL.dto.FormUploadResponse;
import GAGYELOL.entity.Form;
import GAGYELOL.entity.UserGroup;
import GAGYELOL.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class FormServiceAnalyzeTest {

    @Mock FormRepository formRepository;
    @Mock FormAiService formAiService;
    @Mock FormParserService formParserService;
    @Mock EmbeddingService embeddingService;
    @Mock PolicyChunkVectorStore vectorStore;
    @Mock GAGYELOL.repository.UserGroupRepository groupRepository;
    @Mock GAGYELOL.repository.UserRepository userRepository;
    @Mock GAGYELOL.repository.GroupMemberRepository groupMemberRepository;

    @InjectMocks FormService formService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(formService, "objectMapper", new ObjectMapper());
    }

    @Test
    void reanalyze_generatedFields_파싱_및_저장() throws Exception {
        // given
        String analysisJson = """
            {"description":"테스트","fields":["지출인 성명","내용","금액"],"generatedFields":["내용"]}
            """;
        Form form = Form.builder()
                .formName("테스트양식")
                .filePath("dummy.docx")
                .formFields("[]")
                .generatedFields("[]")
                .paymentType("BOTH")
                .build();

        when(formRepository.findById(1L)).thenReturn(Optional.of(form));
        when(formParserService.extractText(any())).thenReturn("양식지 텍스트");
        when(formAiService.analyzeForm(any(), any())).thenReturn(analysisJson);

        // when
        FormUploadResponse response = formService.reanalyze(1L, null);

        // then
        assertThat(response.getFields()).containsExactlyInAnyOrder("지출인 성명", "내용", "금액");
        assertThat(response.getGeneratedFields()).containsExactly("내용");
    }

    @Test
    void reanalyze_generatedFields_없으면_빈_리스트() throws Exception {
        // given: GPT가 generatedFields를 반환하지 않은 경우 (이전 버전 호환)
        String analysisJson = """
            {"description":"테스트","fields":["지출인 성명","금액"]}
            """;
        Form form = Form.builder()
                .formName("테스트양식")
                .filePath("dummy.docx")
                .formFields("[]")
                .paymentType("BOTH")
                .build();

        when(formRepository.findById(1L)).thenReturn(Optional.of(form));
        when(formParserService.extractText(any())).thenReturn("양식지 텍스트");
        when(formAiService.analyzeForm(any(), any())).thenReturn(analysisJson);

        // when
        FormUploadResponse response = formService.reanalyze(1L, null);

        // then
        assertThat(response.getGeneratedFields()).isEmpty();
    }
}
