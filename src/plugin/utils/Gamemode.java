package plugin.utils;

public enum Gamemode {
    idk(),
    survival(false, "survival", "srv!", 5, 3, 150),
    sandbox(true, "sandbox", "snd!", 0, 1, 0),
    attack(false, "attack", "atk!", 0, 1, 200),
    pvp(false, "pvp", "pvp!", 0, 1, 300),
    campaign(false, "campaign", "cmp!", 3, 2, 100),
    tdefense(false, "tower defense", "td!", 5, 1, 150),
    hub(true, "hub", "hb!", 0, 0, 0);

    public boolean optimized;
    public String simpleName, botPrefix;
    public int waveCost, blockCost, winCost;

    Gamemode() {
        this.optimized = false;
        this.simpleName = name();
        this.botPrefix = "idk!";
        this.waveCost = 0;
        this.blockCost = 0;
        this.winCost = 0;
    }

    Gamemode(boolean optimized, String simpleName, String botPrefix, int waveCost, int blockCost, int winCost) {
        this.optimized = optimized;
        this.simpleName = simpleName;
        this.botPrefix = botPrefix;
        this.blockCost = blockCost;
        this.waveCost = waveCost;
        this.winCost = winCost;
    }

    public static Gamemode parseGamemode(String name) {
        for (Gamemode g : values())
            if (g.name().equals(name)) return g;
        return idk;
    }
}
