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
