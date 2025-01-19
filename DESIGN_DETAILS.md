# DESIGN DETAILS

## Overview

`Need<A>` is a Kotlin class that provides **lazy evaluation** with **memoization**. It allows you to define computations that will be evaluated only once, and thereafter cached, so subsequent queries don’t recompute the value. It also supports functional composition via `map` and `flatMap` and can (in a limited sense) be used in recursive or mutually recursive definitions.

This file details the low-level mechanics of how `Need` is implemented, including:

- How `Need` stores and evaluates its internal computation (the “thunk”).
- How `map` and `flatMap` are represented.
- How the evaluation loop (“trampoline”) unrolls nested computations.
- How memoization is achieved (and some notes on concurrency).

## 1. Basic Concepts

### 1.1 `Thunk`

Internally, `Need` stores a “thunk,” which is a representation of the deferred computation. A `thunk` can be:

- A **pre-evaluated value** (the final result).
- A **`Done(value)`** marker, meaning “the lazy computation is already completed, and here’s the result.”
- A **`Map(left, f)`**, meaning “apply function `f` to the result of `left`.”
- A **`FlatMap(left, f)`**, meaning “apply function `f` to the result of `left`, which will produce a new `Need`.”

These forms let us represent an entire chain of lazy computations without actually forcing them until someone calls `.value`.

### 1.2 Memoization

Once a `Need<A>` instance has evaluated its computation, we overwrite the internal `thunk` with the computed result (so `thunk` becomes a raw `A`). This ensures that subsequent calls to `.value` are constant-time: they immediately return the cached result.

### 1.3 Stack-Based Evaluation

`Need` does not rely on the JVM stack to evaluate nested `map`/`flatMap` calls. Instead, it uses a **while-loop with a manual stack** to unroll the chain of computations. This approach prevents deep recursion that can overflow the call stack if you create large chains of `map` or `flatMap`.

---

## 2. Anatomy of the `Need` Class

### 2.1 The Constructor and `value` Property

```kotlin
class Need<out A> private constructor(@Volatile private var thunk: Any?) {
    val value: A
        get() {
            val thunk = this.thunk
            if (thunk !is Thunk<*>) {
                // Already computed
                return thunk as A
            }
            val result = when (thunk) {
                is Thunk.Done<*> -> thunk.value
                else -> evaluate(this) // Full evaluation of the chain
            }
            this.thunk = result // Memoize
            return result as A
        }
    ...
}
```

- The `@Volatile` annotation ensures that if multiple threads read `thunk`, they see a consistent pointer to the underlying state, but this does **not** fully synchronize concurrent evaluation.  
- When you access `value`:
  1. If `thunk` is **already** a final value, it’s returned immediately.
  2. If `thunk` is a `Thunk`, we call `evaluate(this)`, which forces the computation to a final value.
  3. The final result is stored into `thunk`, enabling memoization.

### 2.2 `map` and `flatMap`

The `map` function:

```kotlin
fun <B> map(f: (A) -> B): Need<B> = Need(Thunk.Map(this, f))
```

- Creates a new `Need<B>` with a `Thunk.Map(...)` referencing the original `Need<A>`.
- This is purely structural. It doesn’t force anything at creation time.

The `flatMap` function:

```kotlin
fun <B> flatMap(f: (A) -> Need<B>): Need<B> = Need(Thunk.FlatMap(this, f))
```

- Similar to `map`, but instead of applying `f` and returning a raw `B`, it returns a new `Need<B>` from `f`.
- `Thunk.FlatMap` is unwrapped only when forced.

These two allow **compositional** definitions of lazy computations (often called a monadic interface).

### 2.3 Evaluation Process (`evaluate`)

The core logic is in the private `evaluate(root: Need<A>)` function. It operates like this:

1. **Setup**:  
   - Start with `current = root`.
   - Maintain a separate stack structure (`stack`) for pending transformations.

2. **Loop**:  
   - Repeatedly examine `current.thunk`:
     - If it’s already a plain value (not a `Thunk`), we might pop from the stack, apply a final transformation, and continue.
     - If it’s a `Thunk.Done(value)`, pop from the stack, apply transformations, etc.
     - If it’s `Thunk.Map(left, f)`, push a “MAP” operation on the stack and move `current` to `left`.
     - If it’s `Thunk.FlatMap(left, f)`, push a “FLATMAP” operation on the stack and move `current` to `left`.
   - Eventually, you’ll reduce the chain to a final value.

3. **In-lining**:  
   - When the loop finishes evaluating a sub-computation, it “in-lines” the result back into `current.thunk`. This discards the old intermediate structure so that references to partial closures can be GC’d sooner.

4. **Memoization**:  
   - Once the root `Need` is fully evaluated, `root.thunk` is assigned the final raw value. This is the key step that ensures subsequent `.value` calls cost O(1).

Because we do it all in a while-loop, we avoid the JVM’s method call stack. This is a common technique also known as **trampolining**.

---

## 3. Handling of `recursive`, `now`, `apply`, etc.

- **`Need.now(a)`**: Wraps a value eagerly. No lazy computation. Immediately sets `thunk = a`.
- **`Need.apply { ... }`**: Creates a thunk that defers evaluation until needed. Internally it’s basically `unit.map { yourLambda() }`.
- **`Need.defer { anotherNeed }`**: If you want to flatten one more layer, you can do so with a `FlatMap`.
- **`Need.recursive(f)`**: This is used for lazy self-referential or mutually recursive definitions. It sets up a `Need` whose initial thunk references a `FlatMap` that calls `f(...)` with the same `Need`. If `f` is well-defined, eventually the chain yields a result.

---

## 4. Concurrency Nuances

- `Need` is **not fully thread-safe** in the sense of guaranteeing no duplicate evaluations. Two threads racing to evaluate the same `Need` might do redundant work.  
- The `@Volatile` field ensures that once `thunk` is set to a final value, other threads see that updated reference.  
- If your computations are pure, the worst-case scenario is duplication of effort. Eventually, both threads converge on the same value.  
- If you need stronger thread-safety (e.g., guaranteed single evaluation), you would need additional locking or compare-and-set logic—**but** that also increases overhead.  

---

## 5. Equality, Hashing, and String Representation

- **Equality**: Two `Need`s are equal if, when forced, their values compare equal:
  ```kotlin
  override fun equals(other: Any?) = this.value == (other as? Need<*>)?.value
  ```
  This forces both `Need`s, which can be expensive if they haven’t already been evaluated.
- **Hash Code**: Derived from the forced value, meaning that once evaluated, identical final values yield the same hash.  
- **`toString()`**: By default, prints `Need(...)`. If you haven’t forced it yet, you might see references to `Thunk.FlatMap`, etc. If you have forced it, the `thunk` is the final value.

---

## 6. Common Use Cases

1. **Delayed/expensive computations**:  
   ```kotlin
   val expensive = Need.apply { someLongRunningOperation() }
   // ...
   println(expensive.value) // Only now is it computed
   ```
2. **Chaining**:
   ```kotlin
   val n = Need.apply { 5 }
       .map { it + 1 }
       .map { it * 2 }
   // n.value is 12
   ```
3. **Sharing**: If you hold onto the same `Need` in multiple places, the result is computed once, then cached.

---

## 7. Potential Pitfalls

1. **Infinite recursion**: If you do something like:
   ```kotlin
   val loop = Need.recursive { it -> it }
   loop.value  // never terminates
   ```
   You will never bottom out.
2. **Concurrency**: As noted, not strictly guaranteed to do a single evaluation in multi-threaded scenarios. You might see repeated calls but not corruption, assuming your code is pure.
3. **Forcing in `equals`**: If you accidentally compare two `Need`s for equality, you can trigger a large or expensive computation.

---

## 8. Performance

- Because we use a while-loop “trampoline,” repeated `map/flatMap` doesn’t blow the stack.  
- Once forced, subsequent `.value` calls are O(1).  
- In concurrent settings, you may see extra computations but generally no data corruption.

---

## 9. Conclusion

The `Need` class is effectively a **single-threaded lazy evaluation** monad with partial concurrency support. Its design closely follows the “free monad” or “trampolined computation” pattern often seen in functional programming languages. The approach is:

1. Represent the computation as a chain of `Map` or `FlatMap` thunks.  
2. Use a manual stack-based evaluation to avoid deep recursion.  
3. Memoize after the first evaluation by storing the final value in `thunk`.  

This yields a flexible, efficient, and relatively easy-to-grok solution for lazy evaluation in Kotlin.