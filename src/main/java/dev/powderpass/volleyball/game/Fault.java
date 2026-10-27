package dev.powderpass.volleyball.game;

/** Why a rally ended. */
public enum Fault {
    BALL_IN("Ball in"),
    BALL_OUT("Ball out"),
    OBJECT("Ball touched an object"),
    NET_TOUCH("Net touch"),
    CENTER_LINE("Centre line fault"),
    FOUR_HITS("Four hits"),
    DOUBLE_CONTACT("Double contact"),
    ANTENNA("Ball touched the antenna"),
    OUTSIDE_ANTENNA("Ball crossed outside the antenna"),
    UNDER_NET("Ball passed under the net"),
    REACH_OVER("Reaching over the net"),
    BACK_ROW_ATTACK("Back-row attack"),
    BACK_ROW_BLOCK("Back-row player blocked"),
    SERVE_BLOCK("Blocking or attacking the serve"),
    SERVING_TEAM_TOUCH("Serving team touched its own serve"),
    FOOT_FAULT("Foot fault"),
    TOSS_DROP("Service toss dropped"),
    SERVE_DELAY("Serve took too long"),
    REFEREE("Referee decision"),
    FORFEIT("Forfeit");

    private final String text;

    Fault(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
