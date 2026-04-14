package GAGYELOL.dto.approval;

import lombok.Getter;

import java.util.Map;

@Getter
public class EditFieldsRequest {
    private Map<String, String> filledFields;
}
