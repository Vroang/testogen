package com.testogen.app

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo

// Шаг 27: вход/выход через Supabase Auth.
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

    fun isSignedIn(): Boolean = try {
        SupabaseClient.client.auth.currentUserOrNull() != null
    } catch (e: Exception) {
        false
    }

    // Шаг 28.1: проверка при старте. Сначала дожидаемся загрузки
    // сохранённой сессии из хранилища — иначе currentUserOrNull()
    // возвращает null и пользователь видит экран «Вход».
    suspend fun isSignedInAsync(): Boolean = try {
        SupabaseClient.client.auth.awaitInitialization()
        SupabaseClient.client.auth.currentSessionOrNull() != null
    } catch (e: Exception) {
        false
    }

    fun currentUser(): UserInfo? = try {
        SupabaseClient.client.auth.currentUserOrNull()
    } catch (e: Exception) {
        null
    }
}
