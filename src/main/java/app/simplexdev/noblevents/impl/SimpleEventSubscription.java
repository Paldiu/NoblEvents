package app.simplexdev.noblevents.impl;

import app.simplexdev.noblevents.api.EventSubscription;
import org.bukkit.event.Event;
import reactor.core.Disposable;

public final class SimpleEventSubscription implements EventSubscription {

    private final Disposable disposable;
    private final Class<? extends Event> eventType;

    public SimpleEventSubscription(Disposable disposable, Class<? extends Event> eventType) {
        this.disposable = disposable;
        this.eventType = eventType;
    }

    @Override
    public void cancel() {
        disposable.dispose();
    }

    @Override
    public boolean isCancelled() {
        return disposable.isDisposed();
    }

    @Override
    public Class<? extends Event> eventType() {
        return eventType;
    }
}
