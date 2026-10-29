/**
 * 튠즈 흐름 조종 (flow steering).
 *
 * Collects candidate songs (YouTube radio of each song that plays, plus the songs you
 * play most), places each one on two axes, and orders the next songs along a path from
 * the current song toward the point you steer to:
 *  - energy: calm (0) .. intense (1), from [FlowEnergy]
 *  - novelty: familiar (0) .. never heard (1), from your own play time
 * Once you steer (or start a flow), the songs after the current one in the player queue
 * are replaced by that plan, and the plan is redone at every song change.
 */

package com.metrolist.music.tunes.flow

import android.content.Context
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.YouTubeQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.min

data class FlowSong(
    val meta: MediaMetadata,
    val energy: Float,
    val novelty: Float,
    val measured: Boolean,
)

data class FlowTarget(val energy: Float, val novelty: Float)

enum class SkipReason { LATER, NEVER }

object FlowEngine {
    private class Entry(val meta: MediaMetadata, val raw: Float, val measured: Boolean, val novelty: Float)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _pool = MutableStateFlow<Map<String, FlowSong>>(emptyMap())
    val pool: StateFlow<Map<String, FlowSong>> = _pool
    private val _target = MutableStateFlow<FlowTarget?>(null)
    val target: StateFlow<FlowTarget?> = _target
    private val _plan = MutableStateFlow<List<String>>(emptyList())
    val plan: StateFlow<List<String>> = _plan
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    private val entries = LinkedHashMap<String, Entry>()
    private val banned = mutableSetOf<String>()
    private val later = mutableMapOf<String, Int>()
    private val played = ArrayDeque<String>()
    private val seeded = mutableSetOf<String>()
    private val pending = mutableSetOf<String>()
    private val limit = Semaphore(4)

    private var connection: PlayerConnection? = null
    private var database: MusicDatabase? = null
    private var energy: FlowEnergy? = null
    private var currentId: String? = null
    private var applyJob: Job? = null

    fun attach(context: Context, playerConnection: PlayerConnection?, db: MusicDatabase) {
        if (energy == null) energy = FlowEnergy(context)
        connection = playerConnection
        database = db
    }

    /** Songs to show before anything plays (the ones you play most). */
    fun prime(songs: List<MediaMetadata>) {
        scope.launch { add(songs.take(40)) }
    }

    /** Called whenever the player's current song changes. */
    fun onCurrent(meta: MediaMetadata?) {
        if (meta == null || meta.id == currentId) return
        currentId?.let {
            played.addLast(it)
            while (played.size > 15) played.removeFirst()
        }
        currentId = meta.id
        for (k in later.keys.toList()) {
            val left = (later[k] ?: 0) - 1
            if (left <= 0) later.remove(k) else later[k] = left
        }
        scope.launch {
            add(listOf(meta))
            replan(apply = false)
            seed(meta)
            replan(apply = _active.value)
        }
    }

    fun currentSong(): FlowSong? = currentId?.let { _pool.value[it] }

    fun steer(target: FlowTarget) {
        _target.value = FlowTarget(target.energy.coerceIn(0f, 1f), target.novelty.coerceIn(0f, 1f))
        _active.value = true
        replan(apply = true)
    }

    /** River steering: only the energy axis; keeps the current novelty. */
    fun steerEnergy(value: Float) {
        val n = _target.value?.novelty ?: currentSong()?.novelty ?: 0.4f
        steer(FlowTarget(value, n))
    }

    fun start(meta: MediaMetadata) {
        _active.value = true
        _target.value = null
        connection?.playQueue(YouTubeQueue.radio(meta))
    }

    fun playNow(id: String) {
        val song = _pool.value[id] ?: return
        val pc = connection ?: return
        _active.value = true
        if (currentId == null) {
            start(song.meta)
            return
        }
        runCatching {
            pc.playNext(song.meta.toMediaItem())
            pc.seekToNext()
        }
    }

    fun skip(reason: SkipReason) {
        val id = currentId ?: return
        if (reason == SkipReason.LATER) later[id] = 5 else banned.add(id)
        _active.value = true
        // Make sure what comes next is our plan before we move on
        applyNow()
        runCatching { connection?.seekToNext() }
    }

    private suspend fun seed(meta: MediaMetadata) {
        // Whatever the player already has queued is a free first batch
        add(queuedSongs())
        if (!seeded.add(meta.id)) return
        _loading.value = true
        val items = withContext(Dispatchers.IO) {
            // Music radio first; plain videos often get an empty radio, so then use the
            // ordinary "up next" list and the related songs, like YouTubeQueue does
            val radio = YouTube.next(WatchEndpoint(videoId = meta.id, playlistId = "RDAMVM${meta.id}"))
                .getOrNull()?.items.orEmpty()
            if (radio.size > 1) return@withContext radio
            val plain = YouTube.next(WatchEndpoint(videoId = meta.id)).getOrNull()
            val related = plain?.relatedEndpoint?.let { YouTube.related(it).getOrNull()?.songs }.orEmpty()
            plain?.items.orEmpty() + related
        }
        // Nothing came back (network?): let the next song change try again
        if (items.isEmpty()) seeded.remove(meta.id)
        add(items.map { it.toMediaMetadata() }.filter { it.id != meta.id })
        _loading.value = false
    }

    private fun queuedSongs(): List<MediaMetadata> = runCatching {
        val player = connection?.player ?: return emptyList()
        (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).metadata }
    }.getOrDefault(emptyList())

    /**
     * Puts songs on the map right away (cached energy or a guess from the title),
     * then measures the guessed ones on Deezer in the background and moves them.
     */
    private suspend fun add(metas: List<MediaMetadata>) {
        val fresh = metas.distinctBy { it.id }.filter { it.id !in entries }
        if (fresh.isEmpty()) return
        val e = energy ?: return
        val db = database
        val plays = withContext(Dispatchers.IO) {
            fresh.associate { it.id to (runCatching { db?.getSongById(it.id)?.song?.totalPlayTime }.getOrNull() ?: 0L) }
        }
        fresh.forEach { meta ->
            val minutes = (plays[meta.id] ?: 0L) / 60000.0
            val novelty = (1.0 - ln(1.0 + minutes) / ln(31.0)).coerceIn(0.0, 1.0).toFloat()
            val (raw, measured) = e.quick(meta)
            entries[meta.id] = Entry(meta, raw, measured, novelty)
        }
        trim()
        publish()
        fresh.filter { entries[it.id]?.measured == false && pending.add(it.id) }.forEach { meta ->
            scope.launch {
                val value = limit.withPermit { e.measure(meta) }
                pending.remove(meta.id)
                val old = entries[meta.id] ?: return@launch
                if (value == null) return@launch
                entries[meta.id] = Entry(old.meta, value, true, old.novelty)
                publishSoon()
            }
        }
    }

    private var publishJob: Job? = null

    /** Measurements arrive one by one; move the dots and redo the plan a little later in one go. */
    private fun publishSoon() {
        if (publishJob?.isActive == true) return
        publishJob = scope.launch {
            delay(700)
            publish()
            replan(apply = _active.value)
        }
    }

    private fun trim() {
        val keep = _plan.value.toSet() + listOfNotNull(currentId)
        val extra = entries.size - 90
        if (extra <= 0) return
        entries.keys.filter { it !in keep }.take(extra).forEach { entries.remove(it) }
    }

    /** Raw energies cluster, so blend each with its rank to spread songs across the pad. */
    private fun publish() {
        val sorted = entries.values.sortedBy { it.raw }
        val n = sorted.size
        val map = LinkedHashMap<String, FlowSong>()
        sorted.forEachIndexed { i, en ->
            val rank = if (n > 1) i.toFloat() / (n - 1) else 0.5f
            map[en.meta.id] = FlowSong(en.meta, 0.5f * en.raw + 0.5f * rank, en.novelty, en.measured)
        }
        _pool.value = map
    }

    private fun replan(apply: Boolean) {
        val pool = _pool.value
        val cur = currentId?.let { pool[it] } ?: return
        val target = _target.value ?: FlowTarget(cur.energy, cur.novelty)
        val skip = played.toSet() + banned + later.keys + cur.meta.id
        val avail = pool.values.filter { it.meta.id !in skip }.toMutableList()
        val out = mutableListOf<String>()
        var prev = cur
        val steps = 8
        for (i in 0 until steps) {
            if (avail.isEmpty()) break
            // Walk gradually from the current song toward the target
            val f = min(1f, (i + 1f) / steps * 1.4f)
            val we = cur.energy + (target.energy - cur.energy) * f
            val wn = cur.novelty + (target.novelty - cur.novelty) * f
            val best = avail.minBy {
                sq(it.energy - we) + 0.8f * sq(it.novelty - wn) + 0.12f * sq(it.energy - prev.energy)
            }
            out.add(best.meta.id)
            avail.remove(best)
            prev = best
        }
        _plan.value = out
        if (apply) scheduleApply()
    }

    private fun sq(v: Float) = v * v

    private fun scheduleApply() {
        applyJob?.cancel()
        applyJob = scope.launch {
            delay(400)
            applyNow()
        }
    }

    /** Replaces the songs after the current one with the plan. */
    private fun applyNow() {
        val pc = connection ?: return
        val want = _plan.value
        if (want.isEmpty()) return
        runCatching {
            val player = pc.player
            val idx = player.currentMediaItemIndex
            if (idx < 0) return
            val count = player.mediaItemCount
            val upcoming = (idx + 1 until count).map { player.getMediaItemAt(it).mediaId }
            if (upcoming.take(want.size) == want) return
            val items = want.mapNotNull { _pool.value[it]?.meta?.toMediaItem() }
            if (count > idx + 1) player.removeMediaItems(idx + 1, count)
            player.addMediaItems(items)
        }
    }
}
