package com.innovify.skillswap.model.beans

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
    var redemptionItem: String?
) {
    fun isEarned(): Boolean = type == "Earned"

    // "+40" si ganó, "−40" si canjeó
    fun signedAmount(): String = (if (isEarned()) "+" else "\u2212") + amount

    fun typeLabel(): String = if (isEarned()) "Ganado" else "Canjeado"
}
