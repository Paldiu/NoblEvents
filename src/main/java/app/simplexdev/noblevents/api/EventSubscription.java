package app.simplexdev.noblevents.api;

import org.bukkit.event.Event;

public interface EventSubscription {

    void cancel();

    boolean isCancelled();

    Class<? extends Event> eventType();
}
