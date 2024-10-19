package one.wabbit.data

class Need<out A>(@Volatile private var thunk: Any?) : Cloneable {
    val value: A
        get() {
            val thunk = this.thunk
            if (thunk !is Thunk<*>)
                return thunk as A
            val result = when (thunk) {
                is Thunk.Done<*> -> thunk.value
                else -> evaluate(this)
            }
            this.thunk = result
            return result as A
        }

    fun <B> map(f: (A) -> B): Need<B> = Need(Thunk.Map(this, f))
    fun <B> flatMap(f: (A) -> Need<B>): Need<B> = Need(Thunk.FlatMap(this, f))

    infix fun <B> zipLeft(b: Need<B>): Need<B> = this.flatMap { b }
    infix fun <B> zipRight(b: Need<B>): Need<A> = this.flatMap { a -> b.map { a } }
    infix fun <B> zip(b: Need<B>): Need<Pair<A, B>> = this.flatMap<Pair<A, B>> { a -> b.map { a to it } }

    override fun clone(): Need<A> = this
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Need<*>) return false
        return this.value == other.value
    }
    override fun hashCode(): Int = this.value.hashCode()
    override fun toString(): String = "Need($thunk)"

    private sealed class Thunk<out A> {
        data class Done<out A>(val value: A) : Thunk<A>()
        data class FlatMap<Z, out A>(val left: Need<Z>, val f: (Z) -> Need<A>) : Thunk<A>()
        data class Map<Z, out A>(val left: Need<Z>, val f: (Z) -> A) : Thunk<A>()
    }

    private enum class StackAction { MAP, FLATMAP }
    sealed interface StackBase
    private data object StackNil : StackBase
    private class StackElement(val action: StackAction, val need: Need<Any?>, val f: (Any?) -> Any?, val tail: StackBase) : StackBase

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun <A> evaluate(root: Need<A>): A {
            var current: Need<Any?> = root
            var stack: StackBase = StackNil

            while (true) {
                val thunk = current.thunk
                if (thunk !is Thunk<*>) {
                    val a = thunk as A
                    when (stack) {
                        is StackNil -> return a
                        is StackElement -> {
                            val action = stack.action
                            val need = stack.need
                            val f = stack.f
                            current = need
                            if (action == StackAction.MAP) {
                                current.thunk = f(a)
                            } else {
                                current.thunk = (f(a) as Need<Any?>).thunk
                            }
                            stack = stack.tail
                            continue
                        }
                    }
                }

                when (thunk) {
                    is Thunk.Done<*> -> {
                        val a = thunk.value
                        when (stack) {
                            is StackNil -> return a as A
                            is StackElement -> {
                                val action = stack.action
                                val need = stack.need
                                val f = stack.f
                                current = need
                                if (action == StackAction.MAP) {
                                    current.thunk = f(a)
                                } else {
                                    current.thunk = (f(a) as Need<Any?>).thunk
                                }
                                stack = stack.tail
                                continue
                            }
                        }
                    }
                    is Thunk.FlatMap<*, *> -> {
                        val l = thunk.left
                        val f = thunk.f as (Any?) -> Need<Any?>
                        stack = StackElement(StackAction.FLATMAP, current, f, stack)
                        current = l
                    }
                    is Thunk.Map<*, *> -> {
                        val l = thunk.left
                        val f = thunk.f as (Any?) -> Any?
                        stack = StackElement(StackAction.MAP, current, f, stack)
                        current = l
                    }
                }
            }
        }

        fun <A> apply(a: () -> A): Need<A> =
            unit.map { a() }

        fun <A> defer(a: () -> Need<A>): Need<A> =
            Need(Thunk.FlatMap(unit) { a() })

        fun <A> now(a: A): Need<A> =
            Need(a)

        val unit: Need<Unit> = now(Unit)

        fun <K, V : Any> build(f: ((K) -> Need<V?>, K) -> Need<V?>): (K) -> Need<V?> {
            val map = mutableMapOf<K, V>()
            val result = object : (K) -> Need<V?> {
                override fun invoke(key: K): Need<V?> {
                    val value = map[key]
                    return if (value != null) now(value)
                    else f(this, key).map {
                        if (it != null) map[key] = it
                        it
                    }
                }
            }
            return result
        }
    }
}
