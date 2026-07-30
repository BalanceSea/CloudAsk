package github.balncesea.cloudAsk.service;

import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageService {
    private final JavaPlugin plugin;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void send(CommandSender sender, String key, Map<String, String> values) {
        sender.sendMessage(color(raw("prefix") + format(raw(key), values)));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void broadcast(String key, Map<String, String> values) {
        Bukkit.broadcastMessage(color(raw("prefix") + format(raw(key), values)));
    }

    public void broadcastQuestion(Map<String, String> values) {
        Bukkit.broadcastMessage(color(format(raw("question"), values)));
    }

    private String raw(String key) {
        return plugin.getConfig().getString("messages." + key, "&c缺少消息配置: " + key);
    }

    public static String format(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
