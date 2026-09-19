package com.testogen.app

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo

// Шаг 27: вход/выход через Supabase Auth.
// Шаг 28.2: проверки авторизации — только после
// awaitInitialization(): сессия загружается из хранилища
// асинхронно, и синхронные проверки до этого дают ложное
// «не авторизован».
object AuthManager {

    suspend fun signIn(email: String, password: String): Result<Unit> = try {
        SupabaseClient.client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun signOut() {
        try {
            SupabaseClient.client.auth.signOut()
        } catch (e: Exception) {
        }
    }

    // Для действий с Supabase: дожидается загрузки сессии.
    suspend fun requireSignedIn(): Boolean = try {
        SupabaseClient.client.auth.awaitInitialization()
        SupabaseClient.client.auth.currentSessionOrNull() != null
    } catch (e: Exception) {
        false
    }

    // Текущий пользователь после гарантированной инициализации.
    suspend fun currentUserAsync(): UserInfo? = try {
        SupabaseClient.client.auth.awaitInitialization()
        SupabaseClient.client.auth.currentSessionOrNull()?.user
    } catch (e: Exception) {
        null
    }
}
