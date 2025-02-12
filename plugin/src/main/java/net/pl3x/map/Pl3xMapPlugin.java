package net.pl3x.map;

import java.lang.reflect.Field;
import net.pl3x.map.api.Pl3xMap;
import net.pl3x.map.api.addon.AddonManager;
import net.pl3x.map.api.heightmap.HeightmapRegistry;
import net.pl3x.map.api.httpd.IntegratedServer;
import net.pl3x.map.api.image.io.IO;
import net.pl3x.map.api.player.PlayerManager;
import net.pl3x.map.api.registry.IconRegistry;
import net.pl3x.map.command.CommandManager;
import net.pl3x.map.configuration.AdvancedConfig;
import net.pl3x.map.configuration.Config;
import net.pl3x.map.configuration.Lang;
import net.pl3x.map.httpd.UndertowServer;
import net.pl3x.map.logger.LogFilter;
import net.pl3x.map.logger.Logger;
import net.pl3x.map.logger.Pl3xLogger;
import net.pl3x.map.player.BukkitPlayerManager;
import net.pl3x.map.player.PlayerListener;
import net.pl3x.map.render.task.RendererRegistry;
import net.pl3x.map.task.UpdatePlayerData;
import net.pl3x.map.task.UpdateWorldData;
import net.pl3x.map.world.WorldListener;
import net.pl3x.map.world.WorldManager;
import org.apache.commons.lang.BooleanUtils;
import org.apache.logging.log4j.LogManager;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Pl3xMapPlugin extends JavaPlugin implements Pl3xMap {
    private static Pl3xMapPlugin INSTANCE;

    private UpdatePlayerData updatePlayerDataTask;
    private UpdateWorldData updateWorldDataTask;
    private WorldListener worldListener;

    private AddonManager addonManager;
    private HeightmapRegistry heightmapRegistry;
    private IconRegistry iconRegistry;
    private IntegratedServer integratedServer;
    private PaletteManager paletteManager;
    private PlayerManager playerManager;
    private RendererRegistry rendererRegistry;
    private WorldManager worldManager;

    public static Pl3xMapPlugin getInstance() {
        return INSTANCE;
    }

    public Pl3xMapPlugin() {
        super();

        INSTANCE = this;

        // try to hack in a fancier logger :3
        try {
            Field logger = JavaPlugin.class.getDeclaredField("logger");
            logger.trySetAccessible();
            logger.set(this, new Pl3xLogger());
        } catch (Throwable ignore) {
        }

        // this filter lets us hide undertow/xnio/jboss messages to the logger
        ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addFilter(new LogFilter());
    }

    @Override
    public void onEnable() {
        // test for Paper
        try {
            Class.forName("io.papermc.paper.configuration.PaperConfigurations");
        } catch (ClassNotFoundException e) {
            Logger.severe("This plugin requires Paper or one of its forks to run");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // setup API
        try {
            Field api = Pl3xMap.Provider.class.getDeclaredField("api");
            api.setAccessible(true);
            api.set(null, this);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // load up configs
        saveDefaultConfig();
        Config.reload();
        Lang.reload();
        AdvancedConfig.reload();

        // we have to be the plugin that initializes IO.
        // if an addon does, it can't find the WebP stuff.
        IO.get("png");

        // register bukkit listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        this.worldListener = new WorldListener(this);
        getServer().getPluginManager().registerEvents(this.worldListener, this);

        // setup managers
        this.addonManager = new AddonManager();
        this.heightmapRegistry = new HeightmapRegistry();
        this.iconRegistry = new IconRegistry();
        this.integratedServer = new UndertowServer();
        this.paletteManager = new PaletteManager();
        this.playerManager = new BukkitPlayerManager();
        this.rendererRegistry = new RendererRegistry();
        this.worldManager = new WorldManager();

        // register command manager
        try {
            new CommandManager(this);
        } catch (Exception e) {
            Logger.warn("Failed to initialize command manager");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // start bstats metrics
        Metrics metrics = new Metrics(this, 10133);
        metrics.addCustomChart(new SimplePie("internal_web_server", () ->
                BooleanUtils.toStringTrueFalse(Config.HTTPD_ENABLED)));

        // enable the plugin
        enable();
    }

    @Override
    public void onDisable() {
        disable();
    }

    public void enable() {
        // start integrated server
        getIntegratedServer().startServer();

        // load up worlds already loaded in bukkit
        Bukkit.getWorlds().forEach(getWorldManager()::loadWorld);

        // register events
        if (this.worldListener != null) {
            this.worldListener.registerEvents();
        }

        // start updating player data
        this.updatePlayerDataTask = new UpdatePlayerData();
        this.updatePlayerDataTask.runTaskTimer(this, 20, 20);

        // start updating world data
        this.updateWorldDataTask = new UpdateWorldData();
        this.updateWorldDataTask.runTaskTimer(this, 0, 20 * 5);

        // enable addons
        getAddonManager().enableAddons();
    }

    public void disable() {
        // disable addons
        getAddonManager().disableAddons();

        // stop integrated server
        getIntegratedServer().stopServer();

        // stop updating player data
        if (this.updatePlayerDataTask != null) {
            if (!this.updatePlayerDataTask.isCancelled()) {
                this.updatePlayerDataTask.cancel();
            }
            this.updatePlayerDataTask = null;
        }

        // stop updating world data
        if (this.updateWorldDataTask != null) {
            if (!this.updateWorldDataTask.isCancelled()) {
                this.updateWorldDataTask.cancel();
            }
            this.updateWorldDataTask = null;
        }

        // unregister events
        if (this.worldListener != null) {
            this.worldListener.unregisterEvents();
        }

        // unload all map worlds
        getWorldManager().unload();
    }

    @Override
    public AddonManager getAddonManager() {
        return this.addonManager;
    }

    @Override
    public HeightmapRegistry getHeightmapRegistry() {
        return this.heightmapRegistry;
    }

    @Override
    public IconRegistry getIconRegistry() {
        return this.iconRegistry;
    }

    @Override
    public IntegratedServer getIntegratedServer() {
        return this.integratedServer;
    }

    @Override
    public PaletteManager getPaletteManager() {
        return this.paletteManager;
    }

    @Override
    public PlayerManager getPlayerManager() {
        return this.playerManager;
    }

    @Override
    public RendererRegistry getRendererRegistry() {
        return this.rendererRegistry;
    }

    @Override
    public WorldManager getWorldManager() {
        return this.worldManager;
    }
}
