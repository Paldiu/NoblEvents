package app.simplexdev.noblevents;

import app.simplexdev.noblevents.api.EventBus;
import app.simplexdev.noblevents.core.BukkitEventBus;
import org.bukkit.plugin.java.JavaPlugin;

public class NoblEvents extends JavaPlugin {

    private static NoblEvents instance;
    private EventBus eventBus;

    @Override
    public void onEnable() {
        instance = this;
        eventBus = new BukkitEventBus(this);
        getLogger().info("NoblEvents enabled.");
    }

    @Override
    public void onDisable() {
        if (eventBus != null) {
            eventBus.shutdown();
        }
        getLogger().info("NoblEvents disabled.");
    }

    public static NoblEvents getInstance() {
        return instance;
    }

    public static EventBus getEventBus() {
        return instance.eventBus;
    }
}
