The "kotlin-data-need" library offers an elegant solution for deferred computation and caching in Kotlin applications. By allowing computations to be evaluated only when needed and caching the results, it provides performance benefits and flexibility in scenarios where efficiency is paramount. The library facilitates lazy evaluation and memoization through its primary component, the `Need` class, which supports transformations and combinations of computations via functional operations like `map`, `flatMap`, and `zip`.

## 🚀  Installation

Add the following dependency to your project:

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.wabbit-corp:kotlin-data-need:1.2.0")
}
```

## 🚀  Usage

- **Lazy Initialization**:
  ```kotlin
  val lazyValue = Need.defer { expensiveComputation() }
  println(lazyValue.value)  // Computation happens here
  ```
- **Transformation with `map`**:
  ```kotlin
  val transformedNeed = lazyValue.map { it + 10 }
  println(transformedNeed.value)
  ```
- **Chained Operations using `flatMap`**:
  ```kotlin
  val chainedNeed = lazyValue.flatMap { value ->
      Need.now(value * 2)
  }
  println(chainedNeed.value)
  ```
- **Combining Values with `zip`**:
  ```kotlin
  val combinedNeed = lazyValue.zip(Need.now(5))
  println(combinedNeed.value)  // Outputs a Pair
  ```

## Licensing

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0) for open source use.

For commercial use, please contact Wabbit Consulting Corporation (at wabbit@wabbit.one) for licensing terms.

## Contributing

Before we can accept your contributions, we kindly ask you to agree to our Contributor License Agreement (CLA).
