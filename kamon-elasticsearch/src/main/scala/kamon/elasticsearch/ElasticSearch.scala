/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.elasticsearch

import akka.actor._
import akka.event.Logging
import kamon.Kamon
import kamon.util.ConfigTools.Syntax
import kamon.metric._

import scala.collection.JavaConverters._
import scala.concurrent.duration._

object ElasticSearch extends ExtensionId[ElasticSearchExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = ElasticSearch
  override def createExtension(system: ExtendedActorSystem): ElasticSearchExtension = new ElasticSearchExtension(system)
}

class ElasticSearchExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  implicit val as = system
  val log = Logging(system, classOf[ElasticSearchExtension])

  log.info("Starting the Kamon(ElasticSearch) extension")

  private val elasticSearchConfig = new ElasticSearchConfiguration(system.settings.config.getConfig("kamon.elasticsearch"))

  val subscriptions = elasticSearchConfig.subscriptions

  val datadogHost = elasticSearchConfig.hostname
  val datadogPort = elasticSearchConfig.port

  val flushInterval = elasticSearchConfig.flushInterval
  val tickInterval = Kamon.metrics.settings.tickInterval

  val datadogMetricsListener = buildMetricsListener(tickInterval, flushInterval)

  subscriptions.firstLevelKeys.foreach { subscriptionCategory ⇒
    subscriptions.getStringList(subscriptionCategory).asScala.foreach { pattern ⇒
      Kamon.metrics.subscribe(subscriptionCategory, pattern, datadogMetricsListener, permanently = true)
    }
  }

  def buildMetricsListener(tickInterval: FiniteDuration, flushInterval: FiniteDuration): ActorRef = {
    assert(flushInterval >= tickInterval, "ElasticSearch flush-interval needs to be equal or greater to the tick-interval")

    val metricsSender = system.actorOf(ElasticSearchMetricsSender.props(datadogHost, datadogPort), "elasticsearch-metrics-sender")

    if (flushInterval == tickInterval) {
      // No need to buffer the metrics, let's go straight to the metrics sender.
      metricsSender
    } else {
      system.actorOf(TickMetricSnapshotBuffer.props(flushInterval, metricsSender), "elasticsearch-metrics-buffer")
    }
  }
}
