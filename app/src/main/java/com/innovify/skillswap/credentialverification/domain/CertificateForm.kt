package com.innovify.skillswap.credentialverification.domain

// Editable fields of the certificate form (they are prefilled by the OCR and the Student can fix them).
enum class CertificateField { Institution, Course, IssueDate }

// Pure validation of the certificate form: which fields block the send and what each one says under the field.
object CertificateForm {

    private val DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")

    fun missingFields(institution: String, course: String, issueDate: String): Set<CertificateField> = buildSet {
        if (institution.isBlank()) add(CertificateField.Institution)
        if (course.isBlank()) add(CertificateField.Course)
        if (!DATE_REGEX.matches(issueDate.trim())) add(CertificateField.IssueDate)
    }

    fun messageFor(field: CertificateField): String = when (field) {
        CertificateField.Institution -> "Escribe la institución que emitió el certificado"
        CertificateField.Course -> "Escribe el nombre del curso"
        CertificateField.IssueDate -> "Elige la fecha de emisión"
    }
}
