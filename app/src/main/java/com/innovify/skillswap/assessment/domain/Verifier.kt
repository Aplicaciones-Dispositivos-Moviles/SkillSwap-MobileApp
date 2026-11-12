package com.innovify.skillswap.assessment.domain

import com.innovify.skillswap.learningpath.domain.PathNode

// Body de POST api/v1/verifier-profiles: la habilidad del nodo completado (PathNode.skillTag)
data class CreateVerifierProfileRequest(
    var skillTag: String
)
