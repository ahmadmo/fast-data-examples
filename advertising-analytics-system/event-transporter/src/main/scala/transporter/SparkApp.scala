package transporter

import java.io.File

import com.datastax.spark.connector.cql.{CassandraConnector, CassandraConnectorConf}
import com.datastax.spark.connector.toRDDFunctions
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.apache.spark.sql.streaming.{OutputMode, Trigger}
import org.apache.spark.sql.{Encoder, Encoders, SparkSession}
import org.elasticsearch.hadoop.cfg.{ConfigurationOptions => EsConf}
import org.elasticsearch.spark.sparkRDDFunctions
import transporter.event.{Ad, Click, Impression}

object SparkApp extends App {

  val logger = Logger(getClass)

  val config = args.headOption.map { configPath =>
    val file = new File(configPath)
    logger.info(s"loading config [file = $file]")
    ConfigFactory.parseFile(file).resolve()
  }.getOrElse {
    logger.info("loading default config")
    ConfigFactory.load()
  }

  val sparkConfig = config.getConfig("spark")
  val kafkaConfig = config.getConfig("kafka")
  val cassandraConfig = config.getConfig("cassandra")
  val esConfig = config.getConfig("es")

  private val batchDurationMs = sparkConfig.getDuration("batchDuration").toMillis
  private val checkpointLocation = sparkConfig.getString("checkpointLocation")
  private val kafkaBrokers = kafkaConfig.getString("brokers")
  private val cassandraKeyspace = cassandraConfig.getString("keyspace")
  private val cassandraTable = cassandraConfig.getString("table")
  private val esResource = esConfig.getString("resource")

  val spark = SparkSession
    .builder
    .appName("event-transporter")
    .config(CassandraConnectorConf.ConnectionHostParam.name, cassandraConfig.getString("host"))
    .config(CassandraConnectorConf.KeepAliveMillisParam.name, cassandraConfig.getDuration("keepAlive").toMillis)
    .config(EsConf.ES_NODES, esConfig.getString("nodes"))
    .config(EsConf.ES_MAPPING_ID, esConfig.getString("mappingId"))
    .getOrCreate()

  implicit val impressionEncoding: Encoder[Impression] = Encoders.kryo[Impression]
  implicit val clickEncoding: Encoder[Click] = Encoders.kryo[Click]
  implicit val adEncoding: Encoder[Ad] = Encoders.kryo[Ad]

  implicit val cassandraConnector: CassandraConnector = CassandraConnector(spark.sparkContext)

  import spark.implicits._

  val impressions = spark
    .readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", kafkaBrokers)
    .option("subscribe", kafkaConfig.getString("impressionsTopic"))
    .option("startingOffsets", "earliest")
    .load()
    .selectExpr("value")
    .as[Array[Byte]]
    .map(Impression.parseFrom)

  val clicks = spark
    .readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", kafkaBrokers)
    .option("subscribe", kafkaConfig.getString("clicksTopic"))
    .option("startingOffsets", "earliest")
    .load()
    .selectExpr("value")
    .as[Array[Byte]]
    .map(Click.parseFrom)

  // FIXME out-of-order events
  // possible solutions:
  // 1. watermarks
  // 2. external cache (Redis, Ignite, etc.)
  // 3. feedback to kafka
  // 4. reprocess early data (clicks)

  val cassandraSink = impressions
    .writeStream
    .outputMode(OutputMode.Update())
    .option("checkpointLocation", checkpointLocation + "/impression")
    .trigger(Trigger.ProcessingTime(batchDurationMs))
    .foreachBatch { (set, _) =>
      set.rdd
        // .repartitionByCassandraReplica(cassandraKeyspace, cassandraTable)
        .saveToCassandra(cassandraKeyspace, cassandraTable)
    }
    .start()

  val elasticsearchSink = clicks
    .writeStream
    .outputMode(OutputMode.Append())
    .option("checkpointLocation", checkpointLocation + "/click")
    .trigger(Trigger.ProcessingTime(batchDurationMs))
    .foreachBatch { (set, _) =>
      set.rdd
        // .repartitionByCassandraReplica(cassandraKeyspace, cassandraTable)
        .joinWithCassandraTable[Impression](cassandraKeyspace, cassandraTable)
        .map { case (click, impression) =>
          Ad(
            impression.requestId,
            impression.adId,
            impression.adTitle,
            impression.advertiserCost,
            impression.appId,
            impression.appTitle,
            impression.impressionTime,
            click.clickTime)
        }
        .saveToEs(esResource)
    }
    .start()

  cassandraSink.awaitTermination()
  elasticsearchSink.awaitTermination()
}
