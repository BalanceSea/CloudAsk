package github.balncesea.cloudAsk;

import github.balncesea.cloudAsk.command.CloudAskCommand;
import github.balncesea.cloudAsk.listener.ChatAnswerListener;
import github.balncesea.cloudAsk.network.LocalBackend;
import github.balncesea.cloudAsk.network.NetworkBackend;
import github.balncesea.cloudAsk.network.RedisBackend;
import github.balncesea.cloudAsk.service.AutoQuestionPublisher;
import github.balncesea.cloudAsk.service.MessageService;
import github.balncesea.cloudAsk.service.QuestionService;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CloudAsk extends JavaPlugin {
    private QuestionService questionService;
    private MessageService messages;
    private AutoQuestionPublisher autoPublisher;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        startServices();

        CloudAskCommand commandHandler = new CloudAskCommand(this);
        PluginCommand command = Objects.requireNonNull(getCommand("cloudask"), "cloudask command missing");
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
        getServer().getPluginManager().registerEvents(new ChatAnswerListener(this), this);

        getLogger().info("CloudAsk 已启用，作者 MoutainSeaL，QQ 3643203568");
    }

    @Override
    public void onDisable() {
        if (autoPublisher != null) {
            autoPublisher.shutdown();
        }
        if (questionService != null) {
            questionService.shutdown();
        }
    }

    public void reloadPlugin() {
        if (autoPublisher != null) {
            autoPublisher.shutdown();
        }
        if (questionService != null) {
            questionService.shutdown();
        }
        reloadConfig();
        startServices();
    }

    private void startServices() {
        messages = new MessageService(this);
        String configuredServerId = getConfig().getString("server-id", "auto");
        String serverId = configuredServerId == null || configuredServerId.isBlank()
                || configuredServerId.equalsIgnoreCase("auto")
                ? "server-" + Bukkit.getPort()
                : configuredServerId.trim();

        String configuredMode = getConfig().getString("mode", "local");
        String mode = configuredMode == null ? "local" : configuredMode.toLowerCase(Locale.ROOT);
        NetworkBackend backend;
        if (mode.equals("redis")) {
            backend = new RedisBackend(this);
        } else {
            backend = new LocalBackend();
            if (!mode.equals("local")) {
                getLogger().warning("未知 mode '" + mode + "'，已使用 local 模式。");
            }
        }

        questionService = new QuestionService(this, backend, messages, serverId);
        questionService.start();
        autoPublisher = new AutoQuestionPublisher(this);
        autoPublisher.start();
        getLogger().info("问答后端模式: " + mode + "，服务器标识: " + serverId);
    }

    public QuestionService questions() {
        return questionService;
    }

    public MessageService messages() {
        return messages;
    }
}
