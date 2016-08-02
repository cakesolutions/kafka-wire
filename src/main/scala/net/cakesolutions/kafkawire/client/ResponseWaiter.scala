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
