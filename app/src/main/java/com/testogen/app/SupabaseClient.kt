package com.testogen.app

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

// Шаг 27: подключение Supabase. Anon-ключ публичный — безопасно
// встраивать в APK: без входа в аккаунт он данных не даёт.
object SupabaseClient {
    const val PROJECT_URL = "https://sgkhsncgtcnqfgqfjcdr.supabase.co"
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNna2hzbmNndGNucWZncWZqY2RyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODk3NjI0MDUsImV4cCI6MjEwNTMzODQwNX0.CZL0yEyx5vFNlAWEcKSiAnzXLIM9uNuWCHhcnsOHSEs"

    val client = createSupabaseClient(
        supabaseUrl = PROJECT_URL,
        supabaseKey = ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }
}
