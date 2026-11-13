package com.innovify.skillswap.shared.domain

// Cuerpo de error del backend (ProblemDetail): title = código, detail = mensaje legible
data class ApiError(
    var title: String?,
    var detail: String?,
    var status: Int?
)
