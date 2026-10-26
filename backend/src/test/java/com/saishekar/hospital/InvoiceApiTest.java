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
class InvoiceApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String role) throws Exception {
        String email = role.toLowerCase() + "-invoice-" + System.nanoTime() + "@hospital.test";
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

    private String issuedNow() {
        return Instant.now().minus(5, ChronoUnit.MINUTES).toString();
    }

    private String invoiceJson(long patientId, Long appointmentId, String issuedAt, String linesJson) {
        String appointment = appointmentId == null ? "" : "\"appointmentId\":" + appointmentId + ",";
        return "{\"patientId\":" + patientId + "," + appointment
                + "\"issuedAt\":\"" + issuedAt + "\",\"notes\":\"Payable at the front desk\","
                + "\"lines\":" + linesJson + "}";
    }

    private static final String TWO_LINES = "["
            + "{\"description\":\"Consultation\",\"quantity\":1,\"unitPriceCents\":15000},"
            + "{\"description\":\"ECG\",\"quantity\":2,\"unitPriceCents\":4000}"
            + "]";

    @Test
    void frontDeskIssuesInvoiceWithDerivedTotalAndClinicalRolesCannotReadIt() throws Exception {
        String admin = register("ADMIN");
        String receptionist = register("RECEPTIONIST");
        String doctor = register("DOCTOR");
        String nurse = register("NURSE");
        long patientId = createPatient(admin, "BillingAsha");

        // The total is derived server-side: 1 x 15000 + 2 x 4000.
        MvcResult created = mvc.perform(post("/api/invoices")
                        .header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(patientId, null, issuedNow(), TWO_LINES)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientName").value("BillingAsha Sharma"))
                .andExpect(jsonPath("$.status").value("UNPAID"))
                .andExpect(jsonPath("$.paidAt", org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[1].lineTotalCents").value(8000))
                .andExpect(jsonPath("$.totalCents").value(23000))
                .andReturn();
        long invoiceId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // Billing is financial data: the desk and administrators read
        // it, clinical roles -- who can read the chart -- cannot.
        mvc.perform(get("/api/invoices/" + invoiceId).header("Authorization", "Bearer " + receptionist))
                .andExpect(status().isOk());
        mvc.perform(get("/api/invoices/" + invoiceId).header("Authorization", "Bearer " + doctor))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/invoices?patientId=" + patientId)
                        .header("Authorization", "Bearer " + nurse))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/invoices").header("Authorization", "Bearer " + doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(patientId, null, issuedNow(), TWO_LINES)))
                .andExpect(status().isForbidden());

        // The patient's list and the unpaid queue both find the invoice.
        mvc.perform(get("/api/invoices?patientId=" + patientId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/invoices?status=UNPAID&patientId=" + patientId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(invoiceId));
        mvc.perform(get("/api/invoices?status=PAID&patientId=" + patientId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void appointmentLinkMustBeACompletedVisitForTheSamePatient() throws Exception {
        String admin = register("ADMIN");
        long patientId = createPatient(admin, "BillingMeera");
        long otherPatientId = createPatient(admin, "BillingKiran");
        long doctorId = createDoctor(admin);

        LocalDate date = LocalDate.now(ZoneOffset.UTC).plusDays(42);
        String startsAt = date.atTime(11, 0).toInstant(ZoneOffset.UTC).toString();
        String booking = "{\"patientId\":" + patientId + ",\"doctorId\":" + doctorId + ","
                + "\"startsAt\":\"" + startsAt + "\",\"durationMinutes\":30,\"reason\":\"Review\"}";
        MvcResult booked = mvc.perform(post("/api/appointments").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(status().isCreated()).andReturn();
        long appointmentId = json.readTree(booked.getResponse().getContentAsString()).get("id").asLong();

        // A scheduled visit has not happened, so there is nothing to
        // bill yet; a dangling id is a bad request.
        mvc.perform(post("/api/invoices").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(patientId, appointmentId, issuedNow(), TWO_LINES)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/invoices").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(patientId, 999999L, issuedNow(), TWO_LINES)))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/appointments/" + appointmentId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CHECKED_IN\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/appointments/" + appointmentId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/invoices").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(otherPatientId, appointmentId, issuedNow(), TWO_LINES)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/invoices").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(patientId, appointmentId, issuedNow(), TWO_LINES)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(appointmentId));

        // One visit, one invoice.
        mvc.perform(post("/api/invoices").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(patientId, appointmentId, issuedNow(), TWO_LINES)))
                .andExpect(status().isConflict());
    }

    @Test
    void paidAndVoidInvoicesAreTerminalAndCannotBeEditedOrDeleted() throws Exception {
        String admin = register("ADMIN");
        String receptionist = register("RECEPTIONIST");
        long patientId = createPatient(admin, "BillingDivya");

        MvcResult created = mvc.perform(post("/api/invoices")
                        .header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(patientId, null, issuedNow(), TWO_LINES)))
                .andExpect(status().isCreated()).andReturn();
        long invoiceId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // An unpaid invoice cannot jump back to UNPAID, and an empty
        // charge list or a future issue date is rejected up front.
        mvc.perform(patch("/api/invoices/" + invoiceId + "/status")
                        .header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"UNPAID\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/invoices").header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(patientId, null, issuedNow(), "[]")))
                .andExpect(status().isBadRequest());
        String future = Instant.now().plus(1, ChronoUnit.DAYS).toString();
        mvc.perform(post("/api/invoices").header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(patientId, null, future, TWO_LINES)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/invoices").header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(999999, null, issuedNow(), TWO_LINES)))
                .andExpect(status().isBadRequest());

        // Paying settles the invoice and stamps when it was paid.
        mvc.perform(patch("/api/invoices/" + invoiceId + "/status")
                        .header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());

        // A settled invoice is a financial record: no edits, no second
        // transition, no deletion -- by anyone, administrators included.
        mvc.perform(put("/api/invoices/" + invoiceId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(patientId, null, issuedNow(), TWO_LINES)))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/invoices/" + invoiceId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VOID\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/invoices/" + invoiceId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());

        // Voiding is the other terminal path, reached from UNPAID.
        MvcResult second = mvc.perform(post("/api/invoices")
                        .header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(patientId, null, issuedNow(), TWO_LINES)))
                .andExpect(status().isCreated()).andReturn();
        long secondId = json.readTree(second.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(patch("/api/invoices/" + secondId + "/status")
                        .header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VOID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOID"))
                .andExpect(jsonPath("$.paidAt", org.hamcrest.Matchers.nullValue()));

        // An unpaid invoice can still be corrected and removed: the
        // correction replaces the charge lines as a whole.
        MvcResult third = mvc.perform(post("/api/invoices")
                        .header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(patientId, null, issuedNow(), TWO_LINES)))
                .andExpect(status().isCreated()).andReturn();
        long thirdId = json.readTree(third.getResponse().getContentAsString()).get("id").asLong();
        String oneLine = "[{\"description\":\"Consultation\",\"quantity\":1,\"unitPriceCents\":12000}]";
        mvc.perform(put("/api/invoices/" + thirdId).header("Authorization", "Bearer " + receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(patientId, null, issuedNow(), oneLine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.totalCents").value(12000));
        mvc.perform(delete("/api/invoices/" + thirdId).header("Authorization", "Bearer " + receptionist))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/invoices/" + thirdId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/invoices/" + thirdId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }
}
