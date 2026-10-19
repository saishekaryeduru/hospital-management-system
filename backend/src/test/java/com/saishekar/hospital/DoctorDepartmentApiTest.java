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
class DoctorDepartmentApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String role) throws Exception {
        String email = role.toLowerCase() + "-" + System.nanoTime() + "@hospital.test";
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\","
                + "\"displayName\":\"Test User\",\"role\":\"" + role + "\"}";
        MvcResult res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private long createDepartment(String token, String name) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"description\":\"Test department\"}";
        MvcResult res = mvc.perform(post("/api/departments").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.doctorCount").value(0))
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private String doctorJson(String firstName, long departmentId, String email) {
        return "{\"firstName\":\"" + firstName + "\",\"lastName\":\"Rao\","
                + "\"specialization\":\"Cardiology\",\"departmentId\":" + departmentId + ","
                + "\"phone\":\"555-0201\",\"email\":\"" + email + "\",\"active\":true}";
    }

    @Test
    void adminManagesDepartmentsAndFrontDeskMaintainsTheDoctorDirectory() throws Exception {
        String admin = register("ADMIN");
        long cardiology = createDepartment(admin, "Cardiology-" + System.nanoTime());

        // Clinical staff read the directory but cannot add to it, and only
        // an administrator owns the department structure itself.
        String doctor = register("DOCTOR");
        mvc.perform(post("/api/doctors").header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorJson("Anita", cardiology, "anita-" + System.nanoTime() + "@hospital.test")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/departments").header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Neurology\",\"description\":\"Brain and nerves\"}"))
                .andExpect(status().isForbidden());

        String receptionist = register("RECEPTIONIST");
        String email = "anita-" + System.nanoTime() + "@hospital.test";
        MvcResult created = mvc.perform(post("/api/doctors").header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON).content(doctorJson("Anita", cardiology, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.departmentName").exists())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        long doctorId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        String nurse = register("NURSE");
        mvc.perform(get("/api/doctors/" + doctorId).header("Authorization", "Bearer " + nurse))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialization").value("Cardiology"));

        MvcResult departments = mvc.perform(get("/api/departments").header("Authorization", "Bearer " + nurse))
                .andExpect(status().isOk()).andReturn();
        boolean counted = false;
        for (JsonNode entry : json.readTree(departments.getResponse().getContentAsString())) {
            if (entry.get("id").asLong() == cardiology) {
                counted = entry.get("doctorCount").asLong() == 1;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(counted,
                "department list should report one doctor for cardiology");
    }

    @Test
    void directorySearchAndDepartmentFilterCompose() throws Exception {
        String admin = register("ADMIN");
        long cardiology = createDepartment(admin, "Cardiology-" + System.nanoTime());
        long pediatrics = createDepartment(admin, "Pediatrics-" + System.nanoTime());

        mvc.perform(post("/api/doctors").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorJson("Anita", cardiology, "search-a-" + System.nanoTime() + "@hospital.test")))
                .andExpect(status().isCreated());
        String pedsDoctor = doctorJson("Vikram", pediatrics, "search-v-" + System.nanoTime() + "@hospital.test")
                .replace("\"Cardiology\"", "\"Pediatrics\"");
        mvc.perform(post("/api/doctors").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(pedsDoctor))
                .andExpect(status().isCreated());

        // Specialization search finds the cardiologist without a filter.
        mvc.perform(get("/api/doctors?q=cardio").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.departmentId == " + cardiology + ")]").exists());

        // The same search scoped to pediatrics returns no cardiologist.
        mvc.perform(get("/api/doctors?q=cardio&departmentId=" + pediatrics)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/doctors?departmentId=" + pediatrics).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Vikram"));
    }

    @Test
    void duplicateNamesEmailsAndDanglingDepartmentsAreRejected() throws Exception {
        String admin = register("ADMIN");
        String name = "Oncology-" + System.nanoTime();
        long departmentId = createDepartment(admin, name);

        mvc.perform(post("/api/departments").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name.toLowerCase() + "\",\"description\":\"Duplicate\"}"))
                .andExpect(status().isConflict());

        // A doctor cannot point at a department that does not exist.
        mvc.perform(post("/api/doctors").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorJson("Ghost", 999999, "ghost-" + System.nanoTime() + "@hospital.test")))
                .andExpect(status().isBadRequest());

        String email = "dup-" + System.nanoTime() + "@hospital.test";
        mvc.perform(post("/api/doctors").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(doctorJson("First", departmentId, email)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/doctors").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(doctorJson("Second", departmentId, email)))
                .andExpect(status().isConflict());

        // The department cannot be deleted while its doctors remain in it.
        mvc.perform(delete("/api/departments/" + departmentId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());

        MvcResult list = mvc.perform(get("/api/doctors?departmentId=" + departmentId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn();
        JsonNode content = json.readTree(list.getResponse().getContentAsString()).get("content");
        for (JsonNode entry : content) {
            mvc.perform(delete("/api/doctors/" + entry.get("id").asLong())
                            .header("Authorization", "Bearer " + admin))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(delete("/api/departments/" + departmentId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/doctors/999999").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());

        // Updating a doctor keeps the same email valid (it is their own).
        long otherDept = createDepartment(admin, "Radiology-" + System.nanoTime());
        String ownEmail = "own-" + System.nanoTime() + "@hospital.test";
        MvcResult created = mvc.perform(post("/api/doctors").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(doctorJson("Own", otherDept, ownEmail)))
                .andExpect(status().isCreated()).andReturn();
        long ownId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String updated = doctorJson("Own", otherDept, ownEmail).replace("555-0201", "555-0299");
        mvc.perform(put("/api/doctors/" + ownId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("555-0299"));
    }
}
