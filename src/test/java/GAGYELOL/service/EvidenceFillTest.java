package GAGYELOL.service;

import GAGYELOL.dto.FillFieldsRequest;
import GAGYELOL.dto.FillFieldsResponse;
import GAGYELOL.entity.*;
import GAGYELOL.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvidenceFillTest {

    @Mock EvidenceRepository evidenceRepository;
    @Mock EvidenceFormRepository evidenceFormRepository;
    @Mock ApprovalRequestRepository approvalRequestRepository;
    @Mock FormRepository formRepository;
    @Mock UserRepository userRepository;
    @Mock UserGroupRepository groupRepository;
    @Mock PolicyChunkVectorStore vectorStore;
    @Mock EmbeddingService embeddingService;
    @Mock EvidenceAiService evidenceAiService;
    @Mock FormAiService formAiService;
    @Mock FormFillService formFillService;

    @InjectMocks EvidenceService evidenceService;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(evidenceService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(evidenceService, "uploadDir", tempDir.toString());
    }

    @Test
    void fillFields_경로1_generatedField에_사업명_있으면_LLM_호출() throws Exception {
        // given
        Path evidenceFile = tempDir.resolve("evidence.pdf");
        Files.write(evidenceFile, "dummy".getBytes());

        Evidence evidence = Evidence.builder()
                .filePath(evidenceFile.toString())
                .fileName("evidence.pdf")
                .businessName("단국대 캡스톤 프로젝트")
                .build();

        Form form = Form.builder()
                .formName("지출결의서")
                .formFields("[\"지출인 성명\",\"내용\",\"금액\"]")
                .generatedFields("[\"내용\"]")
                .build();

        when(evidenceRepository.findById(1L)).thenReturn(Optional.of(evidence));
        when(formRepository.findById(10L)).thenReturn(Optional.of(form));
        when(formAiService.generateFieldContent("단국대 캡스톤 프로젝트", "내용"))
                .thenReturn("단국대학교 캡스톤디자인 과제 수행을 위한 물품 구매 비용입니다.");
        when(evidenceAiService.fillFormFields(any(), any(), eq(List.of("지출인 성명", "금액"))))
                .thenReturn("{\"filled\":{\"지출인 성명\":\"홍길동\",\"금액\":\"50000\"},\"missing\":[]}");

        FillFieldsRequest request = new FillFieldsRequest();
        ReflectionTestUtils.setField(request, "formIds", List.of(10L));

        // when
        FillFieldsResponse response = evidenceService.fillFields(1L, request);

        // then
        FillFieldsResponse.FormFillResult result = response.getResults().get(0);
        assertThat(result.getFilledFields()).containsEntry("내용", "단국대학교 캡스톤디자인 과제 수행을 위한 물품 구매 비용입니다.");
        assertThat(result.getFilledFields()).containsEntry("지출인 성명", "홍길동");
        assertThat(result.getFilledFields()).containsEntry("금액", "50000");
        assertThat(result.getMissingFields()).isEmpty();
        verify(formAiService).generateFieldContent("단국대 캡스톤 프로젝트", "내용");
    }

    @Test
    void fillFields_경로1_사업명_없으면_generatedField가_missing으로() throws Exception {
        // given
        Path evidenceFile = tempDir.resolve("evidence2.pdf");
        Files.write(evidenceFile, "dummy".getBytes());

        Evidence evidence = Evidence.builder()
                .filePath(evidenceFile.toString())
                .fileName("evidence2.pdf")
                .businessName(null)
                .build();

        Form form = Form.builder()
                .formName("지출결의서")
                .formFields("[\"내용\",\"금액\"]")
                .generatedFields("[\"내용\"]")
                .build();

        when(evidenceRepository.findById(2L)).thenReturn(Optional.of(evidence));
        when(formRepository.findById(20L)).thenReturn(Optional.of(form));
        when(evidenceAiService.fillFormFields(any(), any(), eq(List.of("금액"))))
                .thenReturn("{\"filled\":{\"금액\":\"30000\"},\"missing\":[]}");

        FillFieldsRequest request = new FillFieldsRequest();
        ReflectionTestUtils.setField(request, "formIds", List.of(20L));

        // when
        FillFieldsResponse response = evidenceService.fillFields(2L, request);

        // then
        FillFieldsResponse.FormFillResult result = response.getResults().get(0);
        assertThat(result.getMissingFields()).contains("내용");
        assertThat(result.getFilledFields()).containsEntry("금액", "30000");
        verify(formAiService, never()).generateFieldContent(any(), any());
    }

    @Test
    void fillFields_경로3_증빙에서_못_찾으면_수령인_사진에서_추출() throws Exception {
        // given
        Path evidenceFile = tempDir.resolve("evidence3.pdf");
        Path recipientFile = tempDir.resolve("recipient.jpg");
        Files.write(evidenceFile, "dummy".getBytes());
        Files.write(recipientFile, "dummy".getBytes());

        Evidence evidence = Evidence.builder()
                .filePath(evidenceFile.toString())
                .fileName("evidence3.pdf")
                .businessName(null)
                .recipientImagePath(recipientFile.toString())
                .build();

        Form form = Form.builder()
                .formName("지출결의서")
                .formFields("[\"지출인 성명\",\"지출인 소속\"]")
                .generatedFields("[]")
                .build();

        when(evidenceRepository.findById(3L)).thenReturn(Optional.of(evidence));
        when(formRepository.findById(30L)).thenReturn(Optional.of(form));
        // 증빙서류에서는 소속을 못 찾음
        when(evidenceAiService.fillFormFields(any(), eq("application/pdf"), any()))
                .thenReturn("{\"filled\":{\"지출인 성명\":\"홍길동\"},\"missing\":[\"지출인 소속\"]}");
        // 수령인 사진에서 소속을 찾음
        when(evidenceAiService.fillFormFields(any(), eq("image/jpeg"), eq(List.of("지출인 소속"))))
                .thenReturn("{\"filled\":{\"지출인 소속\":\"소프트웨어학과\"},\"missing\":[]}");

        FillFieldsRequest request = new FillFieldsRequest();
        ReflectionTestUtils.setField(request, "formIds", List.of(30L));

        // when
        FillFieldsResponse response = evidenceService.fillFields(3L, request);

        // then
        FillFieldsResponse.FormFillResult result = response.getResults().get(0);
        assertThat(result.getFilledFields()).containsEntry("지출인 성명", "홍길동");
        assertThat(result.getFilledFields()).containsEntry("지출인 소속", "소프트웨어학과");
        assertThat(result.getMissingFields()).isEmpty();
    }
}
