package dev.powderpass.volleyball.game;

import org.bukkit.Material;

/** Toggleable rules of a match. */
public enum Rule {
    FOOT_FAULT("foot-fault", "Foot fault", Material.LEATHER_BOOTS,
            "Server may not touch the end line or court", "when hitting (jump serves use the take-off spot)."),
    NET_TOUCH("net-touch", "Net touch", Material.COBWEB,
            "Touching the net between the antennas", "during a rally is a fault."),
    CENTER_LINE("center-line", "Centre line", Material.WHITE_CARPET,
            "Fully crossing the centre line into", "the opponent's court is a fault."),
    FOUR_HITS("four-hits", "Four hits", Material.CLOCK,
            "Max three team contacts (block not counted)."),
    DOUBLE_CONTACT("double-contact", "Double contact", Material.LEAD,
            "The same player may not touch twice in a row", "(a block does not count)."),
    ANTENNA("antenna", "Antennas", Material.RED_CANDLE,
            "Ball touching an antenna or crossing", "outside of them is out."),
    REACH_OVER("reach-over", "Reaching over", Material.STICK,
            "Attacking the ball on the opponent's side", "of the net is a fault (blocking is allowed)."),
    BACK_ROW_ATTACK("back-row-attack", "Back-row attack", Material.IRON_SWORD,
            "6v6 only: back-row players may not attack", "above the net from inside the attack line."),
    SERVE_BLOCK("serve-block", "Serve block", Material.SHIELD,
            "Blocking or attacking a serve is a fault."),
    SERVING_TEAM_TOUCH("serving-team-touch", "Serve screening", Material.BARRIER,
            "The serving team may not touch its own", "serve before it crosses the net."),
    TOSS_DROP("toss-drop", "Dropped toss", Material.EGG,
            "Letting the service toss fall untouched is a fault."),
    CEILING_OUT("ceiling-out", "Ceiling & objects", Material.GLASS,
            "Ball touching walls, ceiling or objects is out."),
    SERVE_DELAY("serve-delay", "Serve clock", Material.REPEATER,
            "The server must serve within the serve time."),
    AIM_ASSIST("aim-assist", "Aim assist", Material.SPYGLASS,
            "Passes go to the setter spot and sets go", "along the net automatically.");

    private final String key;
    private final String displayName;
    private final Material icon;
    private final String[] description;

    Rule(String key, String displayName, Material icon, String... description) {
        this.key = key;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public String[] description() {
        return description;
    }

    public static Rule byKey(String key) {
        for (Rule r : values()) {
            if (r.key.equalsIgnoreCase(key) || r.name().equalsIgnoreCase(key)) {
                return r;
            }
        }
        return null;
    }
}
