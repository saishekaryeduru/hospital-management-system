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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MedicalRecordApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String role) throws Exception {
        String email = role.toLowerCase() + "-record-" + System.nanoTime() + "@hospital.test";
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

    private long createDoctor(String token) throws Exception {
        String deptBody = "{\"name\":\"Dept-" + System.nanoTime() + "\",\"description\":\"Test department\"}";
        MvcResult dept = mvc.perform(post("/api/departments").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(deptBody))
                .andExpect(status().isCreated()).andReturn();
        long departmentId = json.readTree(dept.getResponse().getContentAsString()).get("id").asLong();

        String body = "{\"firstName\":\"Anita\",\"lastName\":\"Rao\",\"specialization\":\"Cardiology\","
                + "\"departmentId\":" + departmentId + ",\"phone\":\"555-0201\","
                + "\"email\":\"doctor-" + System.nanoTime() + "@hospital.test\",\"active\":true}";
        MvcResult res = mvc.perform(post("/api/doctors").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private String pastVisit() {
        return Instant.now().minus(2, ChronoUnit.HOURS).toString();
    }

    private String recordJson(long patientId, long doctorId, Long appointmentId, String visitAt,
                              String diagnosis, String prescriptionsJson) {
        String appointment = appointmentId == null ? "" : "\"appointmentId\":" + appointmentId + ",";
        String prescriptions = prescriptionsJson == null ? ""
                : ",\"prescriptions\":" + prescriptionsJson;
        return "{\"patientId\":" + patientId + ",\"doctorId\":" + doctorId + "," + appointment
                + "\"visitAt\":\"" + visitAt + "\",\"chiefComplaint\":\"Chest discomfort on exertion\","
                + "\"diagnosis\":\"" + diagnosis + "\",\"notes\":\"Resting ECG normal.\""
                + prescriptions + "}";
    }

    @Test
    void doctorAuthorsRecordWithPrescriptionsAndTheFrontDeskCannotReadIt() throws Exception {
        String admin = register("ADMIN");
        String doctor = register("DOCTOR");
        String nurse = register("NURSE");
        String receptionist = register("RECEPTIONIST");
        long patientId = createPatient(admin, "Asha");
        long doctorId = createDoctor(admin);

        String prescriptions = "["
                + "{\"medication\":\"Aspirin\",\"dosage\":\"75 mg\",\"frequency\":\"once daily\","
                + "\"durationDays\":30,\"instructions\":\"Take after food\"},"
                + "{\"medication\":\"Atorvastatin\",\"dosage\":\"10 mg\",\"frequency\":\"at night\"}"
                + "]";
        MvcResult created = mvc.perform(post("/api/medical-records")
                        .header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(patientId, doctorId, null, pastVisit(),
                                "Stable angina, under observation", prescriptions)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientName").value("Asha Sharma"))
                .andExpect(jsonPath("$.doctorName").value("Anita Rao"))
                .andExpect(jsonPath("$.appointmentId", org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.prescriptions.length()").value(2))
                .andExpect(jsonPath("$.prescriptions[0].medication").value("Aspirin"))
                .andExpect(jsonPath("$.prescriptions[1].medication").value("Atorvastatin"))
                .andReturn();
        long recordId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Clinical staff read the chart; the front desk, which can read
        // demographics and the schedule, cannot read a diagnosis.
        mvc.perform(get("/api/medical-records/" + recordId).header("Authorization", "Bearer " + nurse))
                .andExpect(status().isOk());
        mvc.perform(get("/api/medical-records/" + recordId)
                        .header("Authorization", "Bearer " + receptionist))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/medical-records?patientId=" + patientId)
                        .header("Authorization", "Bearer " + receptionist))
                .andExpect(status().isForbidden());

        // Nurses read but do not author; receptionists do neither.
        mvc.perform(post("/api/medical-records").header("Authorization", "Bearer " + nurse)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(patientId, doctorId, null, pastVisit(), "Nurse note", null)))
                .andExpect(status().isForbidden());

        // The patient's history and a diagnosis search find the record.
        mvc.perform(get("/api/medical-records?patientId=" + patientId)
                        .header("Authorization", "Bearer " + doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/medical-records?q=angina").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(recordId));
        mvc.perform(get("/api/medical-records?q=measles").header("Authorization", "Bearer " + doctor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void appointmentLinkMustBeACompletedVisitForTheSamePatientAndDoctor() throws Exception {
        String admin = register("ADMIN");
        String doctor = register("DOCTOR");
        long patientId = createPatient(admin, "Meera");
        long otherPatientId = createPatient(admin, "Kiran");
        long doctorId = createDoctor(admin);

        LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(35);
        String startsAt = date.atTime(10, 0).toInstant(ZoneOffset.UTC).toString();
        String booking = "{\"patientId\":" + patientId + ",\"doctorId\":" + doctorId + ","
                + "\"startsAt\":\"" + startsAt + "\",\"durationMinutes\":30,\"reason\":\"Review\"}";
        MvcResult booked = mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isCreated()).andReturn();
        long appointmentId = json.readTree(booked.getResponse().getContentAsString()).get("id").asLong();

        // A scheduled appointment has not happened yet, so it cannot be
        // documented; neither can a dangling id.
        mvc.perform(post("/api/medical-records").header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(patientId, doctorId, appointmentId, pastVisit(), "Review", null)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/medical-records").header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(patientId, doctorId, 999999L, pastVisit(), "Review", null)))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/appointments/" + appointmentId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CHECKED_IN\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/appointments/" + appointmentId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        // The appointment belongs to Meera: documenting it against a
        // different patient is a bad request, not a quiet relink.
        mvc.perform(post("/api/medical-records").header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(otherPatientId, doctorId, appointmentId, pastVisit(), "Review", null)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/medical-records").header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(patientId, doctorId, appointmentId, pastVisit(),
                                "Viral upper respiratory infection", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(appointmentId));

        // One appointment, one record.
        mvc.perform(post("/api/medical-records").header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(patientId, doctorId, appointmentId, pastVisit(), "Duplicate", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateReplacesPrescriptionLinesAndOnlyAdminDeletes() throws Exception {
        String admin = register("ADMIN");
        String doctor = register("DOCTOR");
        long patientId = createPatient(admin, "Divya");
        long doctorId = createDoctor(admin);

        String oneLine = "[{\"medication\":\"Paracetamol\",\"dosage\":\"500 mg\","
                + "\"frequency\":\"three times daily\",\"durationDays\":5}]";
        MvcResult created = mvc.perform(post("/api/medical-records")
                        .header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(patientId, doctorId, null, pastVisit(), "Acute bronchitis", oneLine)))
                .andExpect(status().isCreated()).andReturn();
        long recordId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // A correction replaces the prescription list as a whole: the
        // withdrawn line is gone, not left behind on the chart.
        mvc.perform(put("/api/medical-records/" + recordId).header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(patientId, doctorId, null, pastVisit(),
                                "Acute bronchitis, resolving", "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosis").value("Acute bronchitis, resolving"))
                .andExpect(jsonPath("$.prescriptions.length()").value(0));

        // A visit cannot be recorded before it happens, and dangling
        // patient or doctor references are bad requests.
        String future = Instant.now().plus(1, ChronoUnit.DAYS).toString();
        mvc.perform(post("/api/medical-records").header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(patientId, doctorId, null, future, "Premonition", null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/medical-records").header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(999999, doctorId, null, pastVisit(), "Ghost", null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/medical-records").header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recordJson(patientId, 999999, null, pastVisit(), "Ghost", null)))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/medical-records/" + recordId).header("Authorization", "Bearer " + doctor))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/medical-records/" + recordId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/medical-records/" + recordId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }
}
