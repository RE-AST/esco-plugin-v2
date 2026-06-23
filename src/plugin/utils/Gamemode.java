package plugin.utils;

public enum Gamemode {
    idk(),
    survival("survival", "srv!", 5, 3, 500),
    sandbox("sandbox", "snd!", 0, 1, 0),
    attack("attack", "atk!", 0, 1, 1000),
    pvp("pvp", "pvp!", 0, 1, 600),
    campaign("campaign", "cmp!", 3, 2, 300),
    tdefense("tower defense", "td!", 5, 1, 750),
    hub("hub", "hb!", 0, 0, 0),
    hexed("hexed", "hex!", 0, 2, 1050),
    crawlerArena("crw", "crw!", 20, 0, 1500);

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

    Gamemode(String simpleName, String botPrefix, int waveCost, int blockCost, int winCost) {
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
