package me.z1haze.potionstacker;

import me.z1haze.commons.util.ChatUtils;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class PotionStacker extends JavaPlugin {

    private static final String PREFIX = "&f&l[&5&lPotionStacker&f&l]&r";
    private static final int BSTATS_ID = 34784;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        ChatUtils.init(this, PREFIX);

        PotCommand potCommand = new PotCommand(this);
        PluginCommand command = getCommand("pot");
        command.setExecutor(potCommand);
        command.setTabCompleter(potCommand);

        new Metrics(this, BSTATS_ID);
    }

    /**
     * A message from config.yml, uncolored and with placeholders still in it.
     */
    public String getMessage(String key) {
        return getConfig().getString("messages." + key, "&cMissing message: " + key);
    }

    public void sendMessage(CommandSender sender, String key) {
        sendMessage(sender, key, Map.of());
    }

    public void sendMessage(CommandSender sender, String key, Map<String, String> placeholders) {
        String message = getMessage(key);
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            message = message.replace(placeholder.getKey(), placeholder.getValue());
        }
        ChatUtils.sendColorized(sender, PREFIX + " " + message);
    }
}
