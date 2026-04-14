package GAGYELOL.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI API HTTP 호출을 담당하는 공통 클라이언트.
 * 이 파일은 인프라 코드이므로 도메인 파트(A/B/C)가 수정할 필요 없습니다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OpenAiClient {

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-4o}")
    String model;

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    /**
     * 텍스트 메시지로 GPT 호출 (JSON 응답 모드)
     */
    public String chatJson(String userMessage) {
        return chat(userMessage, true, 0.1);
    }

    /**
     * 텍스트 메시지로 GPT 호출
     */
    public String chat(String userMessage, boolean jsonMode, double temperature) {
        try {
            Map<String, Object> body = jsonMode
                    ? Map.of("model", model,
                             "messages", List.of(Map.of("role", "user", "content", userMessage)),
                             "response_format", Map.of("type", "json_object"),
                             "temperature", temperature)
                    : Map.of("model", model,
                             "messages", List.of(Map.of("role", "user", "content", userMessage)),
                             "temperature", temperature);

            return extractContent(call(body));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("OpenAI 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 이미지(base64) + 텍스트 메시지로 GPT Vision 호출
     */
    public String chatWithImage(String textPrompt, String base64Image, String mimeType) {
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", List.of(
                            Map.of("type", "text", "text", textPrompt),
                            Map.of("type", "image_url", "image_url",
                                    Map.of("url", "data:" + mimeType + ";base64," + base64Image, "detail", "high"))
                    ))),
                    "max_tokens", 4096
            );

            return extractContent(call(body));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("OpenAI Vision 호출 실패: " + e.getMessage(), e);
        }
    }

    private String call(Map<String, Object> body) throws Exception {
        String requestJson = objectMapper.writeValueAsString(body);
        String responseBody = restClient.post()
                .uri(CHAT_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestJson)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(responseBody);
        if (root.has("error")) {
            throw new RuntimeException("OpenAI API 오류: " + root.path("error").path("message").asText());
        }
        return root.path("choices").get(0).path("message").path("content").asText();
    }

    private String extractContent(String raw) {
        return raw;
    }
}
