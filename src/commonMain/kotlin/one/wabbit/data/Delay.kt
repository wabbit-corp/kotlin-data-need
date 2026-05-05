package one.wabbit.data

/**
 * Represents an abstraction for a delayed computation by wrapping and unwrapping values into a
 * lazily evaluated structure.
 *
 * Provides methods for wrapping computations into a `Need`-like structure and extracting them for
 * evaluation. Additionally, supports recursive and forced evaluation of values using the underlying
 * structure.
 *
 * @param A The type of the value managed by the `Delay` interface.
 */
interface Delay<A> {
    /**
     * Wraps a lazy computation in this delay representation.
     *
     * Strict implementations may force [a] immediately, while lazy implementations can preserve the
     * deferred computation.
     *
     * @param a the computation to wrap.
     * @return the representation-specific value.
     */
    fun wrap(a: Need<A>): A

    /**
     * Converts a represented value back into a [Need].
     *
     * For strict values this usually returns [Need.now]. For lazy values this can return the
     * original delayed computation.
     *
     * @param a the represented value.
     * @return a lazy computation for [a].
     */
    fun unwrap(a: A): Need<A>

    /**
     * Builds a recursive value in this delay representation.
     *
     * The function [f] receives the representation-specific value being defined and returns the
     * value that should be wrapped as the recursive result. Non-terminating recursive definitions
     * remain non-terminating when forced.
     *
     * @param f the recursive definition.
     * @return the recursively defined value in this representation.
     */
    fun recursive(f: (A) -> A): A = wrap(Need.recursive<A> { it: Need<A> -> unwrap(f(wrap(it))) })

    /**
     * Forces one lazy layer in this representation.
     *
     * This unwraps [a], reads [Need.value], and wraps the resulting value again.
     *
     * @param a the value to force.
     * @return the representation-specific value after forcing one layer.
     */
    fun force(a: A): A {
        val thunk = unwrap(a)
        return thunk.value
    }

    /**
     * Constructors for the supported delay representations.
     */
    companion object {
        /**
         * Returns a delay representation that stores plain strict values.
         *
         * Wrapping a [Need] in this representation immediately forces it.
         *
         * @return a strict delay representation for values of type [A].
         */
        fun <A> strict(): Delay<A> =
            object : Delay<A> {
                override fun wrap(a: Need<A>): A = a.value

                override fun unwrap(a: A): Need<A> = Need.now(a)
            }

        /**
         * Returns a delay representation that stores values as [Need].
         *
         * Wrapping flattens a nested `Need<Need<A>>`, and unwrapping turns a `Need<A>` into a lazy
         * value that produces another `Need<A>`.
         *
         * @return a lazy delay representation backed by [Need].
         */
        fun <A> need(): Delay<Need<A>> =
            object : Delay<Need<A>> {
                override fun wrap(a: Need<Need<A>>): Need<A> = a.flatMap { it }

                override fun unwrap(a: Need<A>): Need<Need<A>> = a.map { Need.now(it) }
            }
    }
}
