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

package net.cakesolutions.kafkawire.macros

import com.google.protobuf.Descriptors.ServiceDescriptor

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait ProtobufServiceDescriptor[T] {
  def descriptor(): ServiceDescriptor
}

object ProtobufServiceDescriptor {
  def apply[T](implicit instance: ProtobufServiceDescriptor[T]): ServiceDescriptor = instance.descriptor()

  object Implicits {
    implicit def scalaPBServiceDescriptor[T]: ProtobufServiceDescriptor[T] = macro Macros.getReifyDescriptorImpl[T]

  }
}

case class Pipo(a: Int)

object Macros {
  def getReifyDescriptorImpl[T](c: blackbox.Context)(
      implicit wtt: c.WeakTypeTag[T]): c.Expr[ProtobufServiceDescriptor[T]] = {

    import c.universe._

    val tpe = c.weakTypeOf[T]

    val typeName = tpe.typeSymbol.fullName
    val containingObjectPath = typeName.split("\\.").dropRight(1).toList

    val select = containingObjectPath.foldLeft[Tree](Ident(termNames.ROOTPKG)) {
      case (tree, name) =>
        c.universe.Select(tree, TermName(name))
    }
    c.Expr[ProtobufServiceDescriptor[T]](
        q"""
        new _root_.net.cakesolutions.kafkawire.macros.ProtobufServiceDescriptor[$tpe]() {
          def descriptor(): _root_.com.google.protobuf.Descriptors.ServiceDescriptor = {
            $select.descriptor
          }
        }
       """
    )
  }
}
