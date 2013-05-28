package com.andyfries.DropsProtection;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.logging.Logger;

public class DropsProtection extends JavaPlugin {
    public PluginDescriptionFile pdfFile;
    private final Logger log = Logger.getLogger("Minecraft");
    public FileConfiguration config = new YamlConfiguration();
    private File configFile;

    public void onEnable(){
        //Get the information from the plugin.yml file.
        if (!this.getDataFolder().isDirectory()) {
            this.getDataFolder().mkdir();
        }

        configFile = new File(this.getDataFolder(), "config.yml");
        writeInitialConfig();
        loadConfig();

        getServer().getPluginManager().registerEvents(new ItemListener(this), this);
    }

    public void onDisable(){
        log.info(this.getName() + " disabled");
    }

    public void writeInitialConfig(){
        if (!configFile.exists()){
            configFile.getParentFile().mkdirs();
            copyResource(getResource("config.yml"), configFile);
        }
    }

    private void loadConfig(){
        try {
            config.options().copyDefaults(true);
            config.load(configFile);
        }
        catch (Exception e){
            log.info("Error reading config file.");
            e.printStackTrace();
        }
    }

    private void writeConfig(){
        try {
            config.save(configFile);
        }
        catch (IOException e){
            log.info("Error saving config file.");
            e.printStackTrace();
        }
    }

    private void copyResource(InputStream in, File out){
        try {
            OutputStream outStream = new FileOutputStream(out);
            byte[] buf = new byte[1024];
            int len;
            while((len=in.read(buf))>0){
                outStream.write(buf,0,len);
            }
            outStream.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args){
        if (cmd.getName().toLowerCase().equals("dp")){
            if (args.length == 1) {
                if (args[0].equals("reload")){
                    if (!sender.hasPermission("dropsprotection.reload")){
                        sender.sendMessage("You don't have dropsprotection.reload permission");
                    }
                    else {
                        sender.sendMessage("Config file reloaded");
                        loadConfig();
                    }
                    return true;
                }
                else if (args[0].equals("on")){
                    if (!sender.hasPermission("dropsprotection.toggle")){
                        sender.sendMessage("You don't have dropsprotection.toggle permission");
                    }
                    else {
                        sender.sendMessage("DropsProtection enabled");
                        config.set("DropsProtection.Enabled", true);
                        writeConfig();
                        loadConfig();
                    }
                    return true;
                }
                else if (args[0].equals("off")){
                    if (!sender.hasPermission("dropsprotection.toggle")){
                        sender.sendMessage("You don't have dropsprotection.toggle permission");
                    }
                    else {
                        sender.sendMessage("DropsProtection disable");
                        config.set("DropsProtection.Enabled", false);
                        writeConfig();
                        loadConfig();
                    }
                    return true;
                }
            }
            else if (args.length == 2){
                if (args[0].equals("settimer")){
                    if (!sender.hasPermission("dropsprotection.settimer")){
                        sender.sendMessage("You don't have dropsprotection.settimer permission");
                    }
                    else {
                        try {
                            config.set("DropsProtection.Protection time", Integer.parseInt(args[1]));
                            sender.sendMessage("Protection time set to " + args[1]);
                            writeConfig();
                            loadConfig();
                        }
                        catch (NumberFormatException e){
                            sender.sendMessage("Invalid number for protection time: " + args[1]);
                        }
                    }
                    return true;
                }
            }
            else if (args.length >= 2){
                if (args[0].equals("setmessage")){
                    if (!sender.hasPermission("dropsprotection.setmessage")){
                        sender.sendMessage("You don't have dropsprotection.setmessage permission");
                    }
                    else {
                        //concatenate all args into a single string
                        String message = "";
                        for (int i=1; i < args.length; ++i){
                            message += args[i] + " ";
                        }

                        //trim trailing space and set message
                        config.set("DropsProtection.Pickup denied message", message.substring(0, message.length()-1));
                        sender.sendMessage("Pickup denied message updated");
                        writeConfig();
                        loadConfig();
                    }
                    return true;
                }
            }
            else {
                sender.sendMessage("Commands:");
                sender.sendMessage("/dp [on/off]");
                sender.sendMessage("/dp reload");
                sender.sendMessage("/dp settimer [seconds]");
                sender.sendMessage("/dp setmessage [message]");
            }
        }
        return false;
    }
}
