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
