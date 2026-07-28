package com.example.wontonplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WontonPlugin extends JavaPlugin implements CommandExecutor, Listener {

    private final Map<UUID, List<String>> friendsData = new HashMap<>();

    @Override
    public void onEnable() {
        // Create config.yml if it doesn't exist
        saveDefaultConfig();
        loadFriends();

        // Register the /friend command
        if (this.getCommand("friend") != null) {
            this.getCommand("friend").setExecutor(this);
        }

        // Register events for join notifications
        getServer().getPluginManager().registerEvents(this, this);
        
        getLogger().info("WontonPlugin has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Save data when the server shuts down or restarts
        saveFriends();
        getLogger().info("WontonPlugin has saved all friends data.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use friend commands!");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        friendsData.putIfAbsent(playerUUID, new ArrayList<>());
        List<String> friends = friendsData.get(playerUUID);

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Available commands:");
            player.sendMessage(ChatColor.YELLOW + "/friend add <username>");
            player.sendMessage(ChatColor.YELLOW + "/friend list");
            player.sendMessage(ChatColor.YELLOW + "/friend remove <username>");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "add":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend add <username>");
                    return true;
                }
                
                String targetInput = args[1];
                
                // 1. Validate the player exists
                @SuppressWarnings("deprecation")
                OfflinePlayer targetOffline = Bukkit.getOfflinePlayer(targetInput);
                
                if (!targetOffline.hasPlayedBefore() && !targetOffline.isOnline()) {
                    player.sendMessage(ChatColor.RED + "That player has never joined this server!");
                    return true;
                }
                
                // Use their exact capitalization
                String actualName = targetOffline.getName() != null ? targetOffline.getName() : targetInput;

                if (actualName.equalsIgnoreCase(player.getName())) {
                    player.sendMessage(ChatColor.RED + "You cannot add yourself as a friend!");
                    return true;
                }
                
                // Check if they are already added (ignoring case)
                boolean alreadyAdded = friends.stream().anyMatch(f -> f.equalsIgnoreCase(actualName));
                
                if (alreadyAdded) {
                    player.sendMessage(ChatColor.RED + actualName + " is already on your friends list.");
                } else {
                    friends.add(actualName);
                    player.sendMessage(ChatColor.GREEN + "Added " + actualName + " to your friends list!");
                    saveFriends(); // Save immediately just in case of a server crash
                    
                    // 2. Notify the target if they are currently online
                    Player targetOnline = Bukkit.getPlayerExact(actualName);
                    if (targetOnline != null) {
                        targetOnline.sendMessage(ChatColor.GREEN + player.getName() + " has added you as a friend!");
                    }
                }
                break;

            case "list":
                if (friends.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "Your friends list is empty.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Your Friends:");
                    for (String friendName : friends) {
                        // Check if friend is online to display colored name
                        Player fPlayer = Bukkit.getPlayerExact(friendName);
                        if (fPlayer != null && fPlayer.isOnline()) {
                            player.sendMessage(ChatColor.GREEN + "- " + friendName + " (Online)");
                        } else {
                            player.sendMessage(ChatColor.GRAY + "- " + friendName + " (Offline)");
                        }
                    }
                }
                break;

            case "remove":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend remove <username>");
                    return true;
                }
                String targetRemove = args[1];
                
                // Remove ignoring case
                boolean removed = friends.removeIf(f -> f.equalsIgnoreCase(targetRemove));
                
                if (removed) {
                    player.sendMessage(ChatColor.GREEN + "Removed " + targetRemove + " from your friends list.");
                    saveFriends(); // Save immediately
                } else {
                    player.sendMessage(ChatColor.RED + targetRemove + " is not on your friends list.");
                }
                break;

            default:
                player.sendMessage(ChatColor.RED + "Invalid command. Type /friend for available commands.");
                break;
        }

        return true;
    }

    // --- 3. Join Notification Event ---
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joinedPlayer = event.getPlayer();
        String joinedName = joinedPlayer.getName();

        // Loop through all online players to see if they have the joining player added
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            List<String> theirFriends = friendsData.get(onlinePlayer.getUniqueId());
            if (theirFriends != null) {
                // If the online player's friend list contains the person who just joined
                if (theirFriends.stream().anyMatch(f -> f.equalsIgnoreCase(joinedName))) {
                    onlinePlayer.sendMessage(ChatColor.AQUA + "Your friend " + joinedName + " just joined the server!");
                }
            }
        }
    }

    // --- Data Saving & Loading Logic ---
    private void loadFriends() {
        if (getConfig().contains("friends")) {
            for (String uuidString : getConfig().getConfigurationSection("friends").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    List<String> friends = getConfig().getStringList("friends." + uuidString);
                    friendsData.put(uuid, new ArrayList<>(friends));
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Found invalid UUID in config: " + uuidString);
                }
            }
        }
    }

    private void saveFriends() {
        for (Map.Entry<UUID, List<String>> entry : friendsData.entrySet()) {
            getConfig().set("friends." + entry.getKey().toString(), entry.getValue());
        }
        saveConfig();
    }
}
