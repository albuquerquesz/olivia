package com.olivia.core

/** Epoch serve para correlação externa; somente monotonicMs mede intervalos. */
public data class ClockReading(val monotonicMs: Long, val epochMs: Long) {
    init {
        require(monotonicMs >= 0) { "monotonicMs deve ser não negativo" }
    }

    public fun elapsedSince(earlier: ClockReading): Long {
        require(monotonicMs >= earlier.monotonicMs) { "Relógios monotônicos incompatíveis" }
        return monotonicMs - earlier.monotonicMs
    }
}

/** Compartilhar uma instância entre adapters e domínio durante a vida do processo. */
public fun interface OliviaClock {
    public fun now(): ClockReading
}

/** Não persista monotonicMs para reutilizá-lo em outro processo ou boot. */
public class SystemOliviaClock : OliviaClock {
    private val originNanos: Long = System.nanoTime()

    override fun now(): ClockReading = ClockReading(
        monotonicMs = (System.nanoTime() - originNanos) / 1_000_000,
        epochMs = System.currentTimeMillis(),
    )
}
