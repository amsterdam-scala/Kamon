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


import java.util.UUID

import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric.instrument.{ Counter, Histogram }
import kamon.metric.{ SingleInstrumentEntityRecorder, MetricKey, Entity }
import akka.actor.{Props, Actor}
import spray.json._
import spray.http._

class ElasticSearchMetricsSender(esHost: String, esPort: Int) extends Actor {
  private val elasticClient = new SimpleElasticSearchClient(context.system, esHost, esPort)


  override def preStart(): Unit = {
   //create index
  }

  def receive = receiveMetrics

  private def receiveMetrics: Receive = {
    case tick: TickMetricSnapshot =>
      generateDocuments(tick)
  }

  private def generateDocuments(tick: TickMetricSnapshot) = {
    for {
      (groupIdentity, groupSnapshot) ← tick.metrics
      (metricIdentity, metricSnapshot) ← groupSnapshot.metrics
    } {

      val key = buildMetricName(groupIdentity, metricIdentity)

      metricSnapshot match {
        case hs: Histogram.Snapshot ⇒
//          hs.recordsIterator.foreach { record ⇒
//            val measurementData = formatMeasurement(groupIdentity, metricIdentity, encodeDatadogTimer(record.level, record.count))
//            packetBuilder.appendMeasurement(key, measurementData)
//
//          }

        case cs: Counter.Snapshot ⇒
          val doc = s"""
             |{
             |  "category": "${groupIdentity.category}",
             |  "name": "${groupIdentity.name}",
             |  "tags": ${groupIdentity.tags.toJson.toString},
             |  "metric": "${metricIdentity.name}",
             |  "from": ${tick.from.millis},
             |  "to": ${tick.to.millis},
             |  "value": ${cs.count}
             |}
           """.stripMargin
          elasticClient.doRest(Post(s"/stats/${UUID.randomUUID()}", doc))
      }
    }
  }
}

object ElasticSearchMetricsSender {
  def props(esHost: String, esPort: Int): Props = Props(new ElasticSearchMetricsSender(esHost, esPort))
}
