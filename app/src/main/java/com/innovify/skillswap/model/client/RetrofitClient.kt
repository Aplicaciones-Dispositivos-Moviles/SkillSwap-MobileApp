package com.innovify.skillswap.model.client

import com.innovify.skillswap.model.response.WebService
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
                token?.let { builder.header("Authorization", "Bearer $it") }
                chain.proceed(builder.build())
            }
            .build()
    }

    val webService: WebService by lazy {
        Retrofit.Builder()
            .baseUrl("https://skillswap-webservices-java.onrender.com/")
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WebService::class.java)
    }

}
