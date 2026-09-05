package com.olivia.companion

/**
 * Origem dos frames e do áudio da sessão. Cada flavor fornece um [ActiveBackend]
 * com o valor correspondente, para que o app suba sem o SDK do DAT no flavor mock.
 */
data class GlassesBackend(
    val id: String,
    val label: String,
    val requiresPhysicalGlasses: Boolean,
    val usesDatSdk: Boolean,
)
