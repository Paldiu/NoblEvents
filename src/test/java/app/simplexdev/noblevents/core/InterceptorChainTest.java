package app.simplexdev.noblevents.core;

import app.simplexdev.noblevents.api.interceptor.EventInterceptor;
import app.simplexdev.noblevents.api.interceptor.InterceptorContext;
import app.simplexdev.noblevents.api.interceptor.Intercepts;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class InterceptorChainTest {
    static class Alpha extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    static class Beta extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        @Override public HandlerList getHandlers() { return HANDLERS; }
        public static HandlerList getHandlerList() { return HANDLERS; }
    }

    @Test
    void interceptors_run_in_ascending_priority_order() {
        List<Integer> order = new ArrayList<>();

        @Intercepts(priority = -10)
        class First implements EventInterceptor {
            @Override public void intercept(InterceptorContext<?> ctx) { order.add(1); }
        }

        @Intercepts(priority = 0)
        class Second implements EventInterceptor {
            @Override public void intercept(InterceptorContext<?> ctx) { order.add(2); }
        }

        @Intercepts(priority = 10)
        class Third implements EventInterceptor {
            @Override public void intercept(InterceptorContext<?> ctx) { order.add(3); }
        }

        InterceptorChain chain = new InterceptorChain();
        chain.register(new Third());
        chain.register(new First());
        chain.register(new Second());

        chain.process(new Alpha());

        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void no_annotation_defaults_to_priority_zero() {
        List<Integer> order = new ArrayList<>();

        @Intercepts(priority = -5)
        class Early implements EventInterceptor {
            @Override public void intercept(InterceptorContext<?> ctx) { order.add(1); }
        }

        EventInterceptor middle = ctx -> order.add(2);

        @Intercepts(priority = 5)
        class Late implements EventInterceptor {
            @Override public void intercept(InterceptorContext<?> ctx) { order.add(3); }
        }

        InterceptorChain chain = new InterceptorChain();
        chain.register(new Late());
        chain.register(middle);
        chain.register(new Early());

        chain.process(new Alpha());

        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void cancel_stops_remaining_interceptors() {
        List<String> ran = new ArrayList<>();

        InterceptorChain chain = new InterceptorChain();
        chain.register(ctx -> { ran.add("first"); ctx.cancel(); });
        chain.register(ctx -> ran.add("second")); 

        InterceptorContext<?> result = chain.process(new Alpha());

        assertEquals(List.of("first"), ran);
        assertTrue(result.isCancelled());
    }

    @Test
    void process_returns_non_cancelled_context_when_no_interceptor_cancels() {
        InterceptorChain chain = new InterceptorChain();
        chain.register(ctx -> { /* observe only */ });

        InterceptorContext<?> result = chain.process(new Alpha());

        assertFalse(result.isCancelled());
    }

    @Test
    void intercepts_value_restricts_to_matching_event_type() {
        List<String> ran = new ArrayList<>();

        @Intercepts(value = Alpha.class)
        class AlphaOnly implements EventInterceptor {
            @Override public void intercept(InterceptorContext<?> ctx) { ran.add("ran"); }
        }

        InterceptorChain chain = new InterceptorChain();
        chain.register(new AlphaOnly());

        chain.process(new Beta());
        assertTrue(ran.isEmpty());

        chain.process(new Alpha());
        assertEquals(List.of("ran"), ran);
    }

    @Test
    void empty_intercepts_value_matches_all_event_types() {
        List<String> ran = new ArrayList<>();

        @Intercepts
        class All implements EventInterceptor {
            @Override public void intercept(InterceptorContext<?> ctx) { ran.add("ran"); }
        }

        InterceptorChain chain = new InterceptorChain();
        chain.register(new All());

        chain.process(new Alpha());
        chain.process(new Beta());

        assertEquals(List.of("ran", "ran"), ran);
    }

    @Test
    void unregister_removes_interceptor_from_chain() {
        List<String> ran = new ArrayList<>();
        EventInterceptor interceptor = ctx -> ran.add("ran");

        InterceptorChain chain = new InterceptorChain();
        chain.register(interceptor);
        chain.unregister(interceptor);

        chain.process(new Alpha());

        assertTrue(ran.isEmpty());
    }

    @Test
    void interceptors_snapshot_is_immutable_copy() {
        InterceptorChain chain = new InterceptorChain();
        EventInterceptor i = ctx -> {};
        chain.register(i);

        List<EventInterceptor> snapshot = chain.interceptors();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.remove(i));
    }
}
