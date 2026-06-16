package app.simplexdev.noblevents.impl;

import app.simplexdev.noblevents.core.AbstractEventBus;
import org.bukkit.plugin.Plugin;

public final class BukkitEventBus extends AbstractEventBus {

    public BukkitEventBus(Plugin plugin) {
        super(plugin);
    }
}
