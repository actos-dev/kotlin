package dev.actos

import kotlinx.serialization.json.Json

public val ActosJson: Json =
    Json {
        ignoreUnknownKeys = true // §16 Forward compatibility
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }
