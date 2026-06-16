package app.simplexdev.noblevents.impl.interceptors;

import app.simplexdev.noblevents.api.interceptor.EventInterceptor;
import app.simplexdev.noblevents.api.interceptor.InterceptorContext;
import app.simplexdev.noblevents.api.interceptor.Intercepts;

import java.util.logging.Logger;

@Intercepts(priority = Integer.MIN_VALUE)
public final class LoggingInterceptor implements EventInterceptor {

    private final Logger logger;

    public LoggingInterceptor(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void intercept(InterceptorContext<?> context) {
        logger.fine(() -> "[NoblEvents] dispatch: " + context.event().getEventName());
    }
}
