package transporter

import java.io.File

import com.datastax.spark.connector.cql.{CassandraConnector, CassandraConnectorConf}
import com.datastax.spark.connector.toRDDFunctions
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.apache.spark.sql.streaming.Trigger
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

  val impressionsFromKafka = spark
    .readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", kafkaBrokers)
    .option("subscribe", kafkaConfig.getString("impressionsTopic"))
    .option("startingOffsets", "earliest")
    .load()
    .selectExpr("value")
    .as[Array[Byte]]
    .map(Impression.parseFrom)

  val clicksFromKafka = spark
    .readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", kafkaBrokers)
    .option("subscribe", kafkaConfig.getString("clicksTopic"))
    .option("startingOffsets", "earliest")
    .load()
    .selectExpr("value")
    .as[Array[Byte]]
    .map(Click.parseFrom)

  // TODO improve out-of-order event handling
  // possible approaches: (sorted by complexity asc)
  // 1. feedback to kafka and reprocess early data (CURRENT)
  // 2. external cache (Redis, Ignite, etc.)
  // 3. using watermarks (a Spark Source must be implemented for C*)

  val cassandraSink = impressionsFromKafka
    .writeStream
    .option("checkpointLocation", checkpointLocation + "/impression")
    .trigger(Trigger.ProcessingTime(batchDurationMs))
    .foreachBatch { (set, _) =>
      set.rdd.saveToCassandra(cassandraKeyspace, cassandraTable)
    }
    .queryName("CassandraSink")
    .start()

  val elasticsearchSink = clicksFromKafka
    .writeStream
    .option("checkpointLocation", checkpointLocation + "/click")
    .trigger(Trigger.ProcessingTime(batchDurationMs))
    .foreachBatch { (set, _) =>

      val clicks = set.rdd.cache()

      val matchedClicks = clicks
        .repartitionByCassandraReplica(cassandraKeyspace, cassandraTable)
        .joinWithCassandraTable[Impression](cassandraKeyspace, cassandraTable)
        .cache()

      val unmatchedClicks = clicks.subtract(matchedClicks.map(_._1))

      matchedClicks
        .filter {
          case (click, impression) =>
            if (click.clickTime < impression.impressionTime) {
              // OPTIONAL: save to kafka for further analysis
              logger.warn(s"invalid click event time [impression = $impression, click = $click]")
              false
            } else {
              true
            }
        }
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

      // feedback unmatched clicks to kafka
      // this is not an optimal solution, but it works!
      unmatchedClicks
        .map(_.toByteArray)
        .toDS()
        .write
        .format("kafka")
        .option("kafka.bootstrap.servers", kafkaBrokers)
        .option("topic", kafkaConfig.getString("clicksTopic"))
        .save()
    }
    .queryName("ElasticsearchSink")
    .start()

  cassandraSink.awaitTermination()
  elasticsearchSink.awaitTermination()
}
