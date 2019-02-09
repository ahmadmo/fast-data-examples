package sampler

import com.google.protobuf.util.JsonFormat
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.asynchttpclient.Dsl.asyncHttpClient
import org.asynchttpclient.Dsl.config
import receiver.Events
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

val logger = KotlinLogging.logger {}

fun loadConfig(file: File?): Config {
    return if (file == null) {
        logger.info { "loading default config" }
        ConfigFactory.load()
    } else {
        logger.info { "loading config [file = $file]" }
        ConfigFactory.parseFile(file).resolve()
    }
}

fun main(args: Array<String>) {
    val config = loadConfig(args.getOrNull(0)?.let { File(it) })

    val receiverEndpoint = config.getString("receiver.endpoint")
    val impressionsApi = config.getString("receiver.api.impressions")
    val clicksApi = config.getString("receiver.api.clicks")

    val httpClient = asyncHttpClient(config().setKeepAlive(true))
    val engine = Engine(config.getConfig("engine")).apply { start() }

    runBlocking {
        for (event in engine.events) {
            launch {
                val api = when (event) {
                    is Events.Impression -> impressionsApi
                    is Events.Click -> clicksApi
                    else -> throw IllegalStateException("unknown event = $event")
                }
                val json = JsonFormat.printer().print(event)
                val future = httpClient
                    .preparePut("$receiverEndpoint$api")
                    .addHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .setBody(json)
                    .execute()
                    .toCompletableFuture()
                suspendCoroutine<Any> {
                    it.resume(future.join())
                }
            }
        }
    }

    httpClient.close()
}
