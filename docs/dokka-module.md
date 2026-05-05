# Module kotlin-data-need

Kotlin Multiplatform lazy computation, memoization, and delay abstractions.

Use `Need` when a value should be described now, evaluated later, and cached after the first forced
read. Use `Delay` when generic code needs to abstract over strict values and `Need`-wrapped values.

## Evaluation Model

`Need` stores either an already computed value or a deferred computation. Calling `value` forces the
computation and stores the result for later reads.

Mapped and flat-mapped computations are evaluated with an internal trampoline, so deep lazy chains
can be forced without relying on recursive stack growth.

## Concurrency

Concurrent readers may duplicate in-flight work. The cached result converges after evaluation, but
`Need` should not be used as an exactly-once side-effect primitive.
