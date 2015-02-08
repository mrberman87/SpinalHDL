/*
 * SpinalHDL
 * Copyright (c) Dolu, All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */

package spinal

import scala.collection.mutable.ArrayBuffer

/**
 * Created by PIC18F on 21.08.2014.
 */


object BaseType {

  def assignFrom(baseType: BaseType, initialConsumer: Node,initialConsumerInputId : Int, that: Node): Unit = {
    //var consumer: Node = if (baseType.isReg) baseType.inputs(0) else baseType
    var consumer = initialConsumer
    var consumerInputId: Int = initialConsumerInputId
    var whenHit = baseType.whenScope == null

    for (when <- when.stack.stack.reverseIterator) {

      if (!whenHit) {
        if (when == baseType.whenScope) whenHit = true
      } else {
        val consumerInput = consumer.inputs(consumerInputId)
        if (consumerInput == null || !when.autoGeneratedMuxs.contains(consumerInput)) {
          // val mux = Multiplex(when.cond, consumer.inputs(consumerInputId), consumer.inputs(consumerInputId), true)
          val mux = baseType.newMultiplexor(when.cond, consumer.inputs(consumerInputId), consumer.inputs(consumerInputId))
          mux.whenMux = true;
          consumer.inputs(consumerInputId) = mux
          when.autoGeneratedMuxs += mux
        }
        consumer = consumer.inputs(consumerInputId)
        consumerInputId = if (when.isTrue) 1 else 2
      }

    }

    if (!whenHit)
      throw new Exception("Basetype is affected outside his when scope")

    consumer.inputs(consumerInputId) = that

  }
}


abstract class BaseType extends Node with Data with Nameable {
  inputs += null



  var compositeAssign: Assignable = null

  final override def assignFrom(that: Data): Unit = {
    if (compositeAssign != null) {
      compositeAssign.assignFrom(that)
    } else {
      assignFromImpl(that)
    }
  }


  override def getBitsWidth: Int = getWidth

  def isReg = inputs(0).isInstanceOf[Reg]

  //  override def :=(bits: this.type): Unit = assignFrom(bits)


  def assignFromImpl(that: Data): Unit = {
    that match {
      case that: BaseType => {
        BaseType.assignFrom(this, this,0, that)
      }
      case _ => throw new Exception("Undefined assignement")
    }
  }

  //TODO betther autoconect with component io set as io
  override def autoConnect(that: Data): Unit = {
    if (this.component == that.component) {
      if(this.component == Component.current) {
        sameFromInside
      }else if(this.component.parent == Component.current) {
        sameFromOutside
      }else SpinalError("You cant autoconnect from here")
    } else if (this.component.parent == that.component.parent) {
      kindAndKind
    } else if (this.component == that.component.parent) {
      parentAndKind(this,that)
    } else if (this.component.parent == that.component) {
      parentAndKind(that,this)
    } else SpinalError("Don't know how autoconnect")

    def sameFromOutside: Unit = {
      if (this.isOutput && that.isInput) {
        that := this
      } else if (this.isInput && that.isOutput) {
        this := that
      } else SpinalError("Bad input output specification for autoconnect")
    }
    def sameFromInside: Unit = {
      if (that.isOutputDir && this.isInputDir) {
        that := this
      } else if (that.isInputDir && this.isOutputDir) {
        this := that
      } else SpinalError("Bad input output specification for autoconnect")
    }

    def kindAndKind: Unit = {
      if (this.isOutput && that.isInput) {
        that := this
      } else if (this.isInput && that.isOutput) {
        this := that
      } else SpinalError("Bad input output specification for autoconnect")
    }

    def parentAndKind(p: Data, k: Data): Unit = {
      if(k.isOutput){
        p := k
      }else if(k.isInput){
        k := p
      }else SpinalError("Bad input output specification for autoconnect")
    }
  }
  // def castThatInSame(that: BaseType): this.type = throw new Exception("Not defined")


  override def flatten: ArrayBuffer[(String, BaseType)] = ArrayBuffer((getName(), this));


  override def clone: this.type = {
    val res = this.getClass.newInstance.asInstanceOf[this.type];
    res.dir = this.dir
    res
  }


  def newMultiplexor(sel: Bool, whenTrue: Node, whenFalse: Node): Multiplexer


  def newLogicalOperator(opName: String, right: Node, normalizeInputsImpl: (Node) => Unit): Bool = {
    val op = BinaryOperator(opName, this, right, WidthInfer.oneWidth, normalizeInputsImpl)
    val typeNode = new Bool()
    typeNode.inputs(0) = op
    typeNode
  }

  def newBinaryOperator(opName: String, right: Node, getWidthImpl: (Node) => Int, normalizeInputsImpl: (Node) => Unit): this.type = {
    val op = BinaryOperator(opName, this, right, getWidthImpl, normalizeInputsImpl)
    val typeNode = addTypeNodeFrom(op)
    typeNode
  }

  def newUnaryOperator(opName: String, getWidthImpl: (Node) => Int = WidthInfer.inputMaxWidthl): this.type = {
    val op = UnaryOperator(opName, this, getWidthImpl, InputNormalize.none)
    val typeNode = addTypeNodeFrom(op)
    typeNode
  }

  def castFrom(opName: String, that: Node, getWidthImpl: (Node) => Int = WidthInfer.inputMaxWidthl): this.type = {
    val op = Cast(opName, that, getWidthImpl)
    this.setInput(op)
    this
  }
  def enumCastFrom(opName: String, that: Node, getWidthImpl: (Node) => Int = WidthInfer.inputMaxWidthl): this.type = {
    val op = EnumCast(this.asInstanceOf[SpinalEnumCraft[_]], opName, that, getWidthImpl)
    this.setInput(op)
    this
  }
  def newFunction(opName: String, args: List[Node], getWidthImpl: (Node) => Int = WidthInfer.inputMaxWidthl): this.type = {
    val op = Function(opName, args, getWidthImpl)
    val typeNode = addTypeNodeFrom(op)
    typeNode
  }

  def newResize(opName: String, args: List[Node], getWidthImpl: (Node) => Int = WidthInfer.inputMaxWidthl): this.type = {
    val op = Resize(opName, args, getWidthImpl)
    val typeNode = addTypeNodeFrom(op)
    typeNode
  }

  def addTypeNodeFrom(node: Node): this.type = {
    val typeNode = this.clone()
    typeNode.setInput(node)
    typeNode
  }


  override def toString(): String = s"${getClassIdentifier}(named ${"\"" + getName() + "\""},into ${if (component == null) "null" else component.getClass.getSimpleName}})"
}

