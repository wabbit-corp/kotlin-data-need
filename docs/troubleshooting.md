# Troubleshooting

## The Delayed Function Runs More Than Once

`Need` does not provide exactly-once synchronization. Concurrent callers can observe the same
unevaluated computation and run duplicate work before the cached value converges.

Keep delayed functions pure or idempotent when sharing a `Need` across threads. Use an external
synchronization primitive if a side effect must happen exactly once.

## Equality or Logging Forces Work

`Need.equals` and `Need.hashCode` compare computed values, so they force evaluation. `toString`
shows the current backing state and should be treated as diagnostic output, not a stable serialized
format.

Avoid putting unevaluated `Need` instances in hash-based collections if evaluation is expensive or
has side effects.

## Nullable Values Are Not Cached by `build`

`Need.build` caches only non-null resolver results. A `null` result is returned to the caller but is
not stored, so later lookups for the same key re-run the resolver.

Use `buildNonNull` when every key has a value that should be cached.

## Recursive Values Do Not Terminate

`Need.recursive` permits self-referential definitions. It does not make non-terminating definitions
productive. If forcing the result keeps asking for itself without reaching a base case, evaluation
will not complete.
