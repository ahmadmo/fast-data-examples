package receiver

import com.google.protobuf.Message
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import org.springframework.util.concurrent.ListenableFutureCallback
import reactor.core.publisher.Mono

sealed class EventProducer(private val topic: String,
                           private val kafka: KafkaTemplate<String, ByteArray>) {

    private val logger = KotlinLogging.logger {}

    fun send(event: Message): Mono<Boolean> = Mono.create<Boolean> { sink ->
        val callback = object : ListenableFutureCallback<SendResult<String, ByteArray>> {
            override fun onSuccess(result: SendResult<String, ByteArray>?) {
                // TODO pretty log
                logger.debug { "event sent to kafka [$event]" }
                sink.success(true)
            }

            override fun onFailure(error: Throwable) {
                // TODO pretty log
                logger.error(error) { "event send error [$event]" }
                sink.success(false)
            }
        }
        kafka.send(topic, event.toByteArray()).addCallback(callback)
    }
}

@Component
class ImpressionEventProducer(
        props: KafkaProperties,
        @Qualifier("impressionsKafkaTemplate") kafka: KafkaTemplate<String, ByteArray>
) : EventProducer(props.impressionsTopic.name, kafka)

@Component
class ClickEventProducer(
        props: KafkaProperties,
        @Qualifier("clicksKafkaTemplate") kafka: KafkaTemplate<String, ByteArray>
) : EventProducer(props.clicksTopic.name, kafka)
