package jotting.simple

import org.apache.spark.sql.SparkSession
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.Row
import org.apache.spark.sql.functions.to_date
import org.apache.spark.sql.SaveMode
import org.apache.spark.sql.execution.datasources.jdbc.JdbcUtils
import org.apache.spark.sql.execution.datasources.jdbc.JdbcOptionsInWrite

import jotting.Jotting._

object Job3 {
  def main(args: Array[String]): Unit = {
    implicit val spark = SparkSession
      .builder()
      .appName("Spark SQL runJdbcDatasetUpsert example")
      .config("spark.sql.sources.partitionOverwriteMode", "dynamic")
      .getOrCreate()

    val config: Config = ConfigFactory.load(configFile).getConfig("job2")
    val conn = Conn(
      config.getString("db"),
      config.getString("driver"),
      config.getString("host"),
      config.getInt("port"),
      config.getString("database"),
      config.getString("user"),
      config.getString("password")
    )

    // runJdbcDatasetWrite(conn)
    runJdbcDatasetUpsert(conn)

    spark.stop()
  }

  val save_to = "job3"

  // TODO:
  // automation: save table then alter table:
  // ALTER TABLE
  //   job3
  // ADD CONSTRAINT
  //   date_language
  // UNIQUE (date, language)
  private def runJdbcDatasetWrite(conn: Conn)(implicit spark: SparkSession): Unit = {
    import spark.implicits._

    val data = Seq(
      ("2010-01-23", "Java", "20000"),
      ("2010-01-23", "Python", "100000"),
      ("2010-01-23", "Scala", "3000"),
      ("2015-08-15", "Java", "25000"),
      ("2015-08-15", "Python", "150000"),
      ("2015-08-15", "Scala", "2000")
    )
    val df = spark
      .createDataFrame(data)
      .toDF("date", "language", "users_count")
      .withColumn("date", to_date($"date", "yyyy-MM-dd"))

    df.write
      .format("jdbc")
      .options(conn.options)
      .option("dbtable", save_to)
      .mode("overwrite")
      .save()

  }

  private def runJdbcDatasetUpsert(conn: Conn)(implicit spark: SparkSession): Unit = {
    import spark.implicits._

    val data0 = Seq(
      ("2010-01-23", "Java", "20000")
    )
    val df0 = spark
      .createDataFrame(data0)
      .toDF("date", "language", "users_count")
      .withColumn("date", to_date($"date", "yyyy-MM-dd"))

    val data = Seq(
      ("2010-01-23", "Java", "20000", "21000"),
      ("2010-01-23", "Python", "100000", "100000"),
      ("2010-01-23", "Scala", "3000", "15000"),
      ("2015-08-15", "Java", "25000", "24000"),
      ("2015-08-15", "Python", "150000", "135000"),
      ("2015-08-15", "Scala", "2000", "2200"),
      ("2015-08-15", "Rust", "1000", "1000")
    )
    val df = spark
      .createDataFrame(data)
      .toDF("date", "language", "users_count", "users_count")
      .withColumn("date", to_date($"date", "yyyy-MM-dd"))

    implicit def myJdbcToJdbc(jdbcUtils: JdbcUtils.type) = MyJdbcUtils

    val jdbcOptions = new JdbcOptionsInWrite(conn.options + ("dbtable" -> save_to))
    JdbcUtils.upsertTable(
      df,
      df0.schema,
      false,
      jdbcOptions,
      Seq("date", "language")
    )
  }

}