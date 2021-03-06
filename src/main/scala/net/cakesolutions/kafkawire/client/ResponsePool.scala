/*
 * Copyright (c) 2016 Cake Solutions Limited
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.cakesolutions.kafkawire.client

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import cakesolutions.kafka.akka.KafkaConsumerActor.Subscribe.AutoPartition
import cakesolutions.kafka.akka.KafkaConsumerActor.{Confirm, Subscribe, Unsubscribe}
import cakesolutions.kafka.akka._
import com.typesafe.config.Config
import net.cakesolutions.kafkawire._
import net.cakesolutions.kafkawire.protocol.ServiceMessage
import org.apache.kafka.common.serialization.StringDeserializer

object ResponsePool {

  case class GiveMe(id: String, replyTo: ActorRef)
  case class Response(id: String)
  case class ForgetMe(id: String)

  def props(conf: Config, topic: String): Props = Props(new ResponsePool(conf, topic))
}

class ResponsePool(conf: Config, topic: String) extends Actor with ActorLogging {

  import ResponsePool._
  val MaxQueueSize = 1000000
  var expectants = Map.empty[String, ActorRef]

  val consumer = context.system.actorOf(
      KafkaConsumerActor.props(conf, new StringDeserializer, new ServiceMessageKafkaDeserializer, self)
  )

  val extractor: Extractor[Any, ConsumerRecords[String, ServiceMessage]] =
    ConsumerRecords.extractor[String, ServiceMessage]

  /**
    * Lifecycle hook to subscribe to kafka
    */
  override def preStart = {
    super.preStart()
    consumer ! AutoPartition(List(topic))
  }

  /**
    * Lifecycle hook to unsubscribe from kafka
    */
  override def postStop = {
    consumer ! Unsubscribe
    super.postStop()
  }

  def receive = {
    case GiveMe(id, replyTo) =>
      assert(expectants.size < MaxQueueSize, s"queued too many: ${expectants.size}")
      expectants += (id -> replyTo)

    case ForgetMe(id) =>
      expectants -= id

    case extractor(consumerRecords) =>
      consumerRecords.pairs.foreach(processRecord)
      // Records processed -> commit the offsets (ensures at least once delivery)
      sender ! Confirm(consumerRecords.offsets, commit = true)
  }

  def processRecord(record: (Option[String], ServiceMessage)) =
    record match {
      case (_, message) =>
        for {
          callId <- message.headers.get(CALL_ID_KEY)
          expecting <- expectants.get(callId)
        } {
          expectants.get(callId).foreach(_ ! message)
          expectants -= callId
        }
      case _ => // Do nothing
    }

}
