package app.simplexdev.noblevents;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class EventStreamTest {
    static class SimpleEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final String tag;

        SimpleEvent(String tag) { this.tag = tag; }
        String tag() { return tag; }

        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    static class CancellableEvent extends Event implements Cancellable {
        private static final HandlerList HANDLERS = new HandlerList();
        private boolean cancelled;

        @Override public boolean isCancelled() { return cancelled; }
        @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    /** Convenience: build an EventStream from a raw sink without a plugin reference. */
    private static EventStream<SimpleEvent> stream(Sinks.Many<SimpleEvent> sink) {
        return new EventStream<>(SimpleEvent.class, sink.asFlux(), null);
    }

    @Test
    void filter_passes_only_matching_events() {
        Sinks.Many<SimpleEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

        StepVerifier.create(stream(sink).filter(e -> e.tag().startsWith("ok")).flux())
            .then(() -> {
                sink.tryEmitNext(new SimpleEvent("ok-1"));
                sink.tryEmitNext(new SimpleEvent("skip"));
                sink.tryEmitNext(new SimpleEvent("ok-2"));
                sink.tryEmitComplete();
            })
            .expectNextMatches(e -> e.tag().equals("ok-1"))
            .expectNextMatches(e -> e.tag().equals("ok-2"))
            .verifyComplete();
    }

    @Test
    void ignoreCancelled_filters_cancelled_events() {
        Sinks.Many<CancellableEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        EventStream<CancellableEvent> stream =
            new EventStream<>(CancellableEvent.class, sink.asFlux(), null);

        CancellableEvent cancelled = new CancellableEvent();
        cancelled.setCancelled(true);
        CancellableEvent live = new CancellableEvent();

        StepVerifier.create(stream.ignoreCancelled().flux())
            .then(() -> {
                sink.tryEmitNext(cancelled);
                sink.tryEmitNext(live);
                sink.tryEmitComplete();
            })
            .expectNext(live)
            .verifyComplete();
    }

    @Test
    void limit_auto_cancels_after_count() {
        Sinks.Many<SimpleEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

        StepVerifier.create(stream(sink).limit(2).flux())
            .then(() -> {
                sink.tryEmitNext(new SimpleEvent("a"));
                sink.tryEmitNext(new SimpleEvent("b"));
                sink.tryEmitNext(new SimpleEvent("c"));
            })
            .expectNextCount(2)
            .verifyComplete();
    }

    @Test
    void once_auto_cancels_after_first_event() {
        Sinks.Many<SimpleEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

        StepVerifier.create(stream(sink).once().flux())
            .then(() -> {
                sink.tryEmitNext(new SimpleEvent("first"));
                sink.tryEmitNext(new SimpleEvent("second"));
            })
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void operators_return_new_instance_leaving_original_unchanged() {
        Sinks.Many<SimpleEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        EventStream<SimpleEvent> original = stream(sink);
        EventStream<SimpleEvent> filtered = original.filter(e -> false);

        assertNotSame(original, filtered);

        StepVerifier.create(original.flux())
            .then(() -> {
                sink.tryEmitNext(new SimpleEvent("x"));
                sink.tryEmitComplete();
            })
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void subscribe_null_consumer_throws_NPE() {
        Sinks.Many<SimpleEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        EventStream<SimpleEvent> stream = stream(sink);
        assertThrows(NullPointerException.class, () -> stream.subscribe(null));
    }

    @Test
    void subscribe_null_error_handler_throws_NPE() {
        Sinks.Many<SimpleEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        EventStream<SimpleEvent> stream = stream(sink);
        assertThrows(NullPointerException.class, () -> stream.subscribe(e -> {}, null));
    }

    static class WrappedEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        final SimpleEvent source;
        WrappedEvent(SimpleEvent src) { this.source = src; }
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    @Test
    void map_transforms_event_type() {
        Sinks.Many<SimpleEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

        StepVerifier.create(
                stream(sink)
                    .map(WrappedEvent::new, WrappedEvent.class)
                    .flux())
            .then(() -> {
                sink.tryEmitNext(new SimpleEvent("hello"));
                sink.tryEmitComplete();
            })
            .expectNextMatches(w -> w.source.tag().equals("hello"))
            .verifyComplete();
    }
}
