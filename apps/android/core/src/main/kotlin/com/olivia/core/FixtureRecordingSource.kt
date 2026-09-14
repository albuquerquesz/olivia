package com.olivia.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Offline-only source for a metadata fixture. It contains no media bytes and never pretends to
 * be a DAT transfer. A local path becomes visible only after its progress flow completes.
 */
public class FixtureRecordingSource(fixtures: List<FixtureRecording>) : RecordingSource {
    private val fixturesById: Map<String, FixtureRecording> = fixtures.associateBy(FixtureRecording::assetId)
    private val completedAssets: MutableSet<String> = mutableSetOf()

    init {
        require(fixturesById.size == fixtures.size) { "Duplicate fixture asset" }
    }

    override suspend fun listTakes(sessionId: SessionId): List<RecordingAsset> =
        fixturesById.values.map(FixtureRecording::asset).filter { it.sessionId == sessionId }

    override fun fetch(asset: RecordingAsset): Flow<TransferProgress> = flow {
        val fixture = requireNotNull(fixturesById[asset.id]) { "Unknown fixture asset" }
        require(fixture.asset == asset) { "Fixture asset mismatch" }
        emit(TransferProgress(asset.id, 0, asset.sizeBytes))
        emit(TransferProgress(asset.id, asset.sizeBytes, asset.sizeBytes))
        completedAssets += asset.id
    }

    override suspend fun localPathOf(assetId: String): String? =
        fixturesById[assetId]?.takeIf { assetId in completedAssets }?.localPath
}

public data class FixtureRecording(val asset: RecordingAsset, val localPath: String) {
    public val assetId: String get() = asset.id

    init {
        require(asset.id.isNotBlank() && asset.durationMs > 0 && asset.sizeBytes >= 0)
        require(localPath.isNotBlank() && !localPath.contains("://")) { "Fixture path must be local" }
    }
}

/**
 * Verified linear relation between the session monotonic offset and a native-media timestamp.
 * The caller must withhold automatic cuts when uncertainty exceeds the 250 ms prototype limit.
 */
public data class LinearMediaAlignment(
    val sessionId: SessionId,
    /** Native media time at session offset zero; it may be negative when media began earlier. */
    val mediaOffsetAtSessionStartMs: Long,
    val uncertaintyMs: Long,
) {
    init { require(uncertaintyMs >= 0) }

    public fun toMediaOffset(sessionOffsetMs: Long, maximumUncertaintyMs: Long = 250): Long? {
        require(sessionOffsetMs >= 0 && maximumUncertaintyMs >= 0)
        if (uncertaintyMs > maximumUncertaintyMs) return null
        return (mediaOffsetAtSessionStartMs + sessionOffsetMs).takeIf { it >= 0 }
    }

    public fun align(event: TimelineEvent, maximumUncertaintyMs: Long = 250): MediaTimelineEvent? {
        require(event.sessionId == sessionId) { "Timeline from another session" }
        return toMediaOffset(event.offsetMs, maximumUncertaintyMs)?.let { MediaTimelineEvent(event, it) }
    }
}
