# NoblEvents

A reactive event library for Paper plugins built on [Project Reactor](https://projectreactor.io/).
Wraps Bukkit's listener system behind a `Flux`-based API so you can filter, debounce, throttle,
and compose event pipelines with operators instead of boilerplate listener classes.

## Requirements

- Java 25
- Paper (or any Paper-compatible fork)

## Installation

Add the published artifact to your build. The shadow JAR bundles Reactor, so no extra dependency
management is needed on the server.

**Gradle (Kotlin DSL)**
```kotlin
implementation("app.simplexdev:noblevents:0.1.0")
```

**Gradle (Groovy DSL)**
```groovy
implementation 'app.simplexdev:noblevents:0.1.0'
```

Repository:
```kotlin
maven("https://oss.simplexdev.app/releases")
```

## Quick Start

```java
// Listen for every PlayerMoveEvent on the main thread
NoblEvents.events(PlayerMoveEvent.class)
    .ignoreCancelled()
    .onMainThread()
    .subscribe(event -> {
        // handle event
    });
```

The library lazily registers the underlying Bukkit listener on the first subscriber and
**unregisters it automatically when the last subscriber for that event type leaves** (subscriber
counts are tracked per `(event type, priority, ignoreCancelled)` combination). Everything is also
torn down when NoblEvents itself disables.

Note the lifecycle distinction: a subscription created through the static `NoblEvents.events(...)`
entry point is **not** tied to your plugin — you must cancel it yourself (keep the returned
`EventSubscription`). To have subscriptions cancelled automatically when *your* plugin disables, use
[`NoblEvents.forPlugin(this)`](#plugin-scoped-subscriptions) instead.

## API

### `NoblEvents.events(Class<E>)`

Entry point. Returns an `EventStream<E>` — a fluent builder wrapping a `Flux<E>`. This default
registers the underlying Bukkit listener at `EventPriority.NORMAL` and receives already-cancelled
events.

To control the Bukkit listener priority or skip already-cancelled events at the listener level, use
the overload `NoblEvents.events(Class<E>, EventPriority, boolean ignoreCancelled)` (also available on
`PluginEventContext`). Each unique `(type, priority, ignoreCancelled)` combination is a distinct
Bukkit registration with its own teardown.

```java
// Observe at MONITOR priority and skip events other plugins already cancelled
NoblEvents.events(BlockBreakEvent.class, EventPriority.MONITOR, true)
    .subscribe(e -> auditBreak(e));
```

### `EventStream<E>` operators

| Method | Description |
|---|---|
| `filter(Predicate)` | Keep events matching the predicate |
| `ignoreCancelled()` | Skip events where `isCancelled()` is true |
| `debounce(Duration)` | Emit only after a silent gap — suppresses burst spam |
| `throttle(Duration)` | Emit the most-recent event at a fixed interval |
| `limit(long)` | Cancel after N events |
| `once()` | Shorthand for `limit(1)` |
| `onMainThread()` | Deliver on Bukkit's main thread (safe for all Bukkit API calls) |
| `onAsync()` | Deliver on a dedicated single-threaded async scheduler (ordering preserved; never call Bukkit API here) |
| `flux()` | Escape hatch — returns the raw `Flux<E>` for advanced composition |

### Subscribing

```java
EventSubscription sub = NoblEvents.events(PlayerJoinEvent.class)
    .onMainThread()
    .subscribe(
        event  -> { /* onNext   */ },
        error  -> { /* onError  */ },
        ()     -> { /* onComplete */ }
    );

// Cancel the subscription when you no longer need it
sub.cancel();
```

`EventSubscription` wraps the underlying `Disposable` and exposes a `cancel()` method and
the event type it was registered for.

## Plugin-scoped subscriptions

`NoblEvents.forPlugin(plugin)` returns a `PluginEventContext` that tracks every subscription you
create through it and **cancels them all automatically when your plugin disables** (it listens for
your plugin's `PluginDisableEvent`). This is the recommended entry point for normal plugin use — you
never have to hold and cancel each `EventSubscription` by hand.

```java
public final class MyPlugin extends JavaPlugin {
    private PluginEventContext events;

    @Override
    public void onEnable() {
        events = NoblEvents.forPlugin(this);

        events.events(PlayerMoveEvent.class)
            .ignoreCancelled()
            .onMainThread()
            .subscribe(e -> { /* ... */ });
        // cancelled for you when this plugin disables
    }
}
```

`BoundStream` (returned by `context.events(...)`) mirrors the full `EventStream` operator set, and
each subscription removes itself from the context as soon as it terminates (complete, error, or
cancel) — so short-lived streams like `.once()` don't accumulate. Call `context.cancelAll()` for
manual early teardown.

## Interceptors

Interceptors run before every event reaches any subscriber. They can inspect the event or suppress
it entirely (without affecting the underlying Bukkit event).

```java
@Intercepts(value = PlayerMoveEvent.class, priority = 10)
public class MyInterceptor implements EventInterceptor {

    @Override
    public void intercept(InterceptorContext<?> ctx) {
        if (someCondition()) {
            ctx.cancel(); // event will not reach subscribers on this bus
        }
    }
}

// Register globally
NoblEvents.registerInterceptor(new MyInterceptor());

// Unregister when done
NoblEvents.unregisterInterceptor(myInterceptor);
```

`@Intercepts` is optional. Omitting it (or leaving `value` empty) causes the interceptor to run
for every event type. Lower `priority` values run first.

A `LoggingInterceptor` is registered automatically at startup and logs every event that passes
through the bus.

## Advanced Usage

For operators not exposed by `EventStream` (buffering, windowing, zipping, etc.), call `.flux()`
to get the raw `Flux` and compose freely with Reactor's full API.

```java
Flux<PlayerMoveEvent> raw = NoblEvents.events(PlayerMoveEvent.class).flux();

raw.bufferTimeout(10, Duration.ofSeconds(1))
   .subscribe(batch -> processBatch(batch));
```

Direct bus access is also available for callers that want to build their own pipelines:

```java
EventBus bus = NoblEvents.getEventBus();
Flux<BlockBreakEvent> flux = bus.of(BlockBreakEvent.class);
```

## Building

```bash
./gradlew build
```

The output JAR (with Reactor bundled) is written to `build/libs/`.

## License

See [LICENSE.md](LICENSE.md).
