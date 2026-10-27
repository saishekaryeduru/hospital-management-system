package com.saishekar.hospital;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientAllergyApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String role) throws Exception {
        String email = role.toLowerCase() + "-allergy-" + System.nanoTime() + "@hospital.test";
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\","
                + "\"displayName\":\"Test User\",\"role\":\"" + role + "\"}";
        MvcResult res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private String patientJson(String firstName, String allergiesJson) {
        String allergies = allergiesJson == null ? "" : ",\"allergies\":" + allergiesJson;
        return "{\"firstName\":\"" + firstName + "\",\"lastName\":\"Sharma\","
                + "\"dateOfBirth\":\"1990-04-12\",\"gender\":\"FEMALE\",\"phone\":\"555-0101\","
                + "\"bloodGroup\":\"O_POSITIVE\"" + allergies + "}";
    }

    private long createPatient(String token, String firstName, String allergiesJson) throws Exception {
        MvcResult res = mvc.perform(post("/api/patients").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(patientJson(firstName, allergiesJson)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private static final String TWO_ALLERGIES = "["
            + "{\"substance\":\"Penicillin\",\"reaction\":\"Hives\",\"severity\":\"MODERATE\"},"
            + "{\"substance\":\"Latex\",\"reaction\":\"Anaphylaxis\",\"severity\":\"LIFE_THREATENING\"}"
            + "]";

    @Test
    void structuredAllergiesRoundTripInWrittenOrder() throws Exception {
        String receptionist = register("RECEPTIONIST");
        long id = createPatient(receptionist, "AllergyAsha", TWO_ALLERGIES);

        mvc.perform(get("/api/patients/" + id).header("Authorization", "Bearer " + receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allergies.length()").value(2))
                .andExpect(jsonPath("$.allergies[0].substance").value("Penicillin"))
                .andExpect(jsonPath("$.allergies[0].reaction").value("Hives"))
                .andExpect(jsonPath("$.allergies[0].severity").value("MODERATE"))
                .andExpect(jsonPath("$.allergies[1].substance").value("Latex"))
                .andExpect(jsonPath("$.allergies[1].severity").value("LIFE_THREATENING"));
    }

    @Test
    void updateReplacesTheAllergyListAsAWhole() throws Exception {
        String receptionist = register("RECEPTIONIST");
        long id = createPatient(receptionist, "AllergyMeera", TWO_ALLERGIES);

        // Penicillin is dropped and latex is regraded: the saved chart
        // must show exactly the replacement list, not a merge.
        String replacement = "[{\"substance\":\"Latex\",\"severity\":\"SEVERE\"}]";
        mvc.perform(put("/api/patients/" + id).header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON).content(patientJson("AllergyMeera", replacement)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allergies.length()").value(1))
                .andExpect(jsonPath("$.allergies[0].substance").value("Latex"))
                .andExpect(jsonPath("$.allergies[0].severity").value("SEVERE"))
                .andExpect(jsonPath("$.allergies[0].reaction", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void patientWithoutAllergiesReturnsAnEmptyList() throws Exception {
        String receptionist = register("RECEPTIONIST");
        long id = createPatient(receptionist, "AllergyKiran", null);

        mvc.perform(get("/api/patients/" + id).header("Authorization", "Bearer " + receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allergies.length()").value(0));
    }

    @Test
    void duplicateSubstanceIsRejectedIgnoringCase() throws Exception {
        String receptionist = register("RECEPTIONIST");
        String duplicates = "["
                + "{\"substance\":\"Penicillin\",\"severity\":\"MILD\"},"
                + "{\"substance\":\" penicillin \",\"severity\":\"SEVERE\"}"
                + "]";
        mvc.perform(post("/api/patients").header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON).content(patientJson("AllergyDup", duplicates)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("duplicate substance")));
    }

    @Test
    void blankSubstanceAndMissingSeverityAreRejected() throws Exception {
        String receptionist = register("RECEPTIONIST");
        mvc.perform(post("/api/patients").header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientJson("AllergyBlank", "[{\"substance\":\"\",\"severity\":\"MILD\"}]")))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/patients").header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientJson("AllergyNoSeverity", "[{\"substance\":\"Peanuts\"}]")))
                .andExpect(status().isBadRequest());
    }
}
