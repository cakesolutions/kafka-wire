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
  lazy val kafkaProducer =
    KafkaProducer[String, ServiceMessage](Conf(kafkaConf, new StringSerializer(), new ServiceMessageKafkaSerializer()))

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

    implicit val ec: ExecutionContext = sys.dispatcher
    kafkaProducer.send(KafkaProducerRecord(COMMAND_TOPIC, serviceMessage.withHeaders(newHeaders))).onComplete {
      case Success(x) => println(x)
      case Failure(e) => e.printStackTrace()
    }
    promise.future
  }

  override def finalize(): Unit = {
    super.finalize()
    kafkaProducer.flush()
    kafkaProducer.close()
  }
}
