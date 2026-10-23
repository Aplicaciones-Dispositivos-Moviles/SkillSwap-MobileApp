package com.innovify.skillswap.model.beans

// Body de PUT api/v1/users/me/device-token: el token de Firebase Cloud Messaging de este teléfono
data class DeviceTokenRequest(
    var token: String
)
