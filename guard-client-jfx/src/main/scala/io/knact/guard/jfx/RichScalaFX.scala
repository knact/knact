package io.knact.guard.jfx

import javafx.scene.{Node, Parent}
import javafx.util.Duration

import org.fxmisc.easybind.EasyBind

import scalafx.Includes._
import scalafx.animation.{FadeTransition, Interpolator}
import scalafx.beans.value.ObservableValue
import scalafx.event.subscriptions.Subscription

object RichScalaFX {


	implicit class propertyToOption[T, A](value: T)(implicit ev: T => ObservableValue[A, A]) {

		def foreach(op: A => Unit): Subscription = {
			val that = ev(value).value
			if (that != null) op(that)
			value.onChange { (_, _, n) => op(n) }
		}


		def onChangeOption(op: Option[A] => Unit): Subscription = {
			value.onChange { (_, _, n) => op(Option(n)) }
		}
		def onChangeOptions(op: ((Option[A], Option[A])) => Unit): Subscription = {
			value.onChange { (_, p, n) => op((Option(p), Option(n))) }
		}


		def map[B, J](f: A => B): ObservableValue[B, J] = {
			(EasyBind.map[A, B](value.delegate, { v: A => f(v) }): ObservableValue[B, B])
				.asInstanceOf[ObservableValue[B, J]]
		}
	}


	def findOrMkUnsafe[T <: Node, P <: Parent](p: P, id: String)
											  (an: (P, T) => Unit)(mk: => T): T =
		Option(p.lookup(s"#$id")) match {
			case Some(v) => v.asInstanceOf[T]
			case None    =>
				val n = mk
				n.setId(id)
				an(p, n)
				n
		}

	import scalafx.scene.{Node => SNode}

	implicit class fadeInTransition[+N <: SNode](value: N) {
		def fadeIn(from: Double, to: Double, duration: Duration): Unit = {
			new FadeTransition(duration, value) {
				interpolator = Interpolator.EaseBoth
				fromValue = from
				toValue = to
			}.play()
		}
	}

}
