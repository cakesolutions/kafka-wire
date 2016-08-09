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

package net.cakesolutions.kafkawire

import akka.actor.ActorSystem
import cakesolutions.kafka.testkit.KafkaServer
import com.typesafe.config.ConfigFactory
import net.cakesolutions.kafkawire.client.KafkaWireClient
import net.cakesolutions.kafkawire.server.{KafkaServiceActor, KafkaWireRouter}
import net.cakesolutions.kafkawire.tests.ProtobufServiceGrpc.ProtobufService
import net.cakesolutions.kafkawire.tests.{ProtobufCommand, ProtobufEvent}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.concurrent.{ExecutionContext, Future}

class ClientServerSpec extends FlatSpecLike with Matchers with BeforeAndAfterAll with ScalaFutures {

  val kafkaServer: KafkaServer = new KafkaServer

  implicit val defaultPatience = PatienceConfig(timeout = Span(15, Seconds), interval = Span(500, Millis))

  val config = ConfigFactory.parseString {
    s"""
      |  bootstrap.servers = "localhost:${kafkaServer.kafkaPort}"
      |  group.id = "group"
      |  enable.auto.commit = false
      |  auto.offset.reset = "earliest"
      |
      |  schedule.interval = 3000 milliseconds
      |  unconfirmed.timeout = 3000 milliseconds
      |  buffer.size = 8
    """.stripMargin
  }

  val serverSystem = ActorSystem("serverSystem", config)
  val clientSystem = ActorSystem("clientSystem", config)

  def setup()(implicit ec: ExecutionContext): KafkaWireClient = {
    //SERVER SETUP
    val implementation: ProtobufService = new ProtobufService {
      override def kafkaCall(request: ProtobufCommand): Future[ProtobufEvent] = Future.successful {
        ProtobufEvent(s"${request.something} : greetings to you")
      }
    }
    val router: KafkaWireRouter = new KafkaWireRouter {
      override def router: Router = this.route[ProtobufService](implementation)
    }
    serverSystem.actorOf(KafkaServiceActor.props(config, router))

    //CLIENT SETUP
    new KafkaWireClient(clientSystem, config)
  }

  override protected def beforeAll() = {
    super.beforeAll()
    kafkaServer.startup()
  }

  override protected def afterAll() = {
    clientSystem.terminate()
    serverSystem.terminate()
    kafkaServer.close()
    super.afterAll()
  }

  "Kafka wire" should "allow typesafe kafka communication with minimum boilerplate" in {
    import autowire._

    import scala.concurrent.ExecutionContext.Implicits.global

    val kafkaWireClient = setup()
    val result: Future[ProtobufEvent] = kafkaWireClient[ProtobufService].kafkaCall(ProtobufCommand("hello")).call()

    whenReady(result) { event =>
      event.something.isEmpty shouldBe false
    }

    kafkaWireClient.kafkaProducer.close()

  }

}
