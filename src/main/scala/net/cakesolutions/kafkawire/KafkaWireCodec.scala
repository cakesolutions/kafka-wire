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

import java.util

import com.trueaccord.scalapb.{GeneratedMessage, GeneratedMessageCompanion, Message}
import net.cakesolutions.kafkawire.protocol.ServiceMessage
import org.apache.kafka.common.serialization.{Deserializer, Serializer}

trait Packer[A] {
  def pack(a: A): ServiceMessage
}

object Packer {
  implicit def packer[A <: GeneratedMessage with Message[A]](implicit cmp: GeneratedMessageCompanion[A]): Packer[A] =
    new Packer[A] {
      override def pack(a: A): ServiceMessage = ServiceMessage(
          typeURL = a.companion.descriptor.getFullName
      )
    }
}

trait Unpacker[A] {
  def unpack(serviceMessage: ServiceMessage): A
}

object Unpacker {
  implicit def unpacker[A <: GeneratedMessage with Message[A]](
      implicit cmp: GeneratedMessageCompanion[A]): Unpacker[A] = new Unpacker[A] {
    override def unpack(serviceMessage: ServiceMessage): A = cmp.parseFrom(serviceMessage.payload.toByteArray)
  }
}

class ServiceMessageKafkaDeserializer extends Deserializer[ServiceMessage] {
  override def deserialize(topic: String, data: Array[Byte]) = ServiceMessage.parseFrom(data)

  override def close = {}

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}
}

class ServiceMessageKafkaSerializer extends Serializer[ServiceMessage] {
  override def serialize(topic: String, data: ServiceMessage): Array[Byte] = data.toByteArray

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  override def close(): Unit = {}
}
