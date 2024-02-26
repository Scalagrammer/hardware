package scg.hardware

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.future.future

import kotlin.reflect.KProperty

fun <T, R> T.applyOn(receiver : R, scope : R.(T) -> Unit) : R = receiver.apply { scope(this@applyOn) }

fun <A, R> CoroutineScope.traverse(items : List<A>, scope : suspend CoroutineScope.(A) -> Deferred<R>) : List<R> =
    future { items.map { scope(it) }.awaitAll() }.join()

operator fun <T> ThreadLocal<T>.getValue(receiver : Any?, p : KProperty<*>) : T = get()
operator fun <T> ThreadLocal<T>.setValue(receiver : Any?, p : KProperty<*>, value : T) = set(value)