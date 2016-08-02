package net.cakesolutions.kafkawire.client

import java.util.UUID

import akka.actor.ActorSystem
import cakesolutions.kafka.KafkaProducer.Conf
import cakesolutions.kafka.{KafkaProducer, KafkaProducerRecord}
import net.cakesolutions.kafkawire._
import com.typesafe.config.Config
import net.cakesolutions.kafkawire.protocol.ServiceMessage
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

class KafkaWireClient(sys: ActorSystem, kafkaConf: Config) extends autowire.Client[ServiceMessage, Unpacker, Packer] {
  lazy val responsePool = sys.actorOf(ResponsePool.props(kafkaConf, EVENTS_TOPIC))
  lazy val bootstrapServers = kafkaConf.getString("bootstrap.servers")
  lazy val kafkaProducer = KafkaProducer[String, ServiceMessage](
      Conf(new StringSerializer(), new ServiceMessageKafkaSerializer(), bootstrapServers = bootstrapServers))

  override def write[Result](r: Result)(implicit packer: Packer[Result]): ServiceMessage = {
    packer.pack(r)
  }

  override def read[Result](p: ServiceMessage)(implicit unpacker: Unpacker[Result]): Result = {
    unpacker.unpack(p)
  }

  override def doCall(req: Request): Future[ServiceMessage] = {
    val id = UUID.randomUUID()
    val promise = Promise[ServiceMessage]
    //Creating a waiter for the response
    sys.actorOf(ResponseWaiter.props(id.toString, promise, responsePool, 20.seconds))
    //Preparing message to send to kafka
    val methodPath = req.path.mkString(".")
    // Because of how services work in protobuf, the method has have one and only one parameter.
    val (methodArg, serviceMessage) = req.args.toSeq.head
    val newHeaders = serviceMessage.headers ++ Map(
          METHOD_PATH_KEY -> methodPath,
          CALL_ID_KEY -> id.toString,
          METHOD_PATH_KEY -> methodArg
      )

    implicit val ec : ExecutionContext = sys.dispatcher
    kafkaProducer.send(KafkaProducerRecord(COMMAND_TOPIC, serviceMessage.withHeaders(newHeaders))).onComplete {
      case Success(x) => println(x)
      case Failure(e) => e.printStackTrace()
    }
    promise.future
  }
}
