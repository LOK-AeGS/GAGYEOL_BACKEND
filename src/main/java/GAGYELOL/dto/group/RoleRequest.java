package GAGYELOL.dto.group;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class RoleRequest {

    @NotBlank(message = "역할 이름은 필수입니다.")
    private String roleName;

    @NotNull(message = "결재 순서는 필수입니다.")
    @Min(value = 0, message = "결재 순서는 0 이상이어야 합니다.")
    private Integer approvalOrder;
}
