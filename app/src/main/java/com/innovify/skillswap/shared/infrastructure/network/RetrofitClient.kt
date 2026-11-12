package com.innovify.skillswap.shared.infrastructure.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // JWT del usuario logueado. El interceptor lo agrega a cada request.
    var token: String? = null

    // Render (plan free) se duerme: la primera llamada puede tardar ~50 s, por eso 60 s de timeout.
    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("Accept-Language", "es-419") // errores del backend y textos de la IA en español
                token?.let { builder.header("Authorization", "Bearer $it") }
                chain.proceed(builder.build())
            }
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://skillswap-innovify.duckdns.org/")
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Each bounded context creates its own Retrofit interface (see <context>/infrastructure/*Api.kt).
    fun <T> create(service: Class<T>): T = retrofit.create(service)

}
