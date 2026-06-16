package app.simplexdev.noblevents.api.interceptor;

import org.bukkit.event.Event;

public final class InterceptorContext<E extends Event> {

    private final E event;
    private boolean cancelled;

    public InterceptorContext(E event) {
        this.event = event;
    }

    public E event() {
        return event;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
