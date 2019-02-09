@file:Suppress("UnstableApiUsage", "EXPERIMENTAL_API_USAGE")

package sampler

import com.google.common.util.concurrent.RateLimiter
import com.google.protobuf.Message
import com.typesafe.config.Config
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import org.apache.commons.lang3.RandomStringUtils
import receiver.Events
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Engine(config: Config) : CoroutineScope {

    private val eventsPerSecond = config.getDouble("eventsPerSecond")
    private val outOfOrderEventsPercentage = config.getDouble("outOfOrderEventsPercentage")
    private val incompleteEventsPercentage = config.getDouble("incompleteEventsPercentage")
    private val completionDelayMin = config.getDuration("completionDelay.min").toMillis()
    private val completionDelayMax = config.getDuration("completionDelay.max").toMillis()

    init {
        require(outOfOrderEventsPercentage + incompleteEventsPercentage in 0.0..1.0)
        require(completionDelayMax > 0 && completionDelayMin in 0..(completionDelayMax - 1))

        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            stop()
        })
    }

    private val rateLimiter = RateLimiter.create(eventsPerSecond)

    private var started = false
    private lateinit var job: Job

    private lateinit var firstEvents: Channel<Message>
    private lateinit var outgoingEvents: Channel<Message>

    private fun randomString(count: Int = 10): String =
        RandomStringUtils.randomAlphanumeric(count)

    private fun Random.nextImpressionEvent(requestId: String, clickTime: Long? = null): Events.Impression =
        Events.Impression.newBuilder()
            .setRequestId(requestId)
            .setAdId(UUID.randomUUID().toString())
            .setAdTitle(randomString())
            .setAdvertiserCost(nextDouble())
            .setAppId(UUID.randomUUID().toString())
            .setAppTitle(randomString())
            .setImpressionTime(clickTime?.minus(completionDelayMin) ?: System.currentTimeMillis())
            .build()

    private fun nextClickEvent(requestId: String): Events.Click =
        Events.Click.newBuilder()
            .setRequestId(requestId)
            .setClickTime(System.currentTimeMillis())
            .build()

    private fun Random.nextEvent(): Message {
        val requestId = UUID.randomUUID().toString()
        return if (nextDouble() < outOfOrderEventsPercentage) {
            nextClickEvent(requestId)
        } else {
            nextImpressionEvent(requestId)
        }
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    @Synchronized
    fun start() {
        check(!started) { "engine can not be started twice." }
        started = true
        job = Job()
        firstEvents = Channel()
        outgoingEvents = Channel()
        launch {
            while (isActive) {
                suspendCoroutine<Any> { it.resume(rateLimiter.acquire()) }
                with(ThreadLocalRandom.current()) {
                    val first = nextEvent()
                    outgoingEvents.send(first)
                    if (nextDouble() >= incompleteEventsPercentage) {
                        firstEvents.send(first)
                    }
                }
            }
        }
        launch {
            for (first in firstEvents) {
                launch {
                    with(ThreadLocalRandom.current()) {
                        delay(nextLong(completionDelayMin, completionDelayMax + 1))
                        val second = when (first) {
                            is Events.Impression -> nextClickEvent(first.requestId)
                            is Events.Click -> nextImpressionEvent(first.requestId, first.clickTime)
                            else -> throw IllegalStateException("unknown event = $first")
                        }
                        outgoingEvents.send(second)
                    }
                }
            }
        }
    }

    val events: ReceiveChannel<Message>
        @Synchronized get() {
            check(started) { "engine not started yet." }
            return outgoingEvents
        }

    @Synchronized
    fun stop() {
        if (started) {
            job.cancel()
        }
    }
}
