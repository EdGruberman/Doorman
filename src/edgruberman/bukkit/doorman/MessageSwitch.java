package edgruberman.bukkit.doorman;

import org.bukkit.entity.Player;

public class MessageSwitch {

    private final String DEFAULT_VALUE = "";

    private final String permission;
    private final String valueTrue;
    private final String valueFalse;

    public MessageSwitch(final String permission, final String valueTrue, final String valueFalse) {
        this.permission = permission;
        this.valueTrue = ( valueTrue != null ? valueTrue : this.DEFAULT_VALUE );
        this.valueFalse = ( valueFalse != null ? valueFalse : this.DEFAULT_VALUE );
    }

    public String valueFor(final Player player) {
        return ( player.hasPermission(this.permission) ? this.valueTrue : this.valueFalse );
    }

}
