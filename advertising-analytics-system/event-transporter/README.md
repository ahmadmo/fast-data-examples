## Build Assembly JAR
```bash
sbt clean assembly
```

## Run application on local standalone Spark cluster
```bash
spark-submit \
--packages org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.0 \
--class transporter.SparkApp \
--master local[*] \
/path/to/event-transporter-assembly-1.0.jar
```
