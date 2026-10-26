package com.saishekar.hospital.billing;

import com.saishekar.hospital.model.InvoiceStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @GetMapping
    public InvoiceDtos.PageResponse<InvoiceDtos.InvoiceResponse> list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(patientId, status, page, size);
    }

    @GetMapping("/{id}")
    public InvoiceDtos.InvoiceResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDtos.InvoiceResponse create(@Valid @RequestBody InvoiceDtos.InvoiceRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public InvoiceDtos.InvoiceResponse update(
            @PathVariable Long id, @Valid @RequestBody InvoiceDtos.InvoiceRequest req) {
        return service.update(id, req);
    }

    @PatchMapping("/{id}/status")
    public InvoiceDtos.InvoiceResponse changeStatus(
            @PathVariable Long id, @Valid @RequestBody InvoiceDtos.StatusRequest req) {
        return service.changeStatus(id, req.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
