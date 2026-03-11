package one.wabbit.data

import kotlin.concurrent.Volatile

/**
 * Represents a lazy computation that only evaluates its value when needed and caches the result for
 * future accesses.
 *
 * This class provides a way to define potentially expensive computations in a lazy manner and
 * allows them to be composed using functional operations like `map` and `flatMap`.
 *
 * IMPORTANT CONCURRENCY NOTE: There is no guarantee of single-evaluation under concurrency. If
 * multiple threads attempt to evaluate the same Need simultaneously, they may each perform
 * redundant work before ultimately converging on the same result. This design optimizes for
 * single-threaded or low-contention scenarios.
 *
 * @param A The type of the value encapsulated by this `Need`.
 */
class Need<out A> private constructor(@Volatile private var thunk: Any?) {
    /**
     * A lazily evaluated property that computes and caches its result. The value is initially
     * wrapped in a `Thunk`, which can represent either a completed computation (`Thunk.Done`) or a
     * deferred computation. When accessed, the property evaluates the deferred computation (if
     * necessary), caches the result, and returns it.
     * - If the `thunk` is already a result (`Thunk.Done`), the value is retrieved directly.
     * - If the `thunk` represents a deferred computation, it is evaluated using the `evaluate`
     *   function.
     * - The evaluated result is then stored in the `thunk` and returned.
     *
     * The type of the result is `A`, and type casting is used to ensure compatibility.
     */
    val value: A
        get() {
            val thunk = this.thunk
            if (thunk !is Thunk<*>) {
                @Suppress("UNCHECKED_CAST")
                return thunk as A
            }
            val result =
                when (thunk) {
                    is Thunk.Done<*> -> thunk.value
                    else -> evaluate(this)
                }
            this.thunk = result
            @Suppress("UNCHECKED_CAST")
            return result as A
        }

    /**
     * Transforms the value contained within this `Need` instance using the provided mapping
     * function.
     *
     * @param f a function that takes a value of type `A` and returns a value of type `B`.
     * @return a new `Need<B>` instance containing the transformed value.
     */
    fun <B> map(f: (A) -> B): Need<B> = Need(Thunk.Map(this, f))

    /**
     * Applies a function to the value contained in this `Need` instance and flattens the resulting
     * nested `Need` structure.
     *
     * @param f a function that takes a value of type `A` and returns a `Need` containing a value of
     *   type `B`.
     * @return a new `Need<B>` instance representing the flattened result of applying the function.
     */
    fun <B> flatMap(f: (A) -> Need<B>): Need<B> = Need(Thunk.FlatMap(this, f))

    infix fun <B> zipLeft(b: Need<B>): Need<B> = this.flatMap { b }

    infix fun <B> zipRight(b: Need<B>): Need<A> = this.flatMap { a -> b.map { a } }

    /**
     * Combines the value contained in this `Need` instance with the value in another `Need`
     * instance, producing a `Need` instance containing a pair of the combined values.
     *
     * @param b the other `Need` instance to combine with this `Need`.
     * @return a new `Need` instance containing a pair of values from both `Need` instances.
     */
    infix fun <B> zip(b: Need<B>): Need<Pair<A, B>> =
        this.flatMap<Pair<A, B>> { a -> b.map { a to it } }

    /**
     * Compares this `Need` instance with another object to determine equality.
     *
     * @param other the object to compare with this `Need` instance.
     * @return `true` if the specified object is equal to this `Need`, otherwise `false`.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Need<*>) return false
        return this.value == other.value
    }

    override fun hashCode(): Int = this.value.hashCode()

    override fun toString(): String = "Need($thunk)"

    /**
     * Represents a structure used to encode computation in stages, allowing for lazy evaluation and
     * composition of transformations.
     *
     * This sealed class provides three possible states:
     * - `Done`: Represents a completed computation with a concrete result.
     * - `Map`: Represents a computation where a function is applied to the result of a previous
     *   computation.
     * - `FlatMap`: Represents a computation where a function generates a new computation based on
     *   the result of a previous computation.
     *
     * These states enable chaining and evaluation of computations in a deferred manner.
     */
    private sealed class Thunk<out A> {
        data class Done<out A>(val value: A) : Thunk<A>()

        data class FlatMap<Z, out A>(val left: Need<Z>, val f: (Z) -> Need<A>) : Thunk<A>()

        data class Map<Z, out A>(val left: Need<Z>, val f: (Z) -> A) : Thunk<A>()
    }

    private enum class StackAction {
        MAP,
        FLATMAP,
    }

    sealed interface StackBase

    private data object StackNil : StackBase

    private class StackElement(
        val action: StackAction,
        val need: Need<Any?>,
        val f: (Any?) -> Any?,
        val tail: StackBase,
    ) : StackBase

    companion object {
        fun <A> now(a: A): Need<A> = Need(a)

        val unit: Need<Unit> = now(Unit)

        fun <A> apply(a: () -> A): Need<A> = unit.map { a() }

        /**
         * Defers the computation of a value, creating a `Need` instance that represents the lazily
         * evaluated computation.
         *
         * @param a A lambda that produces a `Need<A>` when invoked.
         * @return A new `Need<A>` instance representing the deferred computation described by the
         *   input lambda.
         */
        fun <A> defer(a: () -> Need<A>): Need<A> = Need(Thunk.FlatMap(unit) { a() })

        fun <A, B, Z> zip(a: Need<A>, b: Need<B>, f: (A, B) -> Z): Need<Z> =
            a.flatMap { aa -> b.map { bb -> f(aa, bb) } }

        fun <A, B, C, Z> zip(a: Need<A>, b: Need<B>, c: Need<C>, f: (A, B, C) -> Z): Need<Z> =
            a.flatMap { aa -> b.flatMap { bb -> c.map { cc -> f(aa, bb, cc) } } }

        fun <A, B, C, D, Z> zip(
            a: Need<A>,
            b: Need<B>,
            c: Need<C>,
            d: Need<D>,
            f: (A, B, C, D) -> Z,
        ): Need<Z> =
            a.flatMap { aa ->
                b.flatMap { bb -> c.flatMap { cc -> d.map { dd -> f(aa, bb, cc, dd) } } }
            }

        /**
         * Executes a recursive operation within the `Need` context, enabling definitions of lazy
         * recursive computations.
         *
         * Be cautious when defining your function 'f'. If 'f(result)' references 'result' itself in
         * a non-terminating way, you can create infinite loops upon evaluation. The function is
         * powerful but must be used carefully.
         *
         * @param f a function that takes a `Need<A>` and returns a transformed `Need<A>`,
         *   representing the recursive operation.
         * @return a `Need<A>` instance representing the result of the recursive computation.
         */
        fun <A> recursive(f: (Need<A>) -> Need<A>): Need<A> {
            // Avoid publishing a Need with a null thunk (data race hazard).
            val box = arrayOfNulls<Need<A>>(1)
            val result = Need<A>(Thunk.FlatMap(unit) { f(box[0]!!) })
            box[0] = result
            return result
        }

        /**
         * Builds a memoized function based on the given dependency resolver.
         *
         * @param f The dependency resolver function. This function takes two arguments:
         *     - a reference to the memoized function being constructed, allowing recursive
         *       invocations.
         *     - a key of type `K` for which the value is to be resolved. The resolver returns a
         *       `Need<V?>`, representing a potentially deferred computation of the value associated
         *       with the given key.
         *
         * @return A function that takes a key of type `K` and returns a `Need<V?>`, providing
         *   either a cached value or a computed result based on the resolver function.
         */
        fun <K, V : Any> build(f: ((K) -> Need<V?>, K) -> Need<V?>): (K) -> Need<V?> {
            val map = mutableMapOf<K, V>()
            val result =
                object : (K) -> Need<V?> {
                    override fun invoke(key: K): Need<V?> {
                        val value = map[key]
                        return if (value != null) {
                            now(value)
                        } else {
                            f(this, key).map {
                                if (it != null) map[key] = it
                                it
                            }
                        }
                    }
                }
            return result
        }

        fun <K, V : Any> buildNonNull(f: ((K) -> Need<V>, K) -> Need<V>): (K) -> Need<V> {
            val cache = mutableMapOf<K, V>()
            val self =
                object : (K) -> Need<V> {
                    override fun invoke(key: K): Need<V> {
                        val hit = cache[key]
                        return if (hit != null) {
                            Need.now(hit)
                        } else {
                            f(this, key).map { v ->
                                cache.put(key, v)
                                v
                            }
                        }
                    }
                }
            return self
        }

        /**
         * Low-level cast function that bypasses certain runtime checks.
         *
         * Normally, a direct cast in Kotlin (`value as B`) involves intrinsics that can be
         * expensive in tight loops, due to extra type/variance/null/arity checks. This hack is a
         * small optimization that may help performance-critical code where casts are guaranteed
         * safe and happen frequently.
         */
        private fun <A, B> unsafeCast(a: A): B {
            @Suppress("UNCHECKED_CAST")
            return a as B
        }

        @Suppress("UNCHECKED_CAST")
        private fun <A> evaluate(root: Need<A>): A {
            var current: Need<Any?> = root
            var stack: StackBase = StackNil

            // We use a manual while-loop with a stack (trampoline) to
            // unroll nested Map/FlatMap computations. This prevents deep
            // recursion and potential stack overflows.
            //
            // 'current' holds the Need being evaluated at each stage,
            // and 'stack' accumulates transformations that need to be applied.
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
                        val f: (Any?) -> Need<Any?> = unsafeCast(thunk.f)
                        stack = StackElement(StackAction.FLATMAP, current, f, stack)
                        current = l
                    }
                    is Thunk.Map<*, *> -> {
                        val l = thunk.left
                        val f: (Any?) -> Any? = unsafeCast(thunk.f)
                        stack = StackElement(StackAction.MAP, current, f, stack)
                        current = l
                    }
                }
            }
        }
    }
}
