package GAGYELOL.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 파트 A 소유 - 규정책 관련 AI 기능
 * Vision OCR, 텍스트 추출
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PolicyAiService {

    private final OpenAiClient openAiClient;

    /**
     * 이미지(base64 PNG)에서 텍스트를 추출합니다.
     */
    public String extractTextFromImage(String base64Image) {
        return extractTextFromImage(base64Image, "image/png");
    }

    /**
     * 이미지(base64)에서 텍스트를 추출합니다.
     */
    public String extractTextFromImage(String base64Image, String mimeType) {
        log.info("Vision OCR 요청 - mimeType: {}", mimeType);
        return openAiClient.chatWithImage(
                "이 이미지에 있는 모든 텍스트를 빠짐없이 추출해주세요. 서식이나 설명 없이 텍스트만 출력하세요.",
                base64Image,
                mimeType
        );
    }
}
