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
import jotting.MyJdbcUtils
import org.apache.spark.sql.types.DateType
import org.apache.spark.sql.types.IntegerType
import org.apache.spark.sql.jdbc.JdbcDialects

/** Test cases for verifying MyJdbcUtils' correctness
  */
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

    args.toList match {
      case "write" :: _          => runJdbcDatasetWrite(conn)
      case "addConstraint" :: _  => runJdbcAddConstraint(conn)
      case "dropConstraint" :: _ => runJdbcDropConstraint(conn)
      case "upsert" :: _         => runJdbcDatasetUpsert(conn)
      case "delete" :: _         => runJdbcDatasetDelete(conn)
      case _                     => throw new Exception("Argument is required")
    }

    spark.stop()
  }

  val save_to = "job3"

  private def runJdbcDatasetWrite(conn: Conn)(implicit spark: SparkSession): Unit = {
    import spark.implicits._

    val data = Seq(
      ("2010-01-23", "Java", 20000),
      ("2010-01-23", "Python", 100000),
      ("2010-01-23", "Scala", 3000),
      ("2015-08-15", "Java", 25000),
      ("2015-08-15", "Python", 150000),
      ("2015-08-15", "Scala", 2000)
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

    new MyJdbcUtils(conn).addUniqueConstraint(
      save_to,
      s"${save_to}_date_language",
      Seq("date", "language")
    )
  }

  private def runJdbcAddConstraint(conn: Conn)(implicit spark: SparkSession): Unit = {
    new MyJdbcUtils(conn).addUniqueConstraint(
      save_to,
      s"${save_to}_date_language",
      Seq("date", "language")
    )
  }

  private def runJdbcDropConstraint(conn: Conn)(implicit spark: SparkSession): Unit = {
    new MyJdbcUtils(conn).dropUniqueConstraint(
      save_to,
      s"${save_to}_date_language"
    )
  }

  private def runJdbcAddIndex(conn: Conn)(implicit spark: SparkSession): Unit = {
    //

    // JdbcUtils.createIndex()
  }

  private def runJdbcDatasetUpsert(conn: Conn)(implicit spark: SparkSession): Unit = {
    import spark.implicits._

    val schema = new StructType()
      .add("date", DateType)
      .add("language", StringType)
      .add("users_count", IntegerType)

    val data = Seq(
      ("2010-01-23", "Java", 21000),
      ("2010-01-23", "Python", 100000),
      ("2010-01-23", "Scala", 15000),
      ("2015-08-15", "Java", 24000),
      ("2015-08-15", "Python", 135000),
      ("2015-08-15", "Scala", 2200),
      ("2015-08-15", "Rust", 1000)
    )
    val df = spark
      .createDataFrame(data)
      .toDF("date", "language", "users_count")
      .withColumn("date", to_date($"date", "yyyy-MM-dd"))

    new MyJdbcUtils(conn).upsertTable(
      df,
      save_to,
      None,
      false,
      Seq("date", "language"),
      "doUpdate"
    )
  }

  private def runJdbcDatasetDelete(conn: Conn)(implicit spark: SparkSession): Unit = {
    val del = "date = '2010-01-23' and language = 'Scala'"
    new MyJdbcUtils(conn).deleteByConditions(save_to, del)
  }

}
