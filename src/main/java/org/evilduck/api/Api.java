package org.evilduck.api;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Api extends JavaPlugin implements Listener {

    private String apiEndpoint;
    private String serverName;

    @Override
    public void onEnable() {
        loadConfig();
        Bukkit.getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::executeCommands, 0L, 200L);
    }

    private void loadConfig() {
        saveDefaultConfig();
        reloadConfig();
        apiEndpoint = getConfig().getString("api-endpoint");
        serverName = getConfig().getString("server-name");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("sendapi") && args.length > 0) {
            String message = String.join(" ", args);
            sendToApi("console", message);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("api") && args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            loadConfig();
            sender.sendMessage("Config reloaded successfully.");
            return true;
        }
        return false;
    }

    private void sendToApi(String playerName, String message) {
        try {
            String data = "{" +
                    "\"player\": \"" + playerName + "\", " +
                    "\"server\": \"" + serverName + "\", " +
                    "\"message\": \"" + message + "\"" +
                    "}";

            URL url = new URL(apiEndpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (var os = connection.getOutputStream()) {
                byte[] input = data.getBytes();
                os.write(input, 0, input.length);
            }

            connection.getResponseCode();
        } catch (Exception e) {
            getLogger().warning("Error sending data to API: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void executeCommands() {
        try {
            URL url = new URL(apiEndpoint + "/commands.json");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            JSONObject commandsData = new JSONObject(content.toString());
            if (!commandsData.has(serverName)) {
                getLogger().warning("No commands found for server: " + serverName);
                return;
            }
            JSONArray commands = commandsData.getJSONObject(serverName).getJSONArray("commands");

            for (int i = 0; i < commands.length(); i++) {
                String command = commands.getString(i);
                Bukkit.getScheduler().runTask(this, () -> {
                    try {
                        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
                        getLogger().info("Executed command: " + command);
                        removeCommandFromApi(command);
                    } catch (Exception e) {
                        getLogger().warning("Error executing command: " + command);
                        e.printStackTrace();
                    }
                });
            }
        } catch (Exception e) {
            getLogger().warning("Error fetching or executing commands: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void removeCommandFromApi(String command) {
        try {
            String data = "{" +
                    "\"server\": \"" + serverName + "\", " +
                    "\"command\": \"" + command + "\"" +
                    "}";

            URL url = new URL(apiEndpoint + "/cmd-update.php");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (var os = connection.getOutputStream()) {
                byte[] input = data.getBytes();
                os.write(input, 0, input.length);
            }

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                getLogger().info("Successfully removed command: " + command);
            } else {
                getLogger().warning("Failed to remove command: " + command);
            }
        } catch (Exception e) {
            getLogger().warning("Error sending command removal data to API: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sendToApi(event.getPlayer().getName(), "joined");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sendToApi(event.getPlayer().getName(), "left");
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        sendToApi(event.getPlayer().getName(), "moved to " + event.getPlayer().getWorld().getName());
    }
}
