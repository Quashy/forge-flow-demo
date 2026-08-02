package cn.quashy.forgeflow.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DemoSecurityProperties.class)
public class SecurityConfig {

    private static final String DEMO_USERNAME = "visitor";

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(DemoSecurityProperties properties,
                                          PasswordEncoder passwordEncoder) {
        UserDetails visitor = User.withUsername(DEMO_USERNAME)
            .password(passwordEncoder.encode(properties.accessCode()))
            .roles("DEMO")
            .build();
        return new InMemoryUserDetailsManager(visitor);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        LoginUrlAuthenticationEntryPoint loginEntryPoint =
            new LoginUrlAuthenticationEntryPoint("/login.html");
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/login.html", "/login.css", "/login.js",
                    "/api/csrf", "/api/meta", "/error"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login.html?error")
                .permitAll()
            )
            .logout(logout -> logout.logoutSuccessUrl("/login.html?logout"))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> {
                    if (isProtectedApiRequest(request)) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                        return;
                    }
                    loginEntryPoint.commence(request, response, exception);
                })
            );
        return http.build();
    }

    private static boolean isProtectedApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }
}
