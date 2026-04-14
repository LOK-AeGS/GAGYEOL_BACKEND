package GAGYELOL.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PolicyChunkVectorStore {

    private final JdbcTemplate jdbcTemplate;

    public void save(Long policyId, int chunkIndex, String content, float[] embedding) {
        String vectorStr = toVectorString(embedding);
        jdbcTemplate.update(
                "INSERT INTO policy_chunk (policy_id, chunk_index, content, embedding) VALUES (?, ?, ?, ?::vector)",
                policyId, chunkIndex, content, vectorStr
        );
    }

    // pgvector의 <=> 는 코사인 거리 (0=동일, 2=정반대). 유사도 = 1 - 거리
    // 거리 0.4 이하 = 유사도 0.6 이상인 청크만 사용
    private static final double SIMILARITY_THRESHOLD = 0.4;

    /**
     * 주어진 쿼리 벡터와 코사인 유사도가 높은 청크를 policy 범위 내에서 검색.
     * 유사도 임계값 이상인 청크만 반환 (낮은 유사도 청크 제외)
     */
    public List<Map<String, Object>> findSimilar(Long policyId, float[] queryEmbedding, int topK) {
        String vectorStr = toVectorString(queryEmbedding);
        return jdbcTemplate.queryForList(
                "SELECT id, policy_id, chunk_index, content, (embedding <=> ?::vector) AS distance " +
                "FROM policy_chunk " +
                "WHERE policy_id = ? AND (embedding <=> ?::vector) <= ? " +
                "ORDER BY embedding <=> ?::vector " +
                "LIMIT ?",
                vectorStr, policyId, vectorStr, SIMILARITY_THRESHOLD, vectorStr, topK
        );
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
