package com.innovify.skillswap.reputation.application

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.innovify.skillswap.reputation.domain.CreditTransaction
import com.innovify.skillswap.reputation.domain.Wallet
import com.innovify.skillswap.reputation.infrastructure.walletApi
import com.innovify.skillswap.shared.application.SessionAwareViewModel
import com.innovify.skillswap.shared.application.SessionStore
import retrofit2.Response

// reputation: the read-only SkillCredits wallet.
class WalletViewModel(session: SessionStore) : SessionAwareViewModel(session) {

    // ------------------------------------------------------- BILLETERA (solo lectura)
    // Saldo y movimientos de SkillCredits del usuario; siempre vienen del servidor (no se guardan en SQLite).
    var wallet: Wallet? by mutableStateOf(null)
    var transactions: List<CreditTransaction> by mutableStateOf(emptyList())

    init {
        session.onSignOut {
            wallet = null
            transactions = emptyList()
        }
    }

    // Carga el saldo y los movimientos. true si ambos llegaron; si no, errorMessage dice por qué.
    suspend fun loadWallet(): Boolean {
        val userId = session.user?.id
        if (userId == null) {
            errorMessage = "Inicia sesión para ver tu billetera"
            return false
        }
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para ver tu billetera"
            return false
        }
        return try {
            val walletResponse = walletApi.getWallet(userId)
            val walletBody = walletResponse.body()
            if (!walletResponse.isSuccessful || walletBody == null) {
                errorMessage = walletError(walletResponse)
                return false
            }
            val transactionsResponse = walletApi.getWalletTransactions(userId)
            val transactionsBody = transactionsResponse.body()
            if (!transactionsResponse.isSuccessful || transactionsBody == null) {
                errorMessage = walletError(transactionsResponse)
                return false
            }
            wallet = walletBody
            transactions = transactionsBody
            true
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    private fun walletError(response: Response<*>): String {
        return when (response.code()) {
            403 -> "Esta billetera no es tuya"
            404 -> messageFrom(response, "No encontramos tu billetera")
            else -> messageFrom(response, "No se pudo cargar tu billetera (${response.code()})")
        }
    }
}
