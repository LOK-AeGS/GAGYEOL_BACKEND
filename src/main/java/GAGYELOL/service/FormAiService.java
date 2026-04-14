package GAGYELOL.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 파트 B 소유 - 양식지 관련 AI 기능
 * 양식지 필드 분석
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FormAiService {

    private final OpenAiClient openAiClient;

    /**
     * 양식지 텍스트에서 필드 목록과 설명을 추출합니다.
     * 반환 형식: {"description": "...", "fields": ["필드1", "필드2", ...]}
     */
    public String analyzeForm(String formText, String policyChunks) {
        String policySection = (policyChunks != null && !policyChunks.isBlank())
                ? "\n[관련 규정 (규정책에서 검색된 내용)]\n" + policyChunks + "\n"
                : "";

        String prompt = String.format("""
                다음은 양식지의 텍스트입니다.%s
                아래 두 가지를 JSON으로 반환하세요.
                1. description: 이 양식지를 **언제, 어떤 상황에서** 사용하는지 구체적으로 설명하세요.
                   - 관련 규정이 제공된 경우, 규정에서 명시한 사용 조건(금액 기준, 결제 수단, 첨부 의무 등)을 반영하세요.
                   - "카드/현금 중 어떤 결제 수단에 쓰는지", "누가 작성하는지", "어떤 거래에서 발생하는지"를 포함하세요.
                2. fields: 이 양식지에서 실제로 값을 입력해야 하는 빈 칸(입력 필드)의 이름 목록

                [필드 추출 규칙]
                - 테이블에서 행/열의 그룹 레이블(예: "지출인", "수령인")은 필드가 아닙니다.
                - 실제 빈 칸(입력 칸)만 필드로 추출하세요.
                - 빈 칸의 이름이 그룹 레이블 하위에 있을 경우, "그룹 레이블 + 칸 이름" 형태로 합쳐서 표현하세요.
                  예) "지출인" 그룹 아래 "성명" 칸 → "지출인 성명"
                - 이미 값이 고정된 셀(제목, 안내문, 합계 레이블 등)은 제외하세요.

                반드시 다음 JSON 형식으로만 응답하세요:
                {"description": "...", "fields": ["항목1", "항목2", ...]}

                양식지 텍스트:
                %s""", policySection, formText);

        log.info("양식지 분석 GPT 요청");
        return openAiClient.chatJson(prompt);
    }
}
