package GAGYELOL.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "GA GYEOL 서버가 정상적으로 실행되었습니다! 환영합니다.";
    }
}