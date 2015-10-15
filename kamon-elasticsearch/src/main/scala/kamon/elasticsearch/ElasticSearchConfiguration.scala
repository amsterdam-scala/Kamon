package kamon.elasticsearch

import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration

class ElasticSearchConfiguration(esConfig: Config) {
  val hostname = esConfig.getString("hostname")
  val port = esConfig.getInt("port")

  val flushInterval = new FiniteDuration(esConfig.getDuration("flush-interval", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)

  val subscriptions = esConfig.getConfig("subscriptions")

  object IndexOptions {
    private val config = esConfig.getConfig("index-options")

    val shards = config.getInt("shards")
  }

}
