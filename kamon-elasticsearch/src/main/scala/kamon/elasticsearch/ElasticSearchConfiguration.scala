package kamon.elasticsearch

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

class ElasticSearchConfiguration(esConfig: Config) {
  val hostname = esConfig.getString("hostname")
  val port = esConfig.getInt("port")

  val flushInterval = esConfig.getDuration("flush-interval", TimeUnit.MILLISECONDS)


  object IndexOptions {
    private val config = esConfig.getConfig("index-options")

    val shards = config.getInt("shards")
  }

}
