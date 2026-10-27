-- Structured allergies replace the free-text note on the patient
-- record. A legacy note names the substance ("Penicillin", "Peanuts
-- and tree nuts"), so it is carried over as one entry with that text
-- as the substance and severity UNKNOWN -- assigning it a reaction or
-- a graded severity would be inventing clinical detail the note never
-- contained.
create table patient_allergies (
    patient_id bigint not null references patients (id),
    line_order integer not null,
    substance varchar(120) not null,
    reaction varchar(200),
    severity varchar(32) not null,
    primary key (patient_id, line_order)
);

insert into patient_allergies (patient_id, line_order, substance, reaction, severity)
select id, 0, left(btrim(allergies), 120), null, 'UNKNOWN'
from patients
where allergies is not null and btrim(allergies) <> '';

alter table patients drop column allergies;
