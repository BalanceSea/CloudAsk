package github.balncesea.cloudAsk.command;

import github.balncesea.cloudAsk.CloudAsk;
import github.balncesea.cloudAsk.util.QuestionParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class CloudAskCommand implements CommandExecutor, TabCompleter {
    private final CloudAsk plugin;

    public CloudAskCommand(CloudAsk plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            plugin.messages().send(sender, "usage");
            sender.sendMessage("§e/cloudask stop §7停止当前问题");
            sender.sendMessage("§e/cloudask status §7查看当前问题");
            sender.sendMessage("§e/cloudask reload §7重载配置");
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "ask" -> {
                if (!sender.hasPermission("cloudask.ask")) {
                    plugin.messages().send(sender, "no-permission");
                    return true;
                }
                Optional<QuestionParser.ParsedQuestion> parsed = QuestionParser.parse(args);
                if (parsed.isEmpty()) {
                    plugin.messages().send(sender, "usage");
                    return true;
                }
                plugin.questions().ask(sender, parsed.get().answer(), parsed.get().question());
            }
            case "stop" -> {
                if (!sender.hasPermission("cloudask.admin")) {
                    plugin.messages().send(sender, "no-permission");
                    return true;
                }
                plugin.questions().stop(sender);
            }
            case "status" -> plugin.questions().status(sender);
            case "reload" -> {
                if (!sender.hasPermission("cloudask.admin")) {
                    plugin.messages().send(sender, "no-permission");
                    return true;
                }
                plugin.reloadPlugin();
                plugin.messages().send(sender, "reloaded");
            }
            default -> plugin.messages().send(sender, "usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], List.of("ask", "stop", "status", "reload", "help"));
        }
        return List.of();
    }

    private static List<String> partial(String input, List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(input.toLowerCase(Locale.ROOT))) {
                result.add(value);
            }
        }
        return result;
    }
}
