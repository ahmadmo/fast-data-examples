## Create Cassandra Table
```sql
CREATE KEYSPACE test WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'}

USE test;

CREATE TABLE test.impression_by_request_id (
    request_id text PRIMARY KEY,
    ad_id text,
    ad_title text,
    advertiser_cost double,
    app_id text,
    app_title text,
    impression_time bigint
);
```

## Create Elasticsearch Index
```http
PUT /ad-events
{
  "settings": {
    "index.number_of_shards" : 1,
    "index.number_of_replicas" : 0
  }
}
```

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
