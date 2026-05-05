# User Guide

## Lazy Values

Use `Need.apply` to delay a computation until a caller asks for `value`:

```kotlin
import one.wabbit.data.Need

var runs = 0
val config = Need.apply {
    runs += 1
    "loaded"
}

check(runs == 0)
check(config.value == "loaded")
check(config.value == "loaded")
check(runs == 1)
```

Use `Need.now` when a value is already available but should participate in APIs that expect `Need`.

## Mapping and Flat Mapping

`map` transforms the eventual value. `flatMap` lets the next step choose another lazy computation:

```kotlin
import one.wabbit.data.Need

val base = Need.now(20)
val result = base
    .map { it + 1 }
    .flatMap { value -> Need.apply { value * 2 } }

check(result.value == 42)
```

The implementation evaluates deep chains using a manual stack, so large chains of `map` or
`flatMap` can be forced without recursive stack overflow.

## Combining Values

Use `zip` to combine lazy values:

```kotlin
import one.wabbit.data.Need

val user = Need.now("ada")
val id = Need.apply { 7 }

val label = Need.zip(user, id) { name, number -> "$name-$number" }

check(label.value == "ada-7")
```

The instance `zip` returns a `Pair`, while companion `zip` overloads accept a combining function.

## Recursive Resolvers

`Need.build` and `Need.buildNonNull` construct memoized recursive resolver functions:

```kotlin
import one.wabbit.data.Need

val fib = Need.buildNonNull<Int, Int> { self, n ->
    when (n) {
        0, 1 -> Need.now(1)
        else -> Need.zip(self(n - 1), self(n - 2)) { a, b -> a + b }
    }
}

check(fib(6).value == 13)
```

Use `build` when the resolver can return `null`. Null results are returned but not cached, because
the nullable cache uses null as its missing-entry marker. Use `buildNonNull` when every key resolves
to a non-null value and should be cached.

## Delay

`Delay` lets higher-level algorithms decide whether values are strict or lazy:

```kotlin
import one.wabbit.data.Delay
import one.wabbit.data.Need

val strict = Delay.strict<Int>()
check(strict.wrap(Need.now(10)) == 10)

val delayed = Delay.need<Int>()
val nested: Need<Need<Int>> = Need.now(Need.apply { 20 })
check(delayed.wrap(nested).value == 20)
```

`Delay.force` unwraps the representation, forces one `Need` layer, and wraps the result again.

## Concurrency

`Need` is not an exactly-once primitive. If several threads force the same unevaluated value
concurrently, the underlying function may run more than once. Keep delayed computations pure or
idempotent when concurrent forcing is possible.
