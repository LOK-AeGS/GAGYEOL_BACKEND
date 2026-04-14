package GAGYELOL.dto.group;

import lombok.Getter;

import java.util.List;

@Getter
public class CreateGroupRequest {
    private String name;
    // 역할 목록 (approval_order 오름차순으로 입력)
    // ex) ["부원", "차장", "부장"]
    private List<String> roles;
}
