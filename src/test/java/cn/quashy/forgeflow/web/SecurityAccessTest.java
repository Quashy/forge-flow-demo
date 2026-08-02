package cn.quashy.forgeflow.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAccessTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void anonymousVisitorShouldBeRedirectedToLoginPage() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/login.html")));
    }

    @Test
    void protectedApiShouldReturnUnauthorizedInsteadOfHtmlRedirect() throws Exception {
        mvc.perform(get("/api/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void healthMetadataAndCsrfTokenShouldRemainPublic() throws Exception {
        mvc.perform(get("/api/meta"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/csrf"))
            .andExpect(status().isOk());
    }

    @Test
    void configuredAccessCodeShouldCreateAuthenticatedSession() throws Exception {
        mvc.perform(formLogin().user("visitor").password("forge-demo"))
            .andExpect(authenticated().withUsername("visitor"));
    }

    @Test
    void writeRequestShouldRequireCsrfToken() throws Exception {
        String requestBody = """
            {"title":"安全验证","category":"设备设施","background":"验证 CSRF 防护", "flowType":"REPORT"}
            """;

        mvc.perform(post("/api/projects")
                .with(user("visitor"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/projects")
                .with(user("visitor"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }
}
