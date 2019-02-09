package receiver

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import mu.KotlinLogging
import org.apache.curator.framework.CuratorFramework
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate

@Configuration
class KafkaAdminConfig(private val curatorFramework: CuratorFramework,
                       private val objectMapper: ObjectMapper) {

    final val brokers: String

    init {
        val endpoints = discoverKafkaBrokerEndpoints()
        if (endpoints.isEmpty()) {
            throw ExceptionInInitializerError("could not find any kafka broker endpoint.")
        }
        brokers = endpoints.joinToString(",")
        KotlinLogging.logger {}.info {
            "found kafka brokers [endpoints = $endpoints]"
        }
    }

    private fun discoverKafkaBrokerEndpoints(): List<String> {
        val zNode = "/brokers/ids"
        val brokers = curatorFramework.children.forPath(zNode)
        val endpoints = ArrayList<String>()
        for (broker in brokers) {
            val data = curatorFramework.data.forPath("$zNode/$broker")
            val json = objectMapper.readValue<ObjectNode>(data, ObjectNode::class.java)
            for (endpoint in json["endpoints"] as ArrayNode) {
                endpoints.add(endpoint.asText())
            }
        }
        return endpoints
    }

    @Bean
    fun admin(): KafkaAdmin {
        val config = mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to brokers)
        return KafkaAdmin(config)
    }
}

@Configuration
class KafkaTopicConfig(private val props: KafkaProperties) {

    @Bean
    fun impressionsTopic() = with(props.impressionsTopic) {
        NewTopic(name, numPartitions, replicationFactor)
    }

    @Bean
    fun clicksTopic() = with(props.clicksTopic) {
        NewTopic(name, numPartitions, replicationFactor)
    }
}

@Configuration
class KafkaProducerConfig(private val adminConfig: KafkaAdminConfig) {

    @Bean
    fun impressionsKafkaTemplate(): KafkaTemplate<String, ByteArray> {
        val config = mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to adminConfig.brokers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java)
        return KafkaTemplate(DefaultKafkaProducerFactory(config))
    }

    @Bean
    fun clicksKafkaTemplate(): KafkaTemplate<String, ByteArray> {
        val config = mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to adminConfig.brokers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java)
        return KafkaTemplate(DefaultKafkaProducerFactory(config))
    }
}
