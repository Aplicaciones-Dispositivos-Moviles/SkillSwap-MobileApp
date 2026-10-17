package com.innovify.skillswap.model.beans

// Body de POST api/v1/verifier-profiles: la habilidad del nodo completado (PathNode.skillTag)
data class CreateVerifierProfileRequest(
    var skillTag: String
)
