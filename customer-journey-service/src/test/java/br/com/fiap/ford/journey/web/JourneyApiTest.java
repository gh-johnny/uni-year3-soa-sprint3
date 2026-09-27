package br.com.fiap.ford.journey.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.ford.journey.application.IntelligencePort;
import br.com.fiap.ford.journey.infra.IntelligenceUnavailableException;
import br.com.fiap.ford.journey.security.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class JourneyApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired TokenService tokens;
    @MockitoBean IntelligencePort intelligence;

    @Test
    void exposesPublicTokenAndProtectedResourceFlow() throws Exception {
        String admin = token("admin@ford.com");
        String customer = token("customer@ford.com");
        UUID customerId = UUID.randomUUID();
        String payload = "{\"customerId\":\"" + customerId + "\",\"vin\":\"1FMCU9GDXMUA12345\"}";

        mvc.perform(post("/api/v1/journeys").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/v1/journeys"));
        mvc.perform(post("/api/v1/journeys").header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.type").value("https://ford.example/problems/403"));

        String response = mvc.perform(post("/api/v1/journeys").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("DETECTED"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(mapper.readTree(response).get("id").asText());

        mvc.perform(get("/api/v1/journeys/{id}", id).header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vin").value("1FMCU9GDXMUA12345"));
        mvc.perform(post("/api/v1/journeys").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business rule violation"));
    }

    @Test
    void validatesRequestsAndStandardizesNotFound() throws Exception {
        String admin = token("admin@ford.com");
        mvc.perform(post("/api/v1/journeys").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value("Invalid request"));
        mvc.perform(get("/api/v1/journeys/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void transitionsAndRequestsRecommendation() throws Exception {
        String admin = token("admin@ford.com");
        UUID id = createJourney(admin, "1FMCU9GDXMUA54321");
        mvc.perform(post("/api/v1/journeys/{id}/transitions", id).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"CONTACTED\",\"nextAction\":\"ANALYZE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONTACTED"));

        when(intelligence.evaluate(any())).thenReturn(new IntelligencePort.RecommendationResult(
                "1FMCU9GDXMUA54321", "MEDIUM", .4, "SCHEDULE", List.of("oil")));
        mvc.perform(post("/api/v1/journeys/{id}/recommendation", id).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(telemetry("1FMCU9GDXMUA54321")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("SCHEDULE"));
    }

    @Test
    void mapsDependencyFailureTo503() throws Exception {
        String admin = token("admin@ford.com");
        UUID id = createJourney(admin, "1FMCU9GDXMUA77777");
        when(intelligence.evaluate(any())).thenThrow(new IntelligenceUnavailableException(new RuntimeException()));
        mvc.perform(post("/api/v1/journeys/{id}/recommendation", id).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(telemetry("1FMCU9GDXMUA77777")))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void rejectsBadCredentialsAndMalformedLogin() throws Exception {
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin@ford.com\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("invalid credentials"));
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidAndExpiredTokensReturnProblemDetails() throws Exception {
        String expired = tokens.issue("admin@ford.com", Set.of("ADMIN"), Instant.now().minusSeconds(7200))
                .accessToken();
        for (String token : List.of("not-a-jwt", expired)) {
            mvc.perform(get("/api/v1/journeys/{id}", UUID.randomUUID())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                    .andExpect(header().exists("WWW-Authenticate"))
                    .andExpect(jsonPath("$.type").value("https://ford.example/problems/401"))
                    .andExpect(jsonPath("$.title").isNotEmpty())
                    .andExpect(jsonPath("$.detail").isNotEmpty());
        }
    }

    @Test
    void openApiDocumentsLoginAsPublicAndKeepsProtectedResourcesSecured() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/token'].post.security").isEmpty())
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }

    @Test
    void frameworkErrorsUseTheSameProblemDetailsContract() throws Exception {
        String admin = token("admin@ford.com");
        mvc.perform(post("/api/v1/journeys").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.type").value("https://ford.example/problems/400"))
                .andExpect(jsonPath("$.instance").value("/api/v1/journeys"));
        mvc.perform(get("/api/v1/missing").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://ford.example/problems/404"))
                .andExpect(jsonPath("$.instance").value("/api/v1/missing"));
    }

    @Test
    void unexpectedFailuresDoNotLeakInternalDetails() throws Exception {
        String admin = token("admin@ford.com");
        UUID id = createJourney(admin, "1FMCU9GDXMUA88888");
        when(intelligence.evaluate(any())).thenThrow(new IllegalStateException("private database connection"));
        mvc.perform(post("/api/v1/journeys/{id}/recommendation", id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(telemetry("1FMCU9GDXMUA88888")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("https://ford.example/problems/500"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    private String token(String user) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"Ford@123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(body);
        return json.get("accessToken").asText();
    }

    private UUID createJourney(String token, String vin) throws Exception {
        String body = mvc.perform(post("/api/v1/journeys").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"" + UUID.randomUUID() + "\",\"vin\":\"" + vin + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(body).get("id").asText());
    }

    private String telemetry(String vin) {
        return "{\"vin\":\"" + vin + "\",\"odometerKm\":100,\"oilLifePercent\":20,"
                + "\"batteryVoltage\":12,\"engineTemperatureC\":100,\"diagnosticCodes\":[]}";
    }
}
