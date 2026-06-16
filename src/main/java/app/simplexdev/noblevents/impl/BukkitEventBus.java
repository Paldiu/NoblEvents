package app.simplexdev.noblevents.impl;

import app.simplexdev.noblevents.core.AbstractEventBus;
import org.bukkit.plugin.Plugin;

/**
 * The default concrete implementation of {@link AbstractEventBus}.
 * Exists as a named type so callers can distinguish the base implementation from
 * decorator subclasses (e.g. {@link app.simplexdev.noblevents.core.InterceptorAwareEventBus})
 * and to preserve the extension point: plugins that need custom emit logic can subclass this
 * rather than {@code AbstractEventBus} directly.
 */
public class BukkitEventBus extends AbstractEventBus {

    public BukkitEventBus(Plugin plugin) {
        super(plugin);
    }
}
