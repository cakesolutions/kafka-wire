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

import com.google.protobuf.Descriptors.ServiceDescriptor
import com.trueaccord.scalapb.Implicits._
import net.cakesolutions.kafkawire.macros.ProtobufServiceDescriptor
import net.cakesolutions.kafkawire.options.OptionsProto
import net.cakesolutions.kafkawire.tests.ProtobufServiceGrpc.ProtobufService
import org.scalatest.{FlatSpecLike, Matchers}

class DescriptorSpec extends FlatSpecLike with Matchers {

  "A protobuf service descriptor" should "allow to access the descriptor of a ScalaPB generated service" in {
    import ProtobufServiceDescriptor.Implicits._
    val descriptor: ServiceDescriptor = ProtobufServiceDescriptor[ProtobufService]

    val commandsTopic = descriptor.getOptions.extension(OptionsProto.commands).flatMap(_.topic)
    val eventsTopic = descriptor.getOptions.extension(OptionsProto.events).flatMap(_.topic)

    //These options were set directly in the proto file, and are accessible at runtime through the typeclass.
    commandsTopic shouldEqual Some("commands_test")
    eventsTopic shouldEqual Some("events_test")
  }

}
