package plugin.database.models;

public class PlayerPrefs {
    public boolean showWelcomeMenu = true;
    public String customName = "";

    public void setShowWelcomeMenu(boolean showWelcomeMenu) {
        this.showWelcomeMenu = showWelcomeMenu;
    }

    public void setCustomName(String s) {
        this.customName = s;
    }
}
