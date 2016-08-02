package net.cakesolutions.kafkawire.client

import java.util.concurrent.TimeoutException

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import net.cakesolutions.kafkawire.client.ResponsePool.{ForgetMe, GiveMe}
import net.cakesolutions.kafkawire.protocol.ServiceMessage

import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration

object ResponseWaiter {

  case class FailedComputation(throwable: Throwable)

  def props(id: String, promise: Promise[ServiceMessage], responsePool: ActorRef, timeout: FiniteDuration): Props =
    Props(new ResponseWaiter(id, promise, responsePool, timeout))
}

class ResponseWaiter(id: String, promise: Promise[ServiceMessage], responsePool: ActorRef, timeout: FiniteDuration)
    extends Actor
    with ActorLogging {

  import ResponseWaiter._
  import context.dispatcher

  context.system.scheduler.scheduleOnce(
      timeout,
      self,
      FailedComputation(new TimeoutException(s"Promise timed out after ${timeout.toString()}"))
  )

  responsePool ! GiveMe(id, self)

  def receive: Receive = {
    case envelopedMessage: ServiceMessage =>
      promise.success(envelopedMessage)
      context.stop(self)
    case FailedComputation(throwable) =>
      responsePool ! ForgetMe(id)
      promise.failure(throwable)
      context.stop(self)
  }

}
