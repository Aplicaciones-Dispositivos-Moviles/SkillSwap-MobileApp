package com.innovify.skillswap.model.beans

// US04 · Body de PUT api/v1/users/{id}/interests: de 1 a 10 temas (hasta 60 caracteres) y una descripción opcional
data class UpdateInterestsRequest(
    var topics: List<String>,
    var description: String?
)

const val MAX_INTERESTS = 10
const val MAX_INTEREST_LENGTH = 60

// Quita espacios de más: "  machine   learning " -> "machine learning"
fun cleanInterest(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

// Motivo por el que no se puede agregar el tema (null = se puede)
fun interestError(current: List<String>, raw: String): String? {
    val topic = cleanInterest(raw)
    return when {
        topic.isEmpty() -> "Escribe un tema"
        topic.length > MAX_INTEREST_LENGTH -> "Máximo $MAX_INTEREST_LENGTH caracteres"
        current.size >= MAX_INTERESTS -> "Máximo $MAX_INTERESTS temas"
        current.any { it.equals(topic, ignoreCase = true) } -> "Ya agregaste ese tema"
        else -> null
    }
}
