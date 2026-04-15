package GAGYELOL.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 파트 C 소유 - 증빙서류 관련 AI 기능
 * 결제유형 분류, 필드 자동 채우기, 양식지 선택
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EvidenceAiService {

    private final OpenAiClient openAiClient;

    /**
     * 증빙서류 내용을 보고 카드/현금 결제 유형을 분류합니다.
     * 반환값: "CARD" 또는 "CASH"
     */
    public String classifyPaymentType(String evidenceText) {
        String prompt = String.format("""
                아래 증빙서류 내용을 보고 결제 수단을 분류하세요.

                [증빙서류 내용]
                %s

                분류 기준:
                - 카드 결제: 신용카드, 체크카드, 카드 매출전표, 카드승인번호 등이 언급된 경우 → "CARD"
                - 현금 결제: 현금영수증, 현금 지출, 계좌이체, 무통장입금 등이 언급된 경우 → "CASH"
                - 판단이 어려우면 내용상 더 가까운 쪽으로 분류하세요.

                반드시 다음 JSON 형식으로만 응답하세요:
                {"paymentType": "CARD" 또는 "CASH", "reason": "판단 근거를 한 문장으로"}
                """, evidenceText);

        log.info("결제유형 분류 GPT 요청");
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String result = openAiClient.chatJson(prompt);
            return mapper.readTree(result).path("paymentType").asText("CASH");
        } catch (Exception e) {
            throw new RuntimeException("결제 유형 분류 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 증빙서류 내용과 양식지 필드 목록을 바탕으로 각 필드에 채울 값을 추출합니다.
     * 반환 형식: {"filled": {"필드명": "값"}, "missing": ["필드명", ...]}
     */
    public String fillFormFields(String evidenceText, List<String> formFields) {
        String fieldList = String.join("\n", formFields.stream().map(f -> "- " + f).toList());
        String prompt = String.format("""
                당신은 회계 처리 전문가입니다.

                아래는 작성해야 할 양식지의 필드 목록입니다.

                [양식지 필드 목록]
                %s

                이제 아래 증빙서류를 살펴보고, 위 각 필드에 해당하는 값을 찾아 채우세요.

                [증빙서류 내용]
                %s

                작성 규칙:
                - 필드명과 증빙서류의 표현이 달라도 의미가 같으면 채우세요. (예: "품목" → "상품명", "구매일" → "날짜", "합계" → "금액")
                - 증빙서류에 값이 존재하지 않는 경우에만 "missing"에 넣으세요. 표현이 다른 것은 missing이 아닙니다.
                - 값을 아예 없는 정보로 만들어내는 것은 하지 마세요.
                - 날짜는 YYYY-MM-DD 형식으로 표기하세요.
                - 금액은 숫자만(쉼표·원 표시 없이) 표기하세요.

                반드시 다음 JSON 형식으로만 응답하세요:
                {"filled": {"필드명": "값", ...}, "missing": ["필드명", ...]}
                """, fieldList, evidenceText);

        log.info("필드 자동 채우기 GPT 요청 - 필드 수: {}", formFields.size());
        return openAiClient.chatJson(prompt);
    }

    /**
     * 증빙서류 내용 + 규정 청크 + 양식지 목록을 바탕으로 적절한 양식지를 선택합니다.
     * 반환 형식: {"formId": 숫자, "reason": "선택 이유"}
     */
    public String selectForm(String evidenceText, String policyChunks, String formListDescription, String paymentType) {
        String paymentLabel = "CARD".equals(paymentType) ? "카드 결제" : "현금 결제";
        String prompt = String.format("""
                당신은 회계 처리 전문가입니다. 아래 정보를 단계적으로 검토하여 가장 적합한 양식지 하나를 선택하세요.

                [결제 수단] %s

                [증빙서류 내용]
                %s

                [관련 규정]
                %s

                [사용 가능한 양식지 목록]
                %s

                반드시 다음 JSON 형식으로만 응답하세요:
                {"formId": 숫자, "reason": "선택 이유를 한국어로 설명"}
                """, paymentLabel, evidenceText, policyChunks, formListDescription);

        log.info("양식지 선택 GPT 요청");
        return openAiClient.chatJson(prompt);
    }
}
