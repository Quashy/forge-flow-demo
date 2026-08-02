package cn.quashy.forgeflow.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("demo.security")
public record DemoSecurityProperties(@NotBlank String accessCode) {
}
