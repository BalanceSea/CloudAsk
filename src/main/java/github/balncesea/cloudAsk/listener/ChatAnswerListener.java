package github.balncesea.cloudAsk.listener;

import github.balncesea.cloudAsk.CloudAsk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class ChatAnswerListener implements Listener {
    private final CloudAsk plugin;

    public ChatAnswerListener(CloudAsk plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.questions().handleChatAnswer(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }
}
