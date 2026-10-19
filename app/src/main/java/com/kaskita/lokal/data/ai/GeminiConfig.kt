package com.kaskita.lokal.data.ai

/** Named in Google's current Gemini API docs; the user can change it in the AI settings. */
const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"

private val modelPattern = Regex("""[A-Za-z0-9][A-Za-z0-9._-]{0,63}""")

/** Trims, drops a leading `models/`, and returns null unless the name is a plain model id. */
fun normalizeGeminiModel(raw: String): String? =
    raw.trim().removePrefix("models/").takeIf { modelPattern.matches(it) }
