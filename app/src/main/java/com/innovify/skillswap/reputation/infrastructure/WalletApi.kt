package com.innovify.skillswap.reputation.infrastructure

import com.innovify.skillswap.reputation.domain.CreditTransaction
import com.innovify.skillswap.reputation.domain.Wallet
import com.innovify.skillswap.shared.infrastructure.network.RetrofitClient
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface WalletApi {

    // Saldo de SkillCredits del usuario (solo el dueño).
    // 200 Wallet | 403 no es tu billetera | 404 cuentas anteriores a las billeteras
    @GET("api/v1/wallets/{userId}")
    suspend fun getWallet(@Path("userId") userId: Int): Response<Wallet>

    // Movimientos de la billetera, del más reciente al más antiguo. 200 | 403 | 404
    @GET("api/v1/wallets/{userId}/transactions")
    suspend fun getWalletTransactions(@Path("userId") userId: Int): Response<List<CreditTransaction>>

}

val walletApi: WalletApi by lazy { RetrofitClient.create(WalletApi::class.java) }
