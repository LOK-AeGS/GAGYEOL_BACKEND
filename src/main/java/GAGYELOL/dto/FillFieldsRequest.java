package GAGYELOL.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class FillFieldsRequest {
    private List<Long> formIds; // 사용자가 선택한 양식지 ID 목록
}
