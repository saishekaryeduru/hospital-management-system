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

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String role) throws Exception {
        String email = role.toLowerCase() + "-dashboard-" + System.nanoTime() + "@hospital.test";
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\","
                + "\"displayName\":\"Test User\",\"role\":\"" + role + "\"}";
        MvcResult res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private long createPatient(String token, String firstName) throws Exception {
        String body = "{\"firstName\":\"" + firstName + "\",\"lastName\":\"Sharma\","
                + "\"dateOfBirth\":\"1990-04-12\",\"gender\":\"FEMALE\",\"phone\":\"555-0101\","
                + "\"email\":\"" + firstName.toLowerCase() + "-" + System.nanoTime() + "@example.com\","
                + "\"bloodGroup\":\"O_POSITIVE\"}";
        MvcResult res = mvc.perform(post("/api/patients").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createDepartment(String token) throws Exception {
        String deptBody = "{\"name\":\"Dept-" + System.nanoTime() + "\",\"description\":\"Test department\"}";
        MvcResult dept = mvc.perform(post("/api/departments").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(deptBody))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(dept.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createDoctor(String token, long departmentId, boolean active) throws Exception {
        String body = "{\"firstName\":\"Anita\",\"lastName\":\"Rao\",\"specialization\":\"Cardiology\","
                + "\"departmentId\":" + departmentId + ",\"phone\":\"555-0201\","
                + "\"email\":\"doctor-" + System.nanoTime() + "@hospital.test\",\"active\":" + active + "}";
        MvcResult res = mvc.perform(post("/api/doctors").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private long book(String token, long patientId, long doctorId, LocalDate date,
                      int hour, int durationMinutes) throws Exception {
        String startsAt = date.atTime(hour, 0).toInstant(ZoneOffset.UTC).toString();
        String body = "{\"patientId\":" + patientId + ",\"doctorId\":" + doctorId + ","
                + "\"startsAt\":\"" + startsAt + "\",\"durationMinutes\":" + durationMinutes
                + ",\"reason\":\"Follow-up\"}";
        MvcResult res = mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private void changeStatus(String token, long appointmentId, String target) throws Exception {
        mvc.perform(patch("/api/appointments/" + appointmentId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + target + "\"}"))
                .andExpect(status().isOk());
    }

    private JsonNode dashboard(String token, String query) throws Exception {
        MvcResult res = mvc.perform(get("/api/dashboard" + query)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(res.getResponse().getContentAsString());
    }

    private JsonNode departmentLoad(JsonNode dashboard, long departmentId) {
        for (JsonNode entry : dashboard.get("departmentLoad")) {
            if (entry.get("departmentId").asLong() == departmentId) {
                return entry;
            }
        }
        throw new AssertionError("department " + departmentId + " missing from dashboard load");
    }

    @Test
    void dashboardSummarizesOneDayAndItsDepartmentLoad() throws Exception {
        String admin = register("ADMIN");
        long patientId = createPatient(admin, "Dashboard");
        long departmentId = createDepartment(admin);
        long doctorId = createDoctor(admin, departmentId, true);
        // A second, inactive doctor in the same department counts toward
        // the directory but not toward who can take bookings.
        createDoctor(admin, departmentId, false);

        // A day far enough ahead that no other test books on it.
        LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(90);
        long scheduled = book(admin, patientId, doctorId, date, 9, 30);
        long completed = book(admin, patientId, doctorId, date, 10, 60);
        long cancelled = book(admin, patientId, doctorId, date, 12, 45);
        changeStatus(admin, completed, "CHECKED_IN");
        changeStatus(admin, completed, "COMPLETED");
        changeStatus(admin, cancelled, "CANCELLED");
        // A booking on the next day must not leak into this day's summary.
        book(admin, patientId, doctorId, date.plusDays(1), 9, 30);

        JsonNode dash = dashboard(admin, "?date=" + date);
        assertEquals(date.toString(), dash.get("date").asText());
        assertEquals(3, dash.get("appointmentsTotal").asLong());
        JsonNode byStatus = dash.get("appointmentsByStatus");
        assertEquals(1, byStatus.get("SCHEDULED").asLong());
        assertEquals(1, byStatus.get("COMPLETED").asLong());
        assertEquals(1, byStatus.get("CANCELLED").asLong());
        // Statuses with no appointments that day are reported as zero,
        // not omitted.
        assertEquals(0, byStatus.get("CHECKED_IN").asLong());
        assertEquals(0, byStatus.get("NO_SHOW").asLong());

        // The day's list is in start order and matches the counts.
        assertEquals(3, dash.get("appointments").size());
        assertEquals(scheduled, dash.get("appointments").get(0).get("id").asLong());
        assertEquals(completed, dash.get("appointments").get(1).get("id").asLong());
        assertEquals(cancelled, dash.get("appointments").get(2).get("id").asLong());

        JsonNode load = departmentLoad(dash, departmentId);
        assertEquals(2, load.get("doctorCount").asLong());
        assertEquals(1, load.get("activeDoctorCount").asLong());
        assertEquals(3, load.get("appointments").asLong());
        assertEquals(1, load.get("completed").asLong());
        // Booked minutes count the scheduled (30) and completed (60)
        // visits; the cancelled 45-minute slot is not load.
        assertEquals(90, load.get("bookedMinutes").asLong());
    }

    @Test
    void dashboardDefaultsToTodayAndCountsRegistrations() throws Exception {
        String admin = register("ADMIN");

        JsonNode before = dashboard(admin, "");
        assertEquals(LocalDate.now(ZoneOffset.UTC).toString(), before.get("date").asText());

        createPatient(admin, "Registered");

        // The shared test database holds other tests' patients too, so
        // the assertions are deltas around this test's own registration.
        JsonNode after = dashboard(admin, "");
        assertEquals(before.get("patientsTotal").asLong() + 1, after.get("patientsTotal").asLong());
        assertEquals(before.get("patientsRegistered").asLong() + 1,
                after.get("patientsRegistered").asLong());
    }

    @Test
    void everyRoleCanReadTheDashboardAndAnonymousCannot() throws Exception {
        for (String role : new String[]{"DOCTOR", "NURSE", "RECEPTIONIST"}) {
            String token = register(role);
            mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.date", is(LocalDate.now(ZoneOffset.UTC).toString())));
        }
        mvc.perform(get("/api/dashboard")).andExpect(status().isForbidden());
    }
}
