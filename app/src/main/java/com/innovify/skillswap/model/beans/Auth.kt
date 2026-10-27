package com.innovify.skillswap.model.beans

import com.google.gson.annotations.SerializedName

// Body de POST api/v1/authentication/sign-in
data class SignInRequest(
    var username: String,
    var password: String
)

// Body de POST api/v1/authentication/sign-up (el correo debe ser institucional .edu.pe)
data class SignUpRequest(
    var username: String,
    var email: String,
    var password: String
)

// UserResource: respuesta de sign-up (la cuenta queda sin verificar hasta confirmar el correo)
data class UserResource(
    var id: Int,
    var username: String,
    var email: String,
    var role: String,
    @SerializedName("isVerified") var isVerified: Boolean,
    var interests: List<String>? = null
)

// Respuesta de sign-in (AuthenticatedUserResource del backend)
data class AuthUser(
    var id: Int,
    var username: String,
    var email: String,
    var role: String,
    @SerializedName("isVerified") var isVerified: Boolean,
    var token: String
)
