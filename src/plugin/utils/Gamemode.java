package plugin.utils;

public enum Gamemode {
    idk(),
    survival(false, "survival", "srv!", 5, 3),
    sandbox(true, "sandbox", "snd!", 0, 1),
    attack(false, "attack", "atk!", 0, 1),
    pvp(false, "pvp", "pvp!", 0, 1),
    campaign(false, "campaign", "cmp!", 3, 2);

    public boolean optimized;
    public String simpleName, botPrefix;
    public int waveCost, blockCost;

    Gamemode() {
        this.optimized = false;
        this.simpleName = name();
        this.botPrefix = "idk!";
        this.waveCost = 0;
        this.blockCost = 0;
    }

    Gamemode(boolean optimized, String simpleName, String botPrefix, int waveCost, int blockCost) {
        this.optimized = optimized;
        this.simpleName = simpleName;
        this.botPrefix = botPrefix;
        this.blockCost = blockCost;
        this.waveCost = waveCost;
    }

    public static Gamemode parseGamemode(String name) {
        for(Gamemode g : values())
            if(g.name().equals(name)) return g;
        return idk;
    }
}
