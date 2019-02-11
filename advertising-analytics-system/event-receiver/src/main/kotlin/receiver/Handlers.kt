package receiver

import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

sealed class EventHandler(private val producer: EventProducer) {

    protected abstract fun newEventBuilder(): Message.Builder

    protected abstract fun validate(event: Message): Boolean

    private fun ServerRequest.bodyToEvent(): Mono<Message> =
            bodyToMono(String::class.java).map { json ->
                val builder = newEventBuilder()
                JsonFormat.parser().merge(json, builder)
                builder.build()
            }

    fun handle(request: ServerRequest): Mono<ServerResponse> =
            request.bodyToEvent().flatMap { event ->
                when {
                    validate(event) -> producer.send(event).flatMap { sent ->
                        if (sent) ServerResponse.ok().build()
                        else ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                    }
                    else -> ServerResponse.status(HttpStatus.BAD_REQUEST).build()
                }
            }
}

@Component
class ImpressionEventHandler(producer: ImpressionEventProducer) : EventHandler(producer) {

    override fun newEventBuilder(): Message.Builder =
            Events.Impression.newBuilder()

    override fun validate(event: Message): Boolean =
            (event as Events.Impression).impressionTime <= System.currentTimeMillis()
}

@Component
class ClickEventHandler(producer: ClickEventProducer) : EventHandler(producer) {

    override fun newEventBuilder(): Message.Builder =
            Events.Click.newBuilder()

    override fun validate(event: Message): Boolean =
            (event as Events.Click).clickTime <= System.currentTimeMillis()
}
