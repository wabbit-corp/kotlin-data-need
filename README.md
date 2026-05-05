# kotlin-data-need

`kotlin-data-need` is a Kotlin Multiplatform library for lazy computations that can be composed,
evaluated on demand, and cached after evaluation.

It is designed for code that needs explicit laziness without tying the public API to JVM-only
`Lazy`, coroutine primitives, or an application-specific memoization layer.

## Installation

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("one.wabbit:kotlin-data-need:1.2.0")
}
```

## Quick Start

```kotlin
import one.wabbit.data.Need

var runs = 0

val expensive = Need.apply {
    runs += 1
    "computed"
}

val rendered = expensive.map { value -> "value=$value" }

check(runs == 0)
check(rendered.value == "value=computed")
check(rendered.value == "value=computed")
check(runs == 1)
```

`Need.apply` delays a computation, `map` and `flatMap` compose additional work, and `value` forces
the computation. Once a `Need` has been forced, later reads return the cached result.

## Combining Computations

```kotlin
import one.wabbit.data.Need

val host = Need.now("localhost")
val port = Need.apply { 5432 }

val address = Need.zip(host, port) { h, p -> "$h:$p" }

check(address.value == "localhost:5432")
```

Use `zip` when multiple lazy values should be forced together and combined. Deep chains of `map`
and `flatMap` are evaluated with an internal trampoline, so large composed computations do not rely
on recursive stack growth.

## Recursive Memoization

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

`Need.build` and `Need.buildNonNull` create memoized resolver functions for recursive dependency
graphs. The nullable variant only stores non-null results; the non-null variant stores every result.

## Delay Abstraction

`Delay` lets generic code choose whether intermediate values are strict or wrapped in `Need`:

```kotlin
import one.wabbit.data.Delay
import one.wabbit.data.Need

val strict = Delay.strict<Int>()
val lazy = Delay.need<Int>()

check(strict.wrap(Need.now(1)) == 1)
check(lazy.wrap(Need.now(Need.now(2))).value == 2)
```

## Concurrency

`Need` is safe to publish and read, but it does not guarantee exactly-once evaluation under
concurrent forcing. If multiple threads force the same unevaluated value at the same time, they may
perform duplicate work before converging on the same cached result. Use it for deterministic lazy
values, not for coordinating side effects that must run once globally.

## Status

This library is small and stable in scope. Its public API is focused on `Need` and `Delay`; new
features should preserve the current multiplatform and stack-safe evaluation model.

## Documentation

- [User guide](docs/user-guide.md)
- [API reference notes](docs/api-reference.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Development](docs/development.md)

Generated API docs can be built locally with Dokka. See [API reference notes](docs/api-reference.md)
for the command.

## Release Notes

- [CHANGELOG.md](CHANGELOG.md)

## Licensing

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0) for open source use.

For commercial use, contact Wabbit Consulting Corporation at `wabbit@wabbit.one`.

## Contributing

Before contributions can be merged, contributors need to agree to the repository CLA.
