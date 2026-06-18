package app.simplexdev.noblevents.impl.interceptors;

import app.simplexdev.noblevents.api.interceptor.EventInterceptor;
import app.simplexdev.noblevents.api.interceptor.InterceptorContext;
import app.simplexdev.noblevents.api.interceptor.Intercepts;

import org.slf4j.Logger;

@Intercepts(priority = Integer.MAX_VALUE)
public final class LoggingInterceptor implements EventInterceptor {

    private final Logger logger;

    public LoggingInterceptor(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void intercept(InterceptorContext<?> context) {
        logger.debug("[NoblEvents] dispatch: {}", context.event().getEventName());
    }
}
