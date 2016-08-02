package net.cakesolutions.kafkawire.server

import net.cakesolutions.kafkawire.protocol.ServiceMessage
import net.cakesolutions.kafkawire.{Packer, Unpacker}

trait KafkaWireRouter extends autowire.Server[ServiceMessage, Unpacker, Packer] {
  override def read[Result](p: ServiceMessage)(implicit unpacker: Unpacker[Result]): Result = unpacker.unpack(p)

  override def write[Result](r: Result)(implicit packer: Packer[Result]): ServiceMessage = packer.pack(r)

  // This is created by calling a macro call, so we cannot provide an implementation before knowing the real API type.
  def router : autowire.Core.Router[ServiceMessage]
}
