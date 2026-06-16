package app.simplexdev.noblevents.api.interceptor;

public interface EventInterceptor {

    /**
     * Called for every event before it is emitted to subscribers.
     * Call {@link InterceptorContext#cancel()} to suppress the event entirely —
     * it will not reach any subscriber on this bus (the underlying Bukkit event is unaffected).
     */
    void intercept(InterceptorContext<?> context);
}
