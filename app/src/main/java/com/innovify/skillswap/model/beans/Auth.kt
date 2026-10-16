package com.innovify.skillswap.model.beans

import com.google.gson.annotations.SerializedName

// Body de POST api/v1/authentication/sign-in
data class SignInRequest(
    var username: String,
    var password: String
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
