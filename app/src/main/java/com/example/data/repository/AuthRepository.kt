package com.example.data.repository

import com.example.data.api.ApiClient
import com.example.data.api.LoginRequest
import com.example.data.api.RegisterRequest
import com.example.data.config.AppConfig
import com.example.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AuthRepository(
    private val config: AppConfig,
    private val apiClient: ApiClient
) {
    private val _currentUser = MutableStateFlow<User?>(
        if (config.authToken != null) {
            User(
                id = "usr-10492",
                email = config.userEmail ?: "test@washpulse.io",
                name = config.userName ?: "Alex Jordan",
                token = config.authToken,
                createdAt = "2026-09-18"
            )
        } else null
    )
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    val isLoggedIn: Boolean
        get() = _currentUser.value != null

    suspend fun login(email: String, pass: String): Result<User> {
        return try {
            val service = apiClient.createService()
            val resp = service.login(LoginRequest(email, pass))
            if (resp.isSuccessful && resp.body() != null) {
                val tokenData = resp.body()!!
                config.authToken = tokenData.accessToken
                config.userEmail = email
                val userName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                config.userName = userName
                val user = User(
                    id = "usr-${UUID.randomUUID().toString().take(6)}",
                    email = email,
                    name = userName,
                    token = tokenData.accessToken
                )
                _currentUser.value = user
                Result.success(user)
            } else {
                Result.failure(Exception(resp.errorBody()?.string() ?: "Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(name: String, email: String, pass: String): Result<User> {
        return try {
            val service = apiClient.createService()
            val resp = service.register(RegisterRequest(name, email, pass))
            if (resp.isSuccessful && resp.body() != null) {
                val tokenData = resp.body()!!
                config.authToken = tokenData.accessToken
                config.userEmail = email
                config.userName = name
                val user = User(
                    id = "usr-${UUID.randomUUID().toString().take(6)}",
                    email = email,
                    name = name,
                    token = tokenData.accessToken
                )
                _currentUser.value = user
                Result.success(user)
            } else {
                Result.failure(Exception(resp.errorBody()?.string() ?: "Registration failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        try {
            apiClient.createService().logout()
        } catch (_: Exception) {}
        config.authToken = null
        _currentUser.value = null
    }
}
