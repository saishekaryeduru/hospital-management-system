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

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppointmentApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String role) throws Exception {
        String email = role.toLowerCase() + "-appt-" + System.nanoTime() + "@hospital.test";
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

    private long createDoctor(String token, boolean active) throws Exception {
        String deptBody = "{\"name\":\"Dept-" + System.nanoTime() + "\",\"description\":\"Test department\"}";
        MvcResult dept = mvc.perform(post("/api/departments").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(deptBody))
                .andExpect(status().isCreated()).andReturn();
        long departmentId = json.readTree(dept.getResponse().getContentAsString()).get("id").asLong();

        String body = "{\"firstName\":\"Anita\",\"lastName\":\"Rao\",\"specialization\":\"Cardiology\","
                + "\"departmentId\":" + departmentId + ",\"phone\":\"555-0201\","
                + "\"email\":\"doctor-" + System.nanoTime() + "@hospital.test\",\"active\":" + active + "}";
        MvcResult res = mvc.perform(post("/api/doctors").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    // Each test books on its own future date so the shared test database
    // never makes one test's day view see another test's appointments.
    private LocalDate day(int daysAhead) {
        return LocalDate.now(ZoneOffset.UTC).plusDays(daysAhead);
    }

    private String at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).toInstant(ZoneOffset.UTC).toString();
    }

    private String appointmentJson(long patientId, long doctorId, String startsAt, Integer durationMinutes) {
        String duration = durationMinutes == null ? "" : ",\"durationMinutes\":" + durationMinutes;
        return "{\"patientId\":" + patientId + ",\"doctorId\":" + doctorId + ","
                + "\"startsAt\":\"" + startsAt + "\"" + duration + ",\"reason\":\"Follow-up\"}";
    }

    private long book(String token, long patientId, long doctorId, String startsAt, Integer duration)
            throws Exception {
        MvcResult res = mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(patientId, doctorId, startsAt, duration)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private void changeStatus(String token, long appointmentId, String target, int expected) throws Exception {
        mvc.perform(patch("/api/appointments/" + appointmentId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + target + "\"}"))
                .andExpect(status().is(expected));
    }

    @Test
    void frontDeskBooksAndTheDayViewListsAppointmentsInTimeOrder() throws Exception {
        String admin = register("ADMIN");
        String receptionist = register("RECEPTIONIST");
        long patientId = createPatient(receptionist, "Asha");
        long doctorId = createDoctor(admin, true);
        LocalDate date = day(7);

        // Booked out of order on purpose: the day view sorts by start time.
        // The first booking omits the duration and must default to 30 minutes.
        MvcResult later = mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(patientId, doctorId, at(date, 15, 0), null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.endsAt").value(at(date, 15, 30)))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.patientName").value("Asha Sharma"))
                .andReturn();
        long laterId = json.readTree(later.getResponse().getContentAsString()).get("id").asLong();
        long earlierId = book(receptionist, patientId, doctorId, at(date, 14, 0), 30);

        // Clinical staff can read the schedule but cannot book at the desk.
        String nurse = register("NURSE");
        mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + nurse)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(patientId, doctorId, at(date, 16, 0), 30)))
                .andExpect(status().isForbidden());

        MvcResult dayView = mvc.perform(get("/api/appointments?date=" + date + "&doctorId=" + doctorId)
                        .header("Authorization", "Bearer " + nurse))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        var content = json.readTree(dayView.getResponse().getContentAsString()).get("content");
        org.junit.jupiter.api.Assertions.assertEquals(earlierId, content.get(0).get("id").asLong());
        org.junit.jupiter.api.Assertions.assertEquals(laterId, content.get(1).get("id").asLong());

        // The same doctor on the next day has nothing booked.
        mvc.perform(get("/api/appointments?date=" + date.plusDays(1) + "&doctorId=" + doctorId)
                        .header("Authorization", "Bearer " + nurse))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // A patient filter finds that patient's appointments across doctors.
        mvc.perform(get("/api/appointments?patientId=" + patientId).header("Authorization", "Bearer " + nurse))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void overlappingBookingsConflictButBackToBackAndOtherDoctorsAreFine() throws Exception {
        String admin = register("ADMIN");
        long patientId = createPatient(admin, "Meera");
        long doctorId = createDoctor(admin, true);
        long otherDoctorId = createDoctor(admin, true);
        LocalDate date = day(14);

        book(admin, patientId, doctorId, at(date, 14, 0), 30);

        // Starts inside the existing appointment.
        mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(patientId, doctorId, at(date, 14, 15), 30)))
                .andExpect(status().isConflict());

        // Starts before it and runs into it.
        mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(patientId, doctorId, at(date, 13, 30), 60)))
                .andExpect(status().isConflict());

        // Back-to-back is not an overlap, and a different doctor is free
        // at the very same time.
        book(admin, patientId, doctorId, at(date, 14, 30), 30);
        book(admin, patientId, otherDoctorId, at(date, 14, 0), 30);
    }

    @Test
    void lifecycleIsEnforcedAndCancellingFreesTheSlot() throws Exception {
        String admin = register("ADMIN");
        String nurse = register("NURSE");
        long patientId = createPatient(admin, "Kiran");
        long doctorId = createDoctor(admin, true);
        LocalDate date = day(21);

        long visit = book(admin, patientId, doctorId, at(date, 10, 0), 30);

        // A booking cannot be completed before the patient checks in, and
        // a nurse at the point of care can run the lifecycle.
        changeStatus(nurse, visit, "COMPLETED", 409);
        changeStatus(nurse, visit, "CHECKED_IN", 200);
        changeStatus(nurse, visit, "COMPLETED", 200);
        changeStatus(nurse, visit, "CANCELLED", 409);

        // A completed appointment can no longer be rescheduled.
        mvc.perform(put("/api/appointments/" + visit).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(patientId, doctorId, at(date, 11, 0), 30)))
                .andExpect(status().isConflict());

        long booked = book(admin, patientId, doctorId, at(date, 12, 0), 30);
        changeStatus(admin, booked, "CANCELLED", 200);
        // The cancelled slot is free again for the same doctor and time.
        book(admin, patientId, doctorId, at(date, 12, 0), 30);
    }

    @Test
    void inactiveDoctorsDanglingReferencesAndPastTimesAreRejected() throws Exception {
        String admin = register("ADMIN");
        long patientId = createPatient(admin, "Divya");
        long activeDoctorId = createDoctor(admin, true);
        long inactiveDoctorId = createDoctor(admin, false);
        LocalDate date = day(28);

        mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(patientId, inactiveDoctorId, at(date, 9, 0), 30)))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(999999, activeDoctorId, at(date, 9, 0), 30)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(patientId, 999999, at(date, 9, 0), 30)))
                .andExpect(status().isBadRequest());

        // Yesterday cannot be booked, however free the calendar is.
        mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(patientId, activeDoctorId, at(day(-1), 9, 0), 30)))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/appointments/999999").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());

        // Rescheduling onto a neighbour's slot conflicts; keeping the
        // appointment's own slot while editing the reason does not.
        long first = book(admin, patientId, activeDoctorId, at(date, 9, 0), 30);
        long second = book(admin, patientId, activeDoctorId, at(date, 10, 0), 30);
        mvc.perform(put("/api/appointments/" + second).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentJson(patientId, activeDoctorId, at(date, 9, 15), 30)))
                .andExpect(status().isConflict());
        String edited = appointmentJson(patientId, activeDoctorId, at(date, 10, 0), 30)
                .replace("Follow-up", "Rescheduled review");
        MvcResult updated = mvc.perform(put("/api/appointments/" + second)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(edited))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Rescheduled review"))
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(second,
                json.readTree(updated.getResponse().getContentAsString()).get("id").asLong());
        org.junit.jupiter.api.Assertions.assertTrue(first != second);
    }
}
