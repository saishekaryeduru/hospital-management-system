package com.saishekar.hospital.appointment;

import com.saishekar.hospital.model.AppointmentStatus;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @GetMapping
    public AppointmentDtos.PageResponse<AppointmentDtos.AppointmentResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(date, doctorId, patientId, status, page, size);
    }

    @GetMapping("/{id}")
    public AppointmentDtos.AppointmentResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentDtos.AppointmentResponse create(@Valid @RequestBody AppointmentDtos.AppointmentRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public AppointmentDtos.AppointmentResponse update(@PathVariable Long id,
                                                      @Valid @RequestBody AppointmentDtos.AppointmentRequest req) {
        return service.update(id, req);
    }

    @PatchMapping("/{id}/status")
    public AppointmentDtos.AppointmentResponse changeStatus(@PathVariable Long id,
                                                            @Valid @RequestBody AppointmentDtos.StatusRequest req) {
        return service.changeStatus(id, req.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
