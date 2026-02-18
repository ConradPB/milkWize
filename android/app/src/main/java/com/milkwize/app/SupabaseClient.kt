package com.milkwize.app

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.gotrue.Auth

object SupabaseClient {
    // Replace these with your actual credentials from your MERN project
    private const val SUPABASE_URL = "https://your-project-id.supabase.co"
    private const val SUPABASE_KEY = "your-anon-key-here"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Postgrest) // This is for your Database (Milking Events)
        install(Auth)      // This is for Login/Sign up
    }
}