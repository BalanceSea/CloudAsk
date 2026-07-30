package github.balncesea.cloudAsk.service;

import github.balncesea.cloudAsk.model.NetworkEvent;
import github.balncesea.cloudAsk.model.Question;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class RewardService {
    private final JavaPlugin plugin;

    public RewardService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void execute(NetworkEvent event) {
        Question question = event.question();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("player", event.winnerName());
        values.put("uuid", event.winnerUuid());
        values.put("question", question.text());
        values.put("asker", question.askerName());
        values.put("server", event.winnerServer());

        for (String configuredCommand : question.rewardCommands()) {
            String command = MessageService.format(configuredCommand, values).trim();
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (!command.isBlank()) {
                boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                if (!success) {
                    plugin.getLogger().warning("奖励命令执行失败: " + command);
                }
            }
        }
    }
}
