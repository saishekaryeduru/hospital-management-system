package com.saishekar.hospital.billing;

import com.saishekar.hospital.model.ChargeItem;
import com.saishekar.hospital.model.Invoice;
import com.saishekar.hospital.model.InvoiceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class InvoiceDtos {
    private InvoiceDtos() {}

    public record ChargeItemRequest(
            @NotBlank @Size(max = 200) String description,
            @NotNull @Min(1) @Max(1000) Integer quantity,
            // Prices are integer cents; the cap is a sanity bound
            // ($100,000 per unit), not a pricing policy.
            @NotNull @Min(0) @Max(10_000_000) Long unitPriceCents) {

        ChargeItem toChargeItem() {
            return new ChargeItem(description, quantity, unitPriceCents);
        }
    }

    public record InvoiceRequest(
            @NotNull Long patientId,
            Long appointmentId,
            @NotNull Instant issuedAt,
            @Size(max = 1000) String notes,
            @Valid @NotEmpty @Size(max = 50) List<ChargeItemRequest> lines) {}

    public record StatusRequest(@NotNull InvoiceStatus status) {}

    public record ChargeItemResponse(String description, int quantity,
                                     long unitPriceCents, long lineTotalCents) {
        static ChargeItemResponse from(ChargeItem c) {
            return new ChargeItemResponse(c.getDescription(), c.getQuantity(),
                    c.getUnitPriceCents(), c.getLineTotalCents());
        }
    }

    public record InvoiceResponse(Long id, Long patientId, String patientName,
                                  Long appointmentId, Instant issuedAt, String notes,
                                  List<ChargeItemResponse> lines, long totalCents,
                                  InvoiceStatus status, Instant paidAt) {
        static InvoiceResponse from(Invoice i) {
            var patient = i.getPatient();
            return new InvoiceResponse(i.getId(),
                    patient.getId(), patient.getFirstName() + " " + patient.getLastName(),
                    i.getAppointment() == null ? null : i.getAppointment().getId(),
                    i.getIssuedAt(), i.getNotes(),
                    i.getLines().stream().map(ChargeItemResponse::from).toList(),
                    i.getTotalCents(), i.getStatus(), i.getPaidAt());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size,
                                  long totalElements, int totalPages) {}
}
