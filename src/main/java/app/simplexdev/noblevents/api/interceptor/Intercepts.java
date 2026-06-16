package app.simplexdev.noblevents.api.interceptor;

import org.bukkit.event.Event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controls which event types an {@link EventInterceptor} handles and the order in which
 * it runs relative to other interceptors. Lower priority values run first.
 *
 * <p>Omit {@link #value()} (or leave it empty) to intercept every event type.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Intercepts {

    Class<? extends Event>[] value() default {};

    int priority() default 0;
}
