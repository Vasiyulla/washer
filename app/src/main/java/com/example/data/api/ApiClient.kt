package com.example.data.api

import com.example.data.config.AppConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class AuthInterceptor(private val config: AppConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = config.authToken
        val requestBuilder = originalRequest.newBuilder()
            .header("Accept", "application/json")
            .header("User-Agent", "WashPulse-Android-Client/1.0")

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}

class ApiClient(private val config: AppConfig) {

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(config))
            .addInterceptor(logging)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun createService(): WashApiService {
        val baseUrl = if (config.apiBaseUrl.endsWith("/")) config.apiBaseUrl else "${config.apiBaseUrl}/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WashApiService::class.java)
    }
}
