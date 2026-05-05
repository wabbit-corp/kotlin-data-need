# API Reference

Generated Dokka API docs can be built with:

```bash
./gradlew dokkaGeneratePublicationHtml
```

## Need

`Need<A>` represents a value of type `A` that may not have been evaluated yet.

- `value` forces evaluation and returns the cached result.
- `map` transforms the eventual value without forcing it immediately.
- `flatMap` sequences another lazy computation.
- `zip` combines this `Need` with another and returns a lazy `Pair`.
- `zipLeft` evaluates this value first, then returns the right-hand value.
- `zipRight` evaluates both values and keeps the left-hand value.

The companion object provides constructors and helpers:

- `now` wraps an already available value.
- `unit` is a cached `Need<Unit>`.
- `apply` delays a strict computation.
- `defer` delays a computation that already returns `Need`.
- `zip` overloads combine two to four `Need` values with a function.
- `recursive` supports self-referential lazy definitions.
- `build` creates a memoized nullable resolver and caches only non-null values.
- `buildNonNull` creates a memoized resolver for non-null values.

## Delay

`Delay<A>` abstracts over a representation that can wrap and unwrap `Need<A>`.

- `strict` evaluates wrapped values immediately and stores them as `A`.
- `need` keeps values in `Need`.
- `recursive` builds recursive delayed values using `Need.recursive`.
- `force` unwraps, evaluates one layer, and wraps the evaluated value again.

## Behavioral Notes

Equality and hash codes force the compared `Need` values because they are based on `value`.

Concurrent forcing may duplicate work. The final cached result converges, but `Need` does not
serialize the first evaluation across threads.
