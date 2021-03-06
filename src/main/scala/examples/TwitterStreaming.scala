package examples

import java.util.Calendar

import scala.collection.mutable.Map
import com.couchbase.client.java.CouchbaseCluster
import com.couchbase.client.java.document.{JsonArrayDocument, JsonDocument}
import com.couchbase.client.java.document.json.{JsonArray, JsonObject}
import com.couchbase.spark.streaming._
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext._
import org.apache.spark.sql.catalyst.util.DateTimeUtils
import org.apache.spark.streaming._
import org.apache.spark.streaming.twitter._
import org.apache.spark.streaming.StreamingContext._

object TwitterStreaming {

  def main(args: Array[String]): Unit = {

    val window = 60
    //val filter = Seq("couchbase", "nosql", "kafka", "storm", "spark")
    //val filter = Seq("usa", "canada", "iran", "russia", "europe");

    val conf = new SparkConf()
      .setMaster("local[*]")
      .setAppName("twitterStreaming")
      .set("com.couchbase.nodes", "localhost")
      .set("com.couchbase.bucket.default", "")
      .set("com.couchbase.bucket.tweets", "")

    val ssc = new StreamingContext(conf, Seconds(window))

    val stream = TwitterUtils
      .createStream(ssc, None)                                        // create the stream
      .flatMap(status => status.getText.split(" ").filter(_.startsWith("#"))) // extract hashtags from tweets
      .map((_, 1))                                                            // add 1 to each tag to prepare it for reduce
      .reduceByKeyAndWindow((a: Int, b: Int) => a + b, Seconds(window), Seconds(window)) // reduce by a 1 second window and emit a new rdd in one second as well
      .map {case (topic, count) => (count, topic)}                            // flip the data for sorting
      .transform(_.sortByKey(false))                                          // sort descending
      .filter(_._1 >= 2)                                                      // filter out not so popular tags (for example set to 3)
      .foreachRDD{
      rdd=>
        val array =rdd.take(3)
        val id = "_tags::" + System.currentTimeMillis() / 1000
        val tags = JsonArray.create()
        val content = JsonObject.create()
        array.foreach(tuple => tags.add(JsonObject.create().put("tag", tuple._2).put("count", tuple._1)))
        content.put("tags", tags)
        content.put("date", Calendar.getInstance().getTime().toString)
        //println(id, content)
        JsonDocument.create(id, content)
        println(Calendar.getInstance().getTime().toString)
        array.foreach(tuple => println(tuple))
        println()
    }
    //.map(data => {                                                          // map from the list of tuples into a JsonArrayDocument. use a custom document id per second

    //})
    //.print()
    //.saveToCouchbase("default")                                             // store the document in couchbase


    /*val tweets = TwitterUtils
      .createStream(ssc, None, filter)
      .map(tweet => {
        val id = tweet.getId.toString
        val content = JsonObject.create()
        val location = tweet.getGeoLocation
        content.put("text", tweet.getText)
        content.put("date", tweet.getCreatedAt.getTime)
        content.put("user", tweet.getUser.getScreenName)
        content.put("userFollowersCount", tweet.getUser.getFollowersCount)
        content.put("userStatusesCount", tweet.getUser.getStatusesCount)

        if (location != null)
           content.put("location", JsonArray.create().add(location.getLongitude).add(location.getLatitude))
        println(id, content)
        JsonDocument.create(id, content)
      })*/
    //.print()
    //.saveToCouchbase("tweets")                                             // store the document in couchbase


    ssc.start()
    ssc.awaitTermination()
  }
}












//val cluter = CouchbaseCluster.create("localhost")
//val bucket = cluter.openBucket("default")


//    val users = TwitterUtils
//      .createStream(ssc, None, filter)                                        // create the tweet stream
//      .map(tweet => {                                                         // map tweets into a JsonDocument, keeping the username, text, date and location fields
//        val tags = tweet.getText.split(" ").filter(_.startsWith("#"))
//        val user = "_test"//tweet.getUser.getScreenName
//      (user, tags)
//      })
//      .map(tuple => {
//        val user = bucket.get(tuple._1)
//        val content = if(user == null) JsonObject.create() else user.content()
//        val tags = collection.mutable.Map[String, Int]().withDefaultValue(0)
//
//        if(content.get("tags") != null)
//          content.get("tags").asInstanceOf[Map[String,Int]].foreach(tag => tags(tag._1) += 1)
//        tuple._2.foreach(tag => tags(tag) += 1)
//
//        content.put("tags", tags)
//        JsonDocument.create("user::" + tuple._1, content)
//      })
//      .saveToCouchbase("default")