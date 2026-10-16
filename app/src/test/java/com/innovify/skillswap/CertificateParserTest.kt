package com.innovify.skillswap

import com.innovify.skillswap.viewmodel.CertificateParser
import org.junit.Assert.assertEquals
import org.junit.Test

class CertificateParserTest {

    @Test
    fun parsesEnglishCertificate() {
        val text = """
            Coursera
            Completed by Victor Alberca
            January 5, 2025
            has successfully completed the online, non-credit course
            Python for Everybody
            offered through University of Michigan
        """.trimIndent()

        val parsed = CertificateParser.parse(text)

        assertEquals("University of Michigan", parsed.institution)
        assertEquals("Python for Everybody", parsed.course)
        assertEquals("2025-01-05", parsed.issueDate)
    }

    @Test
    fun parsesSpanishCertificate() {
        val text = """
            Universidad Peruana de Ciencias Aplicadas
            Certificado de aprobación
            Se otorga a Victor Alberca
            por haber aprobado el curso
            Desarrollo de Aplicaciones Móviles
            Lima, 15 de octubre de 2025
        """.trimIndent()

        val parsed = CertificateParser.parse(text)

        assertEquals("Universidad Peruana de Ciencias Aplicadas", parsed.institution)
        assertEquals("Desarrollo de Aplicaciones Móviles", parsed.course)
        assertEquals("2025-10-15", parsed.issueDate)
    }

    @Test
    fun parsesNumericDateDayFirstAndCourseLabel() {
        val text = """
            Emitido por Platzi
            Curso: Introducción a SQL
            Fecha de emisión: 03/11/2024
        """.trimIndent()

        val parsed = CertificateParser.parse(text)

        assertEquals("Platzi", parsed.institution)
        assertEquals("Introducción a SQL", parsed.course)
        assertEquals("2024-11-03", parsed.issueDate)
    }

    @Test
    fun returnsEmptyFieldsWhenNothingMatches() {
        val parsed = CertificateParser.parse("texto cualquiera sin datos")

        assertEquals("", parsed.institution)
        assertEquals("", parsed.course)
        assertEquals("", parsed.issueDate)
    }
}
