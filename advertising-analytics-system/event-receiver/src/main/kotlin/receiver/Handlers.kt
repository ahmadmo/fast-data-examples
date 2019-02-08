package receiver

import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

sealed class EventHandler(private val producer: EventProducer) {

    abstract fun newEventBuilder(): Message.Builder

    private fun ServerRequest.bodyToEvent(): Mono<Message> =
            bodyToMono(String::class.java).map { json ->
                val builder = newEventBuilder()
                JsonFormat.parser().merge(json, builder)
                builder.build()
            }

    fun handle(request: ServerRequest): Mono<ServerResponse> =
            request.bodyToEvent().flatMap(producer::send).flatMap { sent ->
                if (sent) ServerResponse.ok().build()
                else ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            }
}

@Component
class ImpressionEventHandler(producer: ImpressionEventProducer) : EventHandler(producer) {

    override fun newEventBuilder(): Message.Builder =
            Events.Impression.newBuilder()
}

@Component
class ClickEventHandler(producer: ClickEventProducer) : EventHandler(producer) {

    override fun newEventBuilder(): Message.Builder =
            Events.Click.newBuilder()
}
