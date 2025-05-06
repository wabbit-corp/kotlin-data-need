package one.wabbit.data

import java.util.*
import kotlin.test.Test

class NeedSpec {
    enum class Type {
        Bool, Int
    }

    data class Value<T>(val need: Need<T>, val actual: T) {
        fun <U> map(f: (T) -> U): Value<U> {
            return Value(need.map(f), f(actual))
        }

        fun <U> flatMap(f: (T) -> Value<U>): Value<U> {
            return Value(need.flatMap { f(it).need }, f(actual).actual)
        }
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
            if (random.nextBoolean())
                result = Value(Need.now(r), r)
            else
                result = Value(Need.apply { r }, r)
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
            if (random.nextBoolean())
                result = Value(Need.now(r), r)
            else
                result = Value(Need.apply { r }, r)
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
                        return left.flatMap { l ->
                            right.map { r ->
                                l && r
                            }
                        }
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
                        return left.flatMap { l ->
                            right.map { r ->
                                l + r
                            }
                        }
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
        for (it in 0..100000000) {
            val state = State()
            val needWithActual = genNeed(Type.Bool, 100, state)
            val value = needWithActual.need.value
            check(value == needWithActual.need.value)
            check(value == needWithActual.actual)
        }
    }
}
