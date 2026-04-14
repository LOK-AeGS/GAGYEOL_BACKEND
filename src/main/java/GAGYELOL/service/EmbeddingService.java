package GAGYELOL.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmbeddingService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/embeddings";

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.embedding-model:text-embedding-3-small}")
    private String embeddingModel;

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public float[] embed(String text) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", embeddingModel,
                    "input", text
            );

            String requestJson = objectMapper.writeValueAsString(requestBody);

            String responseBody = restClient.post()
                    .uri(OPENAI_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error")) {
                String msg = root.path("error").path("message").asText();
                throw new RuntimeException("OpenAI Embedding API 오류: " + msg);
            }

            JsonNode embeddingNode = root.path("data").get(0).path("embedding");
            float[] result = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                result[i] = (float) embeddingNode.get(i).asDouble();
            }
            return result;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("임베딩 생성 실패: " + e.getMessage(), e);
        }
    }
}
