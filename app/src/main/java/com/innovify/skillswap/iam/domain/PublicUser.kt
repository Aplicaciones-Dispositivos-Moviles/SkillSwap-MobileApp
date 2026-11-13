package com.innovify.skillswap.iam.domain

// PublicUserResource: GET api/v1/users/{id} for another user. It has no email and no full name.
data class PublicUser(
    val id: Int,
    val username: String?
)
