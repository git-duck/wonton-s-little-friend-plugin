package com.example.friendplugin;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FriendPlugin extends JavaPlugin implements CommandExecutor {

    // Store friends list for each player in memory using their Minecraft UUID
    private final Map<UUID, List<String>> friendsData = new HashMap<>();

    @Override
    public void onEnable() {
        // Register the /friend command
        if (this.getCommand("friend") != null) {
            this.getCommand("friend").setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Ensure the command is executed by a player in-game
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use friend commands!");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        // Ensure the player has an active friends list in memory
        friendsData.putIfAbsent(playerUUID, new ArrayList<>());
        List<String> friends = friendsData.get(playerUUID);

        // Executed: /friend
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Available commands:");
            player.sendMessage(ChatColor.YELLOW + "/friend add <username>");
            player.sendMessage(ChatColor.YELLOW + "/friend list");
            player.sendMessage(ChatColor.YELLOW + "/friend remove <username>");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            // Executed: /friend add <username>
            case "add":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend add <username>");
                    return true;
                }
                String targetAdd = args[1];
                if (targetAdd.equalsIgnoreCase(player.getName())) {
                    player.sendMessage(ChatColor.RED + "You cannot add yourself as a friend!");
                    return true;
                }
                if (friends.contains(targetAdd)) {
                    player.sendMessage(ChatColor.RED + targetAdd + " is already on your friends list.");
                } else {
                    friends.add(targetAdd);
                    player.sendMessage(ChatColor.GREEN + "Added " + targetAdd + " to your friends list!");
                }
                break;

            // Executed: /friend list
            case "list":
                if (friends.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "Your friends list is empty.");
                } else {
                    for (String friendName : friends) {
                        player.sendMessage(ChatColor.WHITE + friendName);
                    }
                }
                break;

            // Executed: /friend remove <username>
            case "remove":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend remove <username>");
                    return true;
                }
                String targetRemove = args[1];
                if (friends.contains(targetRemove)) {
                    friends.remove(targetRemove);
                    player.sendMessage(ChatColor.GREEN + "Removed " + targetRemove + " from your friends list.");
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
}
