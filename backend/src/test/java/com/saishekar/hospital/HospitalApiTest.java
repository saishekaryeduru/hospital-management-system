package com.saishekar.hospital;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HospitalApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String email, String role) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\","
                + "\"displayName\":\"Test User\",\"role\":\"" + role + "\"}";
        MvcResult res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        JsonNode node = json.readTree(res.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    private String patientJson(String firstName) {
        return "{\"firstName\":\"" + firstName + "\",\"lastName\":\"Sharma\","
                + "\"dateOfBirth\":\"1990-04-12\",\"gender\":\"FEMALE\",\"phone\":\"555-0101\","
                + "\"email\":\"asha@example.com\",\"bloodGroup\":\"O_POSITIVE\","
                + "\"address\":\"12 Lake Ave\",\"emergencyContactName\":\"Ravi Sharma\","
                + "\"emergencyContactPhone\":\"555-0102\",\"allergies\":\"Penicillin\"}";
    }

    @Test
    void receptionistRegistersPatientAndDoctorCanReadIt() throws Exception {
        String receptionist = register("desk-" + System.nanoTime() + "@hospital.test", "RECEPTIONIST");
        MvcResult created = mvc.perform(post("/api/patients").header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON).content(patientJson("Asha")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Asha"))
                .andExpect(jsonPath("$.bloodGroup").value("O_POSITIVE"))
                .andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        String doctor = register("doc-" + System.nanoTime() + "@hospital.test", "DOCTOR");
        mvc.perform(get("/api/patients/" + id).header("Authorization", "Bearer " + doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Sharma"));

        mvc.perform(get("/api/patients?q=asha").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void doctorCannotRegisterPatientAndNurseCannotDelete() throws Exception {
        String doctor = register("doc2-" + System.nanoTime() + "@hospital.test", "DOCTOR");
        mvc.perform(post("/api/patients").header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON).content(patientJson("Meera")))
                .andExpect(status().isForbidden());

        String receptionist = register("desk2-" + System.nanoTime() + "@hospital.test", "RECEPTIONIST");
        MvcResult created = mvc.perform(post("/api/patients").header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON).content(patientJson("Meera")))
                .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        String nurse = register("nurse-" + System.nanoTime() + "@hospital.test", "NURSE");
        mvc.perform(delete("/api/patients/" + id).header("Authorization", "Bearer " + nurse))
                .andExpect(status().isForbidden());

        // A nurse updating contact details is part of routine care.
        String updated = patientJson("Meera").replace("555-0101", "555-0199");
        mvc.perform(put("/api/patients/" + id).header("Authorization", "Bearer " + nurse)
                        .contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("555-0199"));

        String admin = register("admin-" + System.nanoTime() + "@hospital.test", "ADMIN");
        mvc.perform(delete("/api/patients/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void unauthenticatedAndInvalidRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/patients")).andExpect(status().isForbidden());

        String receptionist = register("desk3-" + System.nanoTime() + "@hospital.test", "RECEPTIONIST");
        String futureDob = patientJson("Kiran").replace("1990-04-12", "2999-01-01");
        mvc.perform(post("/api/patients").header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON).content(futureDob))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.dateOfBirth").exists());

        mvc.perform(get("/api/patients/999999").header("Authorization", "Bearer " + receptionist))
                .andExpect(status().isNotFound());
    }
}
