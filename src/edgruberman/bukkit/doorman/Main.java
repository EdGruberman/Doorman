package edgruberman.bukkit.doorman;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.PluginClassLoader;

import edgruberman.bukkit.doorman.commands.Add;
import edgruberman.bukkit.doorman.commands.Edit;
import edgruberman.bukkit.doorman.commands.History;
import edgruberman.bukkit.doorman.commands.Reload;
import edgruberman.bukkit.doorman.commands.Show;
import edgruberman.bukkit.doorman.messaging.Courier.ConfigurationCourier;
import edgruberman.bukkit.doorman.util.CustomPlugin;

public final class Main extends CustomPlugin {

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz");

    public static ConfigurationCourier courier;

    private boolean loaded = false;

    @Override
    public void onLoad() {
        this.putConfigMinimum("1.5.0a4");
        this.putConfigMinimum("language.yml", "1.5.0a0");
        this.setPathSeparator('|'); // enables referencing node names with periods in it

        try {
            this.extract("joda-time-2.2.jar");
        } catch (final Exception e) {
            this.getLogger().log(Level.SEVERE, "Unable to add joda-time-2.2.jar library to class loader; Restart server to enable plugin; " + e);
            return;
        }

        this.loaded = true;
    }

    @Override
    public void onEnable() {
        if (!this.loaded) {
            this.getLogger().log(Level.SEVERE, "Disabling plugin; Dependencies not met during plugin load");
            this.setEnabled(false);
            return;
        }

        this.reloadConfig();
        Main.courier = ConfigurationCourier.create(this).setBase(this.loadConfig("language.yml")).setFormatCode("format-code").build();

        Long grace = this.getConfig().getLong("declaration-grace", -1);
        if (grace != -1) grace = TimeUnit.SECONDS.toMillis(grace);

        final Map<String, Object> switches = new HashMap<String, Object>();
        final ConfigurationSection section = this.getConfig().getConfigurationSection("switches");
        if (section != null) switches.putAll(section.getValues(false));

        Date worldStart;
        try {
            worldStart = Main.DATE_FORMAT.parse(this.getConfig().getString("world-start"));
        } catch (final ParseException e) {
            this.getLogger().log(Level.WARNING, "Unable to parse world-start: {0}; {1}", new Object[] { this.getConfig().getString("world-start"), e });
            worldStart = new Date();
        }

        final RecordKeeper records = new RecordKeeper(this);
        final Doorman doorman = new Doorman(this, records, grace, switches
                , this.parseGreetingSwitches(switches.keySet(), "greeting|headers"), this.parseGreetingSwitches(switches.keySet(), "greeting|arguments")
                , worldStart);

        this.getCommand("doorman:history").setExecutor(new History(records));
        this.getCommand("doorman:show").setExecutor(new Show(doorman, records));
        this.getCommand("doorman:add").setExecutor(new Add(doorman, records));
        this.getCommand("doorman:edit").setExecutor(new Edit(doorman, records));
        this.getCommand("doorman:reload").setExecutor(new Reload(this));
    }

    private List<String> parseGreetingSwitches(final Collection<String> recognized, final String path) {
        final List<String> values = this.getConfig().getStringList(path);
        if (values == null) return Collections.emptyList();

        final Iterator<String> it = values.iterator();
        while (it.hasNext()) {
            final String name = it.next();
            if (recognized.contains(name)) continue;
            this.getLogger().warning("Unrecognized switch specified in " + path + ": " + name);
            it.remove();
        }
        return values;
    }

    @Override
    public void onDisable() {
        Main.courier = null;
        HandlerList.unregisterAll(this);
    }

    private URL extract(final String resource) throws SecurityException, NoSuchMethodException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
        final File output = new File(this.getDataFolder(), resource);
        if (output.exists()) return null;

        this.saveResource(output.getName(), true);

        // first time extraction requires late class path addition
        URL utilityURL;
        try { utilityURL = output.toURI().toURL(); } catch (final MalformedURLException e) { throw new RuntimeException(e); }
        final Method getClassLoader = JavaPlugin.class.getDeclaredMethod("getClassLoader");
        getClassLoader.setAccessible(true);
        final PluginClassLoader loader = (PluginClassLoader) getClassLoader.invoke(this);
        loader.addURL(utilityURL);
        return utilityURL;
    }

}
