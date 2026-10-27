package dev.powderpass.volleyball.hit;

/** Every way a player can touch the ball. */
public enum HitType {
    BUMP("Bump", false, false),
    DIG("Dig", false, false),
    SET("Set", false, false),
    QUICK_SET("Quick set", false, false),
    SPIKE("Spike", true, false),
    TIP("Tip", true, false),
    BLOCK("Block", false, false),
    /** The ball ran into a player without a deliberate touch. */
    BODY("Body touch", false, false),
    SERVE_FLOAT("Float serve", false, true),
    SERVE_TOPSPIN("Topspin serve", false, true),
    SERVE_JUMP("Jump serve", false, true),
    SERVE_BANANA("Banana serve", false, true),
    SERVE_UNDERHAND("Underhand serve", false, true);

    private final String displayName;
    private final boolean attack;
    private final boolean serve;

    HitType(String displayName, boolean attack, boolean serve) {
        this.displayName = displayName;
        this.attack = attack;
        this.serve = serve;
    }

    public String displayName() {
        return displayName;
    }

    /** Attack hit in the sense of the rules (directed toward the opponent, not a pass or set). */
    public boolean attack() {
        return attack;
    }

    public boolean serve() {
        return serve;
    }
}
