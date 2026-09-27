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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
    @MockitoBean IntelligencePort intelligence;

    @Test
    void exposesPublicTokenAndProtectedResourceFlow() throws Exception {
        String admin = token("admin@ford.com");
        String customer = token("customer@ford.com");
        UUID customerId = UUID.randomUUID();
        String payload = "{\"customerId\":\"" + customerId + "\",\"vin\":\"1FMCU9GDXMUA12345\"}";

        mvc.perform(post("/api/v1/journeys").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/journeys").header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());

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
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/token").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
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

