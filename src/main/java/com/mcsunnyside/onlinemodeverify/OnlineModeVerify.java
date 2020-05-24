package com.mcsunnyside.onlinemodeverify;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class OnlineModeVerify extends JavaPlugin implements Listener {
    private Cache<UUID, Boolean> onlineModeCaching = CacheBuilder.newBuilder().initialCapacity(1000)
            .expireAfterAccess(7, TimeUnit.DAYS).build();
    private String NOT_PREMIUM_PLAYER_DECIDE_MESSAGE;
    private String MOJANG_API_DOWN;

    @Override
    public void onEnable() {
        // Plugin startup logic
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        this.parseColours(getConfig());
        this.NOT_PREMIUM_PLAYER_DECIDE_MESSAGE = getConfig().getString("messages.not-premium-player", "err");
        this.MOJANG_API_DOWN = getConfig().getString("messages.mojang-api-down", "err");
        getLogger().info("OnlineMode Verify now loaded.");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST) //最后处理事件，给避免在攻击保护插件之前处理数据
    public void onJoin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        Boolean caches = onlineModeCaching.getIfPresent(uuid);

        if (caches == null) {
            if (isOfflineUUID(uuid, name)) { //本地预处理，避免过多请求MOJANG API
                caches = false;
            } else {
                try {
                    caches = this.isOnlineModePlayer(uuid);
                } catch (IOException ioException) {
                    event.setKickMessage(MOJANG_API_DOWN);
                    event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
                    getLogger().warning("Cannot contact with mojang api: " + ioException.getMessage());
                    return;
                }
            }
            onlineModeCaching.put(uuid, caches);
        }

        if (!caches) {
            event.setKickMessage(NOT_PREMIUM_PLAYER_DECIDE_MESSAGE);
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        }
    }

    private boolean isOnlineModePlayer(UUID uuid) throws IOException {
        int code = HttpRequest.get(new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", "")))
                .getResponseCode();
        if (code == 200) {
            return true;
        }
        if (code == 204) {
            return false;
        }
        throw new IOException("Status code returns an unknown value: " + code);
    }

    private boolean isOfflineUUID(UUID uuid, String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).equals(uuid);
    }

    /**
     * Parse colors for the YamlConfiguration.
     *
     * @param config yaml config
     */
    private void parseColours(FileConfiguration config) {
        Set<String> keys = config.getKeys(true);
        for (String key : keys) {
            String filtered = config.getString(key);
            if (filtered == null) {
                continue;
            }
            if (filtered.startsWith("MemorySection")) {
                continue;
            }
            filtered = ChatColor.translateAlternateColorCodes('&', filtered);
            config.set(key, filtered);
        }
    }
}
