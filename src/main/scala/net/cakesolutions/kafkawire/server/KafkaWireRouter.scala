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

import net.cakesolutions.kafkawire.protocol.ServiceMessage
import net.cakesolutions.kafkawire.{Packer, Unpacker}

trait KafkaWireRouter extends autowire.Server[ServiceMessage, Unpacker, Packer] {
  override def read[Result](p: ServiceMessage)(implicit unpacker: Unpacker[Result]): Result = unpacker.unpack(p)

  override def write[Result](r: Result)(implicit packer: Packer[Result]): ServiceMessage = packer.pack(r)

  // This is created by calling a macro call, so we cannot provide an implementation before knowing the real API type.
  def router: autowire.Core.Router[ServiceMessage]
}
