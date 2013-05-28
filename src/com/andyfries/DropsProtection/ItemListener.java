package com.andyfries.DropsProtection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ItemListener implements Listener {
    private DropsProtection plugin;
    private final Logger log = Logger.getLogger("Minecraft");
    private final int timeProtected;
    private boolean enabled;
    private String deniedMsg;

    public ItemListener(DropsProtection plugin){
        this.plugin = plugin;
        timeProtected = plugin.config.getInt("DropsProtection.Protection time", 0);
        enabled = plugin.config.getBoolean("DropsProtection.Enabled", false);
        deniedMsg = plugin.config.getString("DropsProtection.Pickup denied message");
    }

    @EventHandler
    public void onItemPickup(PlayerPickupItemEvent event){
        if (!enabled){
            return;
        }

        ItemStack itemStack = event.getItem().getItemStack();
        ItemMeta info = event.getItem().getItemStack().getItemMeta();
        Player player = event.getPlayer();

        //log.info("here");

        if (info.hasLore()){
            //only interested in items with metadata
            List<String> lore = info.getLore();
            if (lore.contains("DP-protected")){
                //check if player owns item, has bypass permission, or protection time has expired
                String owner = "";
                for (String s : lore){
                    if (s.startsWith("DP-owner: ")){
                        owner = s.split(" ")[1];
                    }
                }

                int timeLeft = timeProtected - event.getItem().getTicksLived()/20;
                if (!owner.equals(player.getName()) && !player.hasPermission("dropsprotection.bypass") && (timeLeft > 0)){
                    //player trying to pick up item doesn't own it
                    if (event.getItem().getTicksLived()%20 == 0){
                        //send player a message every second
                        String deniedMsgModified = deniedMsg.replace("$owner", owner);
                        deniedMsgModified = deniedMsgModified.replace("$timeLeft", String.valueOf(timeLeft));
                        deniedMsgModified = deniedMsgModified.replace("$itemName", event.getItem().getItemStack().getType().name().replace("_", " ").toLowerCase());
                        player.sendMessage(deniedMsgModified);
                    }
                    event.setCancelled(true);
                }
                else {
                    //allow pickup and remove metadata
                    lore.remove("DP-protected");
                    lore.remove("DP-owner: " + owner);
                    info.setLore(lore);
                    itemStack.setItemMeta(info);
                    player.getInventory().addItem(itemStack);
                }
            }
        }
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event){
        if (!enabled){
            return;
        }

        final Item item = event.getEntity();
        final Location loc = event.getLocation();
        ItemMeta info = item.getItemStack().getItemMeta();


        //check if item is despawning before protection runs out and it is protected
        if (event.getEntity().getTicksLived()/20 < timeProtected && info.hasLore()){
            if (info.getLore().contains("protected")){
                //time left until protection runs out
                int waitTime = (timeProtected*20) - (event.getEntity().getTicksLived());

                //cancel event and call again when protection runs out
                event.setCancelled(true);

                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                    public void run() {
                        ItemDespawnEvent nextEvent = new ItemDespawnEvent(item, loc);
                        Bukkit.getServer().getPluginManager().callEvent(nextEvent);
                    }
                }, waitTime);
            }
        }
    }

    private void addProtection(List<ItemStack> items, String playerName){
        for (ItemStack itemStack : items){
            ItemMeta info = itemStack.getItemMeta();
            List<String> lore;

            if (info.hasLore()){
                lore = info.getLore();
            }
            else {
                lore = new ArrayList<String>();
            }
            lore.add("DP-protected");
            lore.add("DP-owner: " + playerName);
            info.setLore(lore);
            itemStack.setItemMeta(info);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event){
        if (!enabled){
            return;
        }

        if (!(event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent)){
            //only interested in deaths caused by entities
            return;
        }

        Entity killer = event.getEntity().getKiller();
        DamageCause damageCause = event.getEntity().getLastDamageCause().getCause();
        EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) event.getEntity().getLastDamageCause();
        killer = damageEvent.getDamager();

        if (killer instanceof Player){
            //entity killed by player
            Player player = (Player) killer;
            addProtection(event.getDrops(), player.getName());
        }
        else if ((killer instanceof Tameable && ((Tameable) killer).getOwner() instanceof Player)){
            //entity killed by player's pet
            Player player = ((Player) ((Tameable) killer).getOwner()).getPlayer();
            addProtection(event.getDrops(), player.getName());
        }
    }
}
