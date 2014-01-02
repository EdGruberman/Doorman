package edgruberman.bukkit.doorman;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

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
        this.putConfigMinimum("1.5.0");
        this.putConfigMinimum("language.yml", "1.5.0");

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
        Main.courier = ConfigurationCourier.Factory.create(this).setBase(this.loadConfig("language.yml")).setFormatCode("format-code").build();

        final ConfigurationSection switches = this.getConfig().getConfigurationSection("switches");
        final List<MessageSwitch> headers = this.parseSwitches(switches, "headers");
        final List<MessageSwitch> arguments = this.parseSwitches(switches, "arguments");

        Long grace = this.getConfig().getLong("declaration-grace", -1);
        if (grace != -1) grace = TimeUnit.SECONDS.toMillis(grace);

        Date worldStart;
        try {
            worldStart = Main.DATE_FORMAT.parse(this.getConfig().getString("world-start"));
        } catch (final ParseException e) {
            this.getLogger().log(Level.WARNING, "Unable to parse world-start: {0}; {1}", new Object[] { this.getConfig().getString("world-start"), e });
            worldStart = new Date();
        }

        final RecordKeeper records = new RecordKeeper(this);
        final Doorman doorman = new Doorman(this, records, grace, headers, arguments, worldStart);
        this.getLogger().log(Level.CONFIG, "History: {0}; Grace: {1}; Headers: {2}; Arguments: {3}; worldStart: {4}", new Object[] { records.getHistory().size(), grace, headers.size(), arguments.size(), worldStart });

        this.getCommand("doorman:history").setExecutor(new History(records));
        this.getCommand("doorman:show").setExecutor(new Show(doorman, records));
        this.getCommand("doorman:add").setExecutor(new Add(doorman, records));
        this.getCommand("doorman:edit").setExecutor(new Edit(doorman, records));
        this.getCommand("doorman:reload").setExecutor(new Reload(this));
    }

    private List<MessageSwitch> parseSwitches(final ConfigurationSection switches, final String path) {
        if (switches == null) return Collections.emptyList();

        final ConfigurationSection section = switches.getConfigurationSection(path);
        if (section == null) return Collections.emptyList();

        final List<MessageSwitch> result = new ArrayList<MessageSwitch>();

        for (final String name : section.getKeys(false)) {
            final ConfigurationSection entry = section.getConfigurationSection(name);
            final MessageSwitch ms = new MessageSwitch(entry.getString("permission"), entry.getString("true"), entry.getString("false"));
            result.add(ms);
        }

        return result;
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
        final URLClassLoader loader = (URLClassLoader) getClassLoader.invoke(this);

        final Method addURL = URLClassLoader.class.getDeclaredMethod("addURL");
        addURL.setAccessible(true);
        addURL.invoke(loader, utilityURL);

        return utilityURL;
    }

}
