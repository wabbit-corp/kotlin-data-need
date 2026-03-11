package one.wabbit.data

import java.util.SplittableRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NeedSpec {
    enum class Type {
        Bool,
        Int,
    }

    data class Value<T>(val need: Need<T>, val actual: T) {
        fun <U> map(f: (T) -> U): Value<U> = Value(need.map(f), f(actual))

        fun <U> flatMap(f: (T) -> Value<U>): Value<U> =
            Value(need.flatMap { f(it).need }, f(actual).actual)
    }

    class State {
        val random = SplittableRandom()
        val thunks = mutableMapOf<Type, MutableList<Value<*>>>()

        fun nextSimpleBool(): Value<Boolean> {
            val old = thunks[Type.Bool] ?: emptyList()
            val index = random.nextInt(old.size + 1)

            if (index < old.size) {
                @Suppress("UNCHECKED_CAST")
                return old[index] as Value<Boolean>
            }

            val r = random.nextBoolean()
            val result: Value<Boolean>
            if (random.nextBoolean()) {
                result = Value(Need.now(r), r)
            } else {
                result = Value(Need.apply { r }, r)
            }
            thunks.getOrPut(Type.Bool) { mutableListOf() }.add(result)
            return result
        }

        fun nextSimpleInt(): Value<Int> {
            val old = thunks[Type.Int] ?: emptyList()
            val index = random.nextInt(old.size + 1)

            if (index < old.size) {
                @Suppress("UNCHECKED_CAST")
                return old[index] as Value<Int>
            }

            val r = random.nextInt(10)
            val result: Value<Int>
            if (random.nextBoolean()) {
                result = Value(Need.now(r), r)
            } else {
                result = Value(Need.apply { r }, r)
            }
            thunks.getOrPut(Type.Int) { mutableListOf() }.add(result)
            return result
        }
    }

    fun genNeed(type: Type, depth: Int, state: State): Value<*> {
        when (type) {
            Type.Bool -> {
                when (state.random.nextInt(if (depth >= 1) 4 else 1)) {
                    0 -> return state.nextSimpleBool()
                    1 -> {
                        val left = state.nextSimpleBool()
                        return left.map { !it }
                    }
                    2 -> {
                        val left = state.nextSimpleBool()
                        val right = state.nextSimpleBool()
                        return left.flatMap { l -> right.map { r -> l && r } }
                    }
                    3 -> {
                        val left = state.nextSimpleInt()
                        return left.map { it % 2 == 0 }
                    }
                    else -> error("unreachable")
                }
            }
            Type.Int -> {
                when (state.random.nextInt(if (depth >= 1) 4 else 1)) {
                    0 -> return state.nextSimpleInt()
                    1 -> {
                        val left = state.nextSimpleInt()
                        return left.map { it + 1 }
                    }
                    2 -> {
                        val left = state.nextSimpleInt()
                        val right = state.nextSimpleInt()
                        return left.flatMap { l -> right.map { r -> l + r } }
                    }
                    3 -> {
                        val left = state.nextSimpleBool()
                        return left.map { if (it) 1 else 0 }
                    }
                    else -> error("unreachable")
                }
            }
        }
    }

    @Test
    fun test() {
        for (it in 0..1000000) {
            val state = State()
            val needWithActual = genNeed(Type.Bool, 100, state)
            val value = needWithActual.need.value
            check(value == needWithActual.need.value)
            check(value == needWithActual.actual)
        }
    }

    @Test
    fun deep_map_does_not_blow_stack() {
        val N = 200_000
        val n = (0 until N).fold(Need.now(0)) { acc, _ -> acc.map { it + 1 } }
        assertEquals(N, n.value)
    }

    @Test
    fun deep_flatMap_does_not_blow_stack() {
        val N = 100_000
        val n = (0 until N).fold(Need.now(0)) { acc, _ -> acc.flatMap { Need.now(it + 1) } }
        assertEquals(N, n.value)
    }

    @Test
    fun recursive_does_not_race_or_null() {
        // Fibonacci with memoization through Need.build, to exercise recursive + build.
        val fib =
            Need.build<Int, Int> { self, k ->
                when (k) {
                    0,
                    1 -> Need.now(1)
                    else -> Need.zip(self(k - 1), self(k - 2)) { a, b -> a!! + b!! }
                }
            }
        listOf(0 to 1, 1 to 1, 2 to 2, 3 to 3, 4 to 5, 5 to 8, 6 to 13).forEach { (k, v) ->
            assertEquals(v, fib(k).value)
        }
    }

    @Test
    fun concurrent_evaluations_converge() {
        val n =
            Need.apply {
                Thread.sleep(10)
                42
            }
        val pool = Executors.newFixedThreadPool(16)
        val results =
            (1..64).map { pool.submit<Int> { n.value } }.map { it.get(1, TimeUnit.SECONDS) }
        pool.shutdown()
        assertTrue(results.all { it == 42 })
    }

    @Test
    fun need_delay_force_forces_one_layer() {
        val D = Delay.need<Int>()
        val inner = Need.apply { 99 }
        val outer: Need<Need<Int>> = Need.now(inner)
        val flattened = D.wrap(outer)
        // Force one layer via Delay: unwrap+value gives Need.now(inner.value)
        val forcedOnce: Need<Int> = D.force(flattened)
        assertEquals(99, forcedOnce.value)
    }
}
