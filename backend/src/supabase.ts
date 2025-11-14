import { createClient } from '@supabase/supabase-js';
import dotenv from 'dotenv';
dotenv.config();


const SUPABASE_URL = process.env.SUPABASE_URL!;
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY!;


if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
throw new Error('SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY must be set in env');
}


export const supabaseAdmin = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
auth: { persistSession: false },
});