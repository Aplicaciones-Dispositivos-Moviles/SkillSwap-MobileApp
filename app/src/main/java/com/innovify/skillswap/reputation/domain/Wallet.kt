package com.innovify.skillswap.reputation.domain

// WalletResource: respuesta de GET api/v1/wallets/{userId} (saldo de SkillCredits)
data class Wallet(
    var id: Int,
    var walletOwnerId: Int,
    var balance: Int
)

// CreditTransactionResource: un movimiento de GET api/v1/wallets/{userId}/transactions.
// type: "Earned" (ganó créditos al resolver un caso) o "Redeemed" (canjeó un beneficio).
// amount siempre llega positivo; el signo lo da el tipo. createdAt viene en UTC.
data class CreditTransaction(
    var id: Int,
    var walletId: Int,
    var amount: Int,
    var type: String,
    var description: String?,
    var relatedCaseId: Int?,
    var createdAt: String?,
    var redemptionItem: String?,
    var relatedDemonstrationId: Int? = null
) {
    fun isEarned(): Boolean = type == "Earned"

    // Spanish title built from the movement data: the backend description is English and not shown.
    fun title(): String = when {
        isEarned() && relatedCaseId != null -> "Caso #$relatedCaseId resuelto"
        isEarned() && relatedDemonstrationId != null -> "Demostración final revisada"
        isEarned() -> "SkillCredits ganados"
        else -> REDEMPTION_LABELS[redemptionItem]?.let { "Canje: $it" } ?: "Canje de SkillCredits"
    }

    // "+40" si ganó, "−40" si canjeó
    fun signedAmount(): String = (if (isEarned()) "+" else "\u2212") + amount

    fun typeLabel(): String = if (isEarned()) "Ganado" else "Canjeado"
}

// Names of the redeemable benefits (values of "redemptionItem" in the backend).
private val REDEMPTION_LABELS = mapOf(
    "Badge" to "Insignia",
    "ContributionCertificate" to "Certificado de contribución",
    "AdvancedPathUnlock" to "Ruta avanzada",
    "SponsoredCertification" to "Certificación patrocinada"
)
