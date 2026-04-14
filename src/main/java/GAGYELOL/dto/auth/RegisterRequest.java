package GAGYELOL.dto.auth;

import lombok.Getter;

@Getter
public class RegisterRequest {
    private String email;
    private String password;
    private String name;
}
