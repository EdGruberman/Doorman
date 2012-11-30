package edgruberman.bukkit.doorman.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import edgruberman.bukkit.doorman.Doorman;
import edgruberman.bukkit.doorman.Main;
import edgruberman.bukkit.doorman.RecordKeeper;

public final class Show implements CommandExecutor {

    private final Doorman doorman;
    private final RecordKeeper records;
    private final CommandExecutor declarationSet;

    public Show(final Doorman doorman, final RecordKeeper records, final CommandExecutor declarationSet) {
        this.doorman = doorman;
        this.records = records;
        this.declarationSet = declarationSet;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length >= 1)
            return this.declarationSet.onCommand(sender, command, label, args);

        if (this.records.getHistory().size() == 0) {
            Main.courier.send(sender, "no-history");
            return true;
        }

        this.records.declare(sender);
        this.doorman.updateLast(sender.getName());
        return true;
    }

}
