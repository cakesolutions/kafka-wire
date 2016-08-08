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

package net.cakesolutions.kafkawire.server

import akka.actor.{Actor, ActorLogging, Props}
import cakesolutions.kafka.{KafkaProducer, KafkaProducerRecord}
import cakesolutions.kafka.KafkaProducer.Conf
import cakesolutions.kafka.akka.KafkaConsumerActor.Subscribe.AutoPartition
import cakesolutions.kafka.akka.KafkaConsumerActor.{Confirm, Unsubscribe}
import cakesolutions.kafka.akka._
import net.cakesolutions.kafkawire._
import com.typesafe.config.Config
import net.cakesolutions.kafkawire.protocol.ServiceMessage
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}

object KafkaServiceActor {
  def props(conf: Config, router: KafkaWireRouter, topic: String): Props =
    Props(new KafkaServiceActor(conf, router, topic))
}

class KafkaServiceActor(kafkaConf: Config, router: KafkaWireRouter, topic: String) extends Actor with ActorLogging {

  import context.dispatcher // implicit execution context
  lazy val kafkaProducer =
    KafkaProducer[String, ServiceMessage](Conf(kafkaConf, new StringSerializer(), new ServiceMessageKafkaSerializer()))

  val consumer = context.system.actorOf(
      KafkaConsumerActor.props(kafkaConf, new StringDeserializer, new ServiceMessageKafkaDeserializer, self)
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
    kafkaProducer.flush()
    kafkaProducer.close()
    super.postStop()
  }

  def receive = {
    case extractor(consumerRecords) =>
      consumerRecords.pairs.foreach(processRecord)
      // Records processed -> commit the offsets (ensures at least once delivery)
      sender ! Confirm(consumerRecords.offsets, commit = true)
  }

  def processRecord(record: (Option[String], ServiceMessage)) = {
    record match {
      case (_, message) =>
        for {
          callId <- message.headers.get(CALL_ID_KEY)
          methodPath <- message.headers.get(METHOD_PATH_KEY)
          methodArg <- message.headers.get(METHOD_ARG_KEY)
        } {
          router.router(
              autowire.Core.Request(methodPath.split("\\.").toSeq,
                                    Map(methodArg -> message.withHeaders(
                                            message.headers - CALL_ID_KEY - METHOD_PATH_KEY - METHOD_ARG_KEY))))
        }.foreach { event =>
          val newHeaders = event.headers + (CALL_ID_KEY -> callId)
          kafkaProducer.send(KafkaProducerRecord(EVENTS_TOPIC, event.withHeaders(newHeaders)))
        }

      case _ => // Do nothing
    }
  }

}
