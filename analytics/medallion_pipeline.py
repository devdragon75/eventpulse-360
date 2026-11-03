"""
EventPulse 360 - Bronze / Silver / Gold analytics pipeline (PySpark).

Input  : registration events published by the Spring Boot app (Kafka topic `eventpulse.registrations`),
         or JSON-lines files with the same payload (for running without Kafka).
Layers : bronze = raw, append-only, replayable
         silver = parsed, typed, de-duplicated, PII-hashed; bad records quarantined
         gold   = business-ready aggregates for dashboards

Runs unchanged on Databricks (use --format delta and DBFS/Unity Catalog paths) or locally:

    spark-submit medallion_pipeline.py --source files --input sample_data/events.jsonl --base-path ./lakehouse
    spark-submit --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.1 medallion_pipeline.py \
        --source kafka --kafka-bootstrap localhost:9092 --base-path ./lakehouse
"""
from __future__ import annotations

import argparse

from pyspark.sql import DataFrame, SparkSession, Window
from pyspark.sql import functions as F
from pyspark.sql.types import (DecimalType, LongType, StringType, StructField, StructType,
                               TimestampType)

# Contract with com.eventpulse.messaging.RegistrationEvent. Fields are read as strings first and cast
# later so one malformed value can't null out the whole record.
EVENT_SCHEMA = StructType([
    StructField("eventType", StringType()),
    StructField("registrationId", LongType()),
    StructField("eventId", LongType()),
    StructField("eventTitle", StringType()),
    StructField("ticketTypeId", LongType()),
    StructField("ticketType", StringType()),
    StructField("attendeeId", LongType()),
    StructField("attendeeEmail", StringType()),
    StructField("amount", StringType()),
    StructField("currency", StringType()),
    StructField("occurredAt", StringType()),
])

VALID_EVENT_TYPES = ["REGISTRATION_CONFIRMED", "REGISTRATION_CANCELLED"]


# --------------------------------------------------------------------------------------------
# Bronze: land the data exactly as received, plus lineage columns. Never transform here.
# --------------------------------------------------------------------------------------------
def ingest_bronze_from_files(spark: SparkSession, input_path: str, bronze_path: str, fmt: str) -> None:
    raw = (spark.read.text(input_path)
           .select(F.col("value").alias("raw_payload"), F.col("_metadata.file_path").alias("source_ref"))
           .withColumn("source", F.lit("file"))
           .withColumn("ingested_at", F.current_timestamp())
           .withColumn("ingest_date", F.to_date("ingested_at")))
    raw.write.format(fmt).mode("append").partitionBy("ingest_date").save(bronze_path)


def ingest_bronze_from_kafka(spark: SparkSession, bootstrap: str, topic: str, bronze_path: str,
                             checkpoint_path: str, fmt: str) -> None:
    """Incremental, checkpointed micro-batch: each run only reads offsets it hasn't seen."""
    stream = (spark.readStream.format("kafka")
              .option("kafka.bootstrap.servers", bootstrap)
              .option("subscribe", topic)
              .option("startingOffsets", "earliest")
              .load()
              .select(F.col("value").cast("string").alias("raw_payload"),
                      F.lit("kafka").alias("source"),
                      F.concat_ws(":", F.col("topic"), F.col("partition").cast("string"),
                                  F.col("offset").cast("string")).alias("source_ref"),
                      F.current_timestamp().alias("ingested_at"))
              .withColumn("ingest_date", F.to_date("ingested_at")))
    (stream.writeStream.format(fmt)
     .option("checkpointLocation", checkpoint_path)
     .partitionBy("ingest_date")
     .trigger(availableNow=True)
     .start(bronze_path)
     .awaitTermination())


# --------------------------------------------------------------------------------------------
# Silver: trustworthy, typed, de-duplicated events. Rejects are kept, not silently dropped.
# --------------------------------------------------------------------------------------------
def build_silver(bronze: DataFrame) -> tuple[DataFrame, DataFrame]:
    parsed = (bronze
              .withColumn("e", F.from_json("raw_payload", EVENT_SCHEMA))
              .select("raw_payload", "source", "source_ref", "ingested_at", "e.*")
              .withColumn("amount_dec", F.col("amount").cast(DecimalType(10, 2)))
              .withColumn("occurred_ts", F.to_timestamp("occurredAt")))

    is_valid = (F.col("registrationId").isNotNull()
                & F.col("eventId").isNotNull()
                & F.col("eventType").isin(VALID_EVENT_TYPES)
                & F.col("amount_dec").isNotNull() & (F.col("amount_dec") >= 0)
                & F.col("occurred_ts").isNotNull())

    rejected = (parsed.filter(~is_valid)
                .select("raw_payload", "source", "source_ref", "ingested_at"))

    # At-least-once delivery means the same business event can arrive twice; keep one copy.
    latest_first = Window.partitionBy("registrationId", "eventType").orderBy(F.col("ingested_at").desc())
    silver = (parsed.filter(is_valid)
              .withColumn("_rn", F.row_number().over(latest_first))
              .filter(F.col("_rn") == 1).drop("_rn")
              .select(
                  F.col("registrationId").alias("registration_id"),
                  F.col("eventType").alias("event_type"),
                  F.col("eventId").alias("event_id"),
                  F.col("eventTitle").alias("event_title"),
                  F.col("ticketTypeId").alias("ticket_type_id"),
                  F.col("ticketType").alias("ticket_type"),
                  F.col("attendeeId").alias("attendee_id"),
                  # PII never leaves bronze: analysts get a stable pseudonym, not an email address.
                  F.sha2(F.lower(F.trim("attendeeEmail")), 256).alias("attendee_hash"),
                  F.col("amount_dec").alias("amount"),
                  F.coalesce("currency", F.lit("USD")).alias("currency"),
                  F.col("occurred_ts").alias("occurred_at"),
                  F.to_date("occurred_ts").alias("event_date"),
                  # Signed so that SUM(net_amount) is revenue after refunds.
                  F.when(F.col("eventType") == "REGISTRATION_CONFIRMED", F.col("amount_dec"))
                   .otherwise(-F.col("amount_dec")).alias("net_amount"),
                  F.when(F.col("eventType") == "REGISTRATION_CONFIRMED", 1)
                   .otherwise(-1).alias("net_registrations"),
                  "ingested_at"))
    return silver, rejected


# --------------------------------------------------------------------------------------------
# Gold: aggregates shaped for dashboards.
# --------------------------------------------------------------------------------------------
def build_gold(silver: DataFrame) -> dict[str, DataFrame]:
    confirmed = F.col("event_type") == "REGISTRATION_CONFIRMED"
    cancelled = F.col("event_type") == "REGISTRATION_CANCELLED"

    daily = (silver.groupBy("event_id", "event_title", "event_date")
             .agg(F.sum(F.when(confirmed, 1).otherwise(0)).alias("registrations"),
                  F.sum(F.when(cancelled, 1).otherwise(0)).alias("cancellations"),
                  F.sum("net_registrations").alias("net_registrations"),
                  F.sum(F.when(confirmed, F.col("amount")).otherwise(0)).alias("gross_revenue"),
                  F.sum(F.when(cancelled, F.col("amount")).otherwise(0)).alias("refunds"),
                  F.sum("net_amount").alias("net_revenue"))
             .orderBy("event_id", "event_date"))

    kpis = (silver.groupBy("event_id", "event_title")
            .agg(F.sum(F.when(confirmed, 1).otherwise(0)).alias("registrations"),
                 F.sum(F.when(cancelled, 1).otherwise(0)).alias("cancellations"),
                 F.sum("net_registrations").alias("net_registrations"),
                 F.sum("net_amount").alias("net_revenue"),
                 F.countDistinct("attendee_hash").alias("unique_attendees"),
                 F.min(F.when(confirmed, F.col("occurred_at"))).alias("first_registration_at"),
                 F.max(F.when(confirmed, F.col("occurred_at"))).alias("last_registration_at"))
            .withColumn("cancellation_rate",
                        F.round(F.col("cancellations") / F.greatest(F.col("registrations"), F.lit(1)), 4))
            .withColumn("avg_net_ticket_value",
                        F.round(F.col("net_revenue") / F.greatest(F.col("net_registrations"), F.lit(1)), 2))
            .orderBy(F.col("net_revenue").desc()))

    mix = (silver.groupBy("event_id", "event_title", "ticket_type")
           .agg(F.sum("net_registrations").alias("net_registrations"),
                F.sum("net_amount").alias("net_revenue")))
    per_event = Window.partitionBy("event_id")
    mix = (mix.withColumn("share_of_event_revenue",
                          F.round(F.col("net_revenue") / F.nullif(F.sum("net_revenue").over(per_event), F.lit(0)), 4))
           .orderBy("event_id", F.col("net_revenue").desc()))

    return {"gold_daily_event_metrics": daily, "gold_event_kpis": kpis, "gold_ticket_mix": mix}


# --------------------------------------------------------------------------------------------
def main() -> None:
    p = argparse.ArgumentParser(description="EventPulse 360 medallion pipeline")
    p.add_argument("--source", choices=["files", "kafka"], default="files")
    p.add_argument("--input", help="JSON-lines file or directory (source=files)")
    p.add_argument("--kafka-bootstrap", default="localhost:9092")
    p.add_argument("--topic", default="eventpulse.registrations")
    p.add_argument("--base-path", default="./lakehouse", help="Root for bronze/silver/gold/checkpoints")
    p.add_argument("--format", choices=["parquet", "delta"], default="parquet")
    p.add_argument("--stage", choices=["all", "bronze", "silver", "gold"], default="all")
    args = p.parse_args()

    spark = (SparkSession.builder.appName("eventpulse-medallion")
             .config("spark.sql.session.timeZone", "UTC").getOrCreate())
    base = args.base_path.rstrip("/")
    bronze_path, silver_path, rejected_path = f"{base}/bronze/registrations", f"{base}/silver/registrations", f"{base}/silver/_rejected"

    if args.stage in ("all", "bronze"):
        if args.source == "files":
            if not args.input:
                p.error("--input is required with --source files")
            ingest_bronze_from_files(spark, args.input, bronze_path, args.format)
        else:
            ingest_bronze_from_kafka(spark, args.kafka_bootstrap, args.topic, bronze_path,
                                     f"{base}/_checkpoints/bronze_registrations", args.format)

    if args.stage in ("all", "silver"):
        silver, rejected = build_silver(spark.read.format(args.format).load(bronze_path))
        silver.write.format(args.format).mode("overwrite").save(silver_path)
        rejected.write.format(args.format).mode("overwrite").save(rejected_path)
        print(f"silver rows: {silver.count()}  rejected rows: {rejected.count()}")

    if args.stage in ("all", "gold"):
        gold = build_gold(spark.read.format(args.format).load(silver_path))
        for name, df in gold.items():
            df.write.format(args.format).mode("overwrite").save(f"{base}/gold/{name}")
        print("gold tables written:", ", ".join(gold))

    spark.stop()


if __name__ == "__main__":
    main()
