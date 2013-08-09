package edgruberman.bukkit.doorman;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.joda.time.Days;
import org.joda.time.LocalDate;

import edgruberman.bukkit.doorman.messaging.Message;
import edgruberman.bukkit.doorman.messaging.Recipients;

/** delivers player join messages */
public final class Doorman implements Listener, Runnable {

    private static final long TICKS_PER_SECOND = 20;

    private final Plugin plugin;
    private final RecordKeeper records;
    private final long grace;
    private final List<MessageSwitch> headers;
    private final List<MessageSwitch> arguments;
    private final LocalDate worldStart;
    private final Map<String, Long> lastDeclaration = new HashMap<String, Long>();

    Doorman(final Plugin plugin, final RecordKeeper records, final long grace, final List<MessageSwitch> headers, final List<MessageSwitch> arguments, final Date worldStart) {
        this.plugin = plugin;
        this.records = records;
        this.grace = grace;
        this.headers = headers;
        this.arguments = arguments;
        this.worldStart = LocalDate.fromDateFields(worldStart);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerJoin(final PlayerJoinEvent join) {
        Message message = null;
        final Player target = join.getPlayer();

        // headers - always displayed, each on separate line
        for (final MessageSwitch header : this.headers) {
            final String value = header.valueFor(target);
            if (value.toString().length() == 0) continue;

            final Message prefix = Main.courier.draft("{1}\n", value);
            if (message == null) { message = prefix; } else { message.append(prefix); }
        }

        // greeting - always displayed
        final int worldAge = Days.daysBetween(this.worldStart, LocalDate.now()).getDays();
        long serverSize = 0; for (final World world : Bukkit.getWorlds()) serverSize += Doorman.directorySize(world.getWorldFolder());
        final List<Object> args = new ArrayList<Object>();
        args.add(worldAge);
        args.add(Doorman.readableFileSize(serverSize));
        for (final MessageSwitch argument : this.arguments) args.add(argument.valueFor(target));
        final Message greeting = Main.courier.compose("greeting", args.toArray());
        if (message == null) { message = greeting; } else { message.append(greeting); }

        // declaration - do not show if player has already received this message in the last grace period
        if (this.records.getHistory().size() > 0) {
            final Long last = this.lastDeclaration.get(target.getName());
            if ((last == null) || ((System.currentTimeMillis() - last) > this.grace)) {
                message.append(this.records.declare(target));
                this.updateLast(target.getName());
            }
        }

        // missed - excluding the declaration just sent, check if at least the previous one was missed
        if (this.records.getHistory().size() > 1 && target.getLastPlayed() < this.records.getHistory().get(1).set) {
            message.append(Main.courier.compose("missed"));
        }

        Main.courier.submit(Recipients.Sender.create(target), message);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        // Run a clean-up task when the grace period has expired (In case someone joins and immediately crashes)
        // TODO figure a better way to do this to avoid large numbers of tasks building up
        Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, this, Doorman.convertMsToTicks(this.grace));
    }

    @Override
    public void run() {
        final long now = System.currentTimeMillis();
        final Iterator<Map.Entry<String, Long>> it = this.lastDeclaration.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<String, Long> entry = it.next();
            if ((now - entry.getValue()) >= this.grace) it.remove();
        }
    }

    public void clearLast() {
        this.lastDeclaration.clear();
    }

    public void updateLast(final String name) {
        this.lastDeclaration.put(name, System.currentTimeMillis());
    }

    private static long directorySize(final File directory) {
        long length = 0;
        for (final File file : directory.listFiles()) {
            if (file.isFile()) {
                length += file.length();
            } else {
                length += Doorman.directorySize(file);
            }
        }
        return length;
    }

    private static String readableFileSize(final long size) {
        if(size <= 0) return "0";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        final int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private static long convertMsToTicks(final long milliseconds) {
        return TimeUnit.MILLISECONDS.toSeconds(Math.max(0, milliseconds)) * Doorman.TICKS_PER_SECOND;
    }

}
