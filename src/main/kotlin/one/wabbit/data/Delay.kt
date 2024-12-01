package one.wabbit.data

interface Delay<A> {
    fun wrap(a: Need<A>): A
    fun unwrap(a: A): Need<A>

    fun recursive(f: (A) -> A): A =
        wrap(Need.recursive<A> { it : Need<A> ->
            unwrap(f(wrap(it)))
        })

    fun force(a: A): A {
        val thunk = unwrap(a)
        return thunk.value
    }

    companion object {
        fun <A> strict(): Delay<A> = object : Delay<A> {
            override fun wrap(a: Need<A>): A = a.value
            override fun unwrap(a: A): Need<A> = Need.now(a)
        }

        fun <A> need(): Delay<Need<A>> = object : Delay<Need<A>> {
            override fun wrap(a: Need<Need<A>>): Need<A> = a.flatMap { it }
            override fun unwrap(a: Need<A>): Need<Need<A>> = a.map { Need.now(it) }
        }
    }
}
