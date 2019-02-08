package receiver

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.router

@Configuration
class ReceiverRouter(private val impressionsHandler: ImpressionEventHandler,
                     private val clicksHandler: ClickEventHandler) {

    @Bean
    fun eventsRouter() = router {
        (accept(MediaType.APPLICATION_JSON) and "/events").nest {
            PUT("/impression", impressionsHandler::handle)
            PUT("/click", clicksHandler::handle)
        }
    }
}
