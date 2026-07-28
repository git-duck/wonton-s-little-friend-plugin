package com.example.wontonplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class WontonPlugin extends JavaPlugin implements CommandExecutor, Listener, TabCompleter {

    private final Map<UUID, List<String>> friendsData = new HashMap<>();
    
    // Tracks pending requests: Target UUID -> Set of Player Names who sent the request
    private final Map<UUID, Set<String>> pendingRequests = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadFriends();

        if (this.getCommand("friend") != null) {
            this.getCommand("friend").setExecutor(this);
            this.getCommand("friend").setTabCompleter(this);
        }

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("WontonPlugin has been enabled successfully with Mutual Requests and Tab Completion!");
    }

    @Override
    public void onDisable() {
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
            player.sendMessage(ChatColor.YELLOW + "/friend accept <username>");
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
                @SuppressWarnings("deprecation")
                OfflinePlayer targetOffline = Bukkit.getOfflinePlayer(targetInput);
                
                if (!targetOffline.hasPlayedBefore() && !targetOffline.isOnline()) {
                    player.sendMessage(ChatColor.RED + "That player has never joined this server!");
                    return true;
                }
                
                String actualName = targetOffline.getName() != null ? targetOffline.getName() : targetInput;

                if (actualName.equalsIgnoreCase(player.getName())) {
                    player.sendMessage(ChatColor.RED + "You cannot add yourself as a friend!");
                    return true;
                }
                
                boolean alreadyAdded = friends.stream().anyMatch(f -> f.equalsIgnoreCase(actualName));
                if (alreadyAdded) {
                    player.sendMessage(ChatColor.RED + actualName + " is already on your friends list.");
                    return true;
                }

                // Check if target is online to send a request
                Player targetOnline = Bukkit.getPlayerExact(actualName);
                if (targetOnline == null) {
                    player.sendMessage(ChatColor.RED + actualName + " must be online to receive a friend request.");
                    return true;
                }

                UUID targetUUID = targetOnline.getUniqueId();
                pendingRequests.putIfAbsent(targetUUID, new HashSet<>());
                Set<String> targetPending = pendingRequests.get(targetUUID);

                // Check if a request was already sent
                if (targetPending.contains(player.getName())) {
                    player.sendMessage(ChatColor.RED + "You have already sent a friend request to " + actualName + "!");
                    return true;
                }

                targetPending.add(player.getName());
                player.sendMessage(ChatColor.GREEN + "Friend request sent to " + actualName + "!");
                targetOnline.sendMessage(ChatColor.AQUA + player.getName() + " has sent you a friend request! Type /friend accept " + player.getName());
                break;

            case "accept":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend accept <username>");
                    return true;
                }

                String requesterName = args[1];
                UUID myUUID = player.getUniqueId();
                Set<String> myPending = pendingRequests.get(myUUID);

                if (myPending == null || !myPending.stream().anyMatch(name -> name.equalsIgnoreCase(requesterName))) {
                    player.sendMessage(ChatColor.RED + "You don't have a pending friend request from " + requesterName + ".");
                    return true;
                }

                // Find exact case of requester name
                String exactRequesterName = myPending.stream()
                        .filter(name -> name.equalsIgnoreCase(requesterName))
                        .findFirst()
                        .orElse(requesterName);

                // Remove from pending
                myPending.removeIf(name -> name.equalsIgnoreCase(requesterName));

                // Add to both players' friend lists (Mutual)
                friends.add(exactRequesterName);
                player.sendMessage(ChatColor.GREEN + "You are now friends with " + exactRequesterName + "!");

                Player requesterPlayer = Bukkit.getPlayerExact(exactRequesterName);
                if (requesterPlayer != null) {
                    UUID reqUUID = requesterPlayer.getUniqueId();
                    friendsData.putIfAbsent(reqUUID, new ArrayList<>());
                    friendsData.get(reqUUID).add(player.getName());
                    requesterPlayer.sendMessage(ChatColor.GREEN + player.getName() + " accepted your friend request!");
                } else {
                    // If they went offline, manually add to their stored list data safely
                    for (Map.Entry<UUID, List<String>> entry : friendsData.entrySet()) {
                        OfflinePlayer offReq = Bukkit.getOfflinePlayer(entry.getKey());
                        if (offReq.getName() != null && offReq.getName().equalsIgnoreCase(exactRequesterName)) {
                            entry.getValue().add(player.getName());
                        }
                    }
                }

                saveFriends();
                break;

            case "list":
                if (friends.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "Your friends list is empty.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Your Friends:");
                    for (String friendName : friends) {
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
                
                boolean removed = friends.removeIf(f -> f.equalsIgnoreCase(targetRemove));
                
                if (removed) {
                    player.sendMessage(ChatColor.GREEN + "Removed " + targetRemove + " from your friends list.");
                    
                    // Also remove requester reciprocally if desired
                    for (Map.Entry<UUID, List<String>> entry : friendsData.entrySet()) {
                        OfflinePlayer offP = Bukkit.getOfflinePlayer(entry.getKey());
                        if (offP.getName() != null && offP.getName().equalsIgnoreCase(targetRemove)) {
                            entry.getValue().removeIf(f -> f.equalsIgnoreCase(player.getName()));
                        }
                    }
                    
                    saveFriends();
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

    // --- Tab Completion Logic ---
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("add", "accept", "list", "remove");
            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("add") || sub.equals("remove")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            } else if (sub.equals("accept") && sender instanceof Player) {
                Player player = (Player) sender;
                Set<String> pending = pendingRequests.get(player.getUniqueId());
                if (pending != null) {
                    for (String name : pending) {
                        if (name.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(name);
                        }
                    }
                }
            }
        }

        return completions;
    }

    // --- Join Notification Event ---
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joinedPlayer = event.getPlayer();
        String joinedName = joinedPlayer.getName();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            List<String> theirFriends = friendsData.get(onlinePlayer.getUniqueId());
            if (theirFriends != null) {
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
