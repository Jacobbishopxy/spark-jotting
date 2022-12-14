package jotting.simple

import org.apache.spark.sql.SparkSession
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.Row
import org.apache.spark.sql.functions.to_date

import jotting.Jotting._

object Job2 {

  def main(args: Array[String]): Unit = {
    implicit val spark = SparkSession
      .builder()
      .appName("Spark SQL data sources example")
      // .config("spark.some.config.option", "some-value")
      .getOrCreate()

    val config = ConfigFactory.load(configFile).getConfig("job")
    val conn   = Conn(config)

    runJdbcDatasetWrite(conn)
    runJdbcDatasetRead(conn)

    spark.stop()
  }

  private def createDataFrame(spark: SparkSession): Unit = {
    import spark.implicits._

    val columns = Seq("language", "users_count")
    val data    = Seq(("Java", "20000"), ("Python", "100000"), ("Scala", "3000"))

    // 1. Create DF from RDD
    val rdd = spark.sparkContext.parallelize(data)

    // 1.1 `toDF()` function
    val dfFromRDD1 = rdd.toDF()
    dfFromRDD1.printSchema()

    val dfFromRDD1WithColumnName = rdd.toDF("language", "users_count")
    dfFromRDD1WithColumnName.printSchema()

    // 1.2 `createDataFrame()` from SparkSession
    val dfFromRDD2 = spark.createDataFrame(rdd).toDF(columns: _*)

    // 1.3 `createDataFrame()` with the Row type
    val schema = StructType(
      Array(
        StructField("language", StringType, true),
        StructField("users", StringType, true)
      )
    )
    val rowRDD     = rdd.map(attributes => Row(attributes._1, attributes._2))
    val dfFromRDD3 = spark.createDataFrame(rowRDD, schema)

    // 2. Create DF from List & Seq
    // 2.1 `toDF` make sure importing `import spark.implicits._`
    val dfFromData1 = data.toDF()

    // 2.2 `createDataFrame()` from SparkSession
    val dfFromData2 = spark.createDataFrame(data).toDF(columns: _*)

    // 2.3 `createDataFrame()` with the Row type
    import scala.collection.JavaConversions._
    val rowData     = Seq(Row("Java", "20000"), Row("Python", "100000"), Row("Scala", "3000"))
    val dfFromData3 = spark.createDataFrame(rowData, schema)
  }

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

    // mode:
    // overwrite
    // append
    // ignore
    // error
    df.write
      .format("jdbc")
      .options(conn.options)
      .option("dbtable", "dev")
      .mode("overwrite")
      .save()
  }

  private def runJdbcDatasetRead(conn: Conn)(implicit spark: SparkSession): Unit = {
    val df = spark.read
      .format("jdbc")
      .options(conn.options)
      .option("query", "SELECT * FROM dev")
      .load()

    df.printSchema()
    df.show()
  }
}
