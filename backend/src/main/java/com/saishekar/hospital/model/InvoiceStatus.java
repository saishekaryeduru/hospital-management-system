package com.saishekar.hospital.model;

// An invoice is issued unpaid, then it is either paid or voided. Both
// outcomes are terminal: a paid invoice is a settled financial record,
// and a voided one stays on file (rather than being deleted) so the
// billing history keeps an audit trail of what was issued.
public enum InvoiceStatus {
    UNPAID,
    PAID,
    VOID
}
