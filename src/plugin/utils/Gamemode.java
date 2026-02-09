package plugin.utils;

public enum Gamemode {
    idk(),
    survival(false, "survival", "srv!"),
    sandbox(true, "sandbox", "snd!"),
    attack(false, "attack", "atk!");

    public boolean optimized;
    public String simpleName, botPrefix;

    Gamemode() {
        this.optimized = false;
        this.simpleName = name();
        this.botPrefix = "idk!";
    }

    Gamemode(boolean optimized, String simpleName, String botPrefix) {
        this.optimized = optimized;
        this.simpleName = simpleName;
        this.botPrefix = botPrefix;
    }

    public static Gamemode parseGamemode(String name) {
        for(Gamemode g : values())
            if(g.name().equals(name)) return g;
        return idk;
    }
}
