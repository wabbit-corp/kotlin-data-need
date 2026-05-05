# Development

## Build

```bash
./dev build kotlin-data-need
```

The build covers JVM, Android library metadata, iOS simulator, iOS device, and host-native targets.

## Documentation Checks

```bash
./dev verify docs kotlin-data-need
./gradlew -p kotlin-data-need dokkaGeneratePublicationHtml
```

Keep README examples executable-looking and prefer `check` assertions so snippets document expected
behavior directly.

## Publication Dry Run

```bash
./dev publish --dry-run kotlin-data-need
```

The generated Gradle metadata comes from `root.clj`. Update the root project definition and rerun
`./dev setup --local kotlin-data-need` instead of editing generated Gradle files directly.

## KDoc Expectations

Public API additions should include KDoc that explains forcing behavior, caching behavior, and
concurrency caveats when relevant. `Need` intentionally allows duplicate concurrent evaluation, so
new helpers should avoid implying exactly-once execution.
