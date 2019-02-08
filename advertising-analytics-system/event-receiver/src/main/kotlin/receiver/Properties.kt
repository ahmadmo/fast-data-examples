package receiver

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("kafka")
class KafkaProperties {

    class Topic {
        lateinit var name: String
        var numPartitions: Int = 0
        var replicationFactor: Short = 0
    }

    val impressionsTopic = Topic()
    val clicksTopic = Topic()
}
