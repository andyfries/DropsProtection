package com.andyfries.DropsProtection;

import net.minecraft.server.v1_5_R3.NBTTagCompound;
import net.minecraft.server.v1_5_R3.NBTTagList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_5_R3.inventory.CraftItemStack;
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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ItemListener implements Listener {
    private DropsProtection plugin;
    private final Logger log = Logger.getLogger("Minecraft");
    private ItemStack lastProtected;

    public ItemListener(DropsProtection plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event){
        if (event.getItemDrop().getItemStack().getItemMeta().hasLore()){
            for (String s : event.getItemDrop().getItemStack().getItemMeta().getLore()){
                log.info(s);
            }
        }
        else {
            ItemStack is = event.getItemDrop().getItemStack();
            ItemMeta im = event.getItemDrop().getItemStack().getItemMeta();

            log.info(is.getData().toString());
            log.info(is.getTypeId() + "");
            log.info(is.getItemMeta().toString() + "");
            log.info(is.equals(new ItemStack(Material.ROTTEN_FLESH)) + "");

        }
    }

    @EventHandler
    public void onItemPickup(PlayerPickupItemEvent event){
        boolean enabled = plugin.config.getBoolean("DropsProtection.Enabled", false);
        int timeProtected = plugin.config.getInt("DropsProtection.Protection time", 0);
        String deniedMsg = plugin.config.getString("DropsProtection.Pickup denied message");
        ItemStack itemStack = event.getItem().getItemStack();

        ItemMeta info = event.getItem().getItemStack().getItemMeta();
        Player player = event.getPlayer();

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
                if (!owner.equals(player.getName()) && !player.hasPermission("dropsprotection.bypass") && (timeLeft > 0) && enabled){
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

                    if (lore.size() == 0){
                        //no lore, so just get a generic ItemMeta
                        log.info("removing lore");
                        info.setLore(null);
                        itemStack.setItemMeta(info);
                        NBTTagCompound tag = CraftItemStack.asNMSCopy(itemStack).getTag();
                        tag.remove("tag");
                    }
                    else {
                        //otherwise, just remove custom tags
                        info.setLore(lore);

                    }
                    //itemStack.setItemMeta(info);

                    //tag = CraftItemStack.asNMSCopy(lastProtected).getTag();
                    //log.info("last protected: " + tag.isEmpty());
                    //log.info("lore same: " + lastProtected.getItemMeta().getLore().equals(lore));


                    //player.getInventory().remove(itemStack);
                    //player.getInventory().addItem(new ItemStack(itemStack.getType(), originalAmount));
                }
            }
        }
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event){
        boolean enabled = plugin.config.getBoolean("DropsProtection.Enabled", false);
        int timeProtected = plugin.config.getInt("DropsProtection.Protection time", 0);

        final Item item = event.getEntity();
        final Location loc = event.getLocation();
        ItemMeta info = item.getItemStack().getItemMeta();

        //check if item is despawning before protection runs out and it is protected
        if (event.getEntity().getTicksLived()/20 < timeProtected && info.hasLore() && enabled){
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
            lastProtected = new ItemStack(itemStack.getTypeId(), itemStack.getAmount(), itemStack.getDurability(), itemStack.getData().getData());
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
        boolean enabled = plugin.config.getBoolean("DropsProtection.Enabled", false);

        if (!(event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent)){
            //only interested in deaths caused by entities
            return;
        }

        EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) event.getEntity().getLastDamageCause();
        Entity killer = damageEvent.getDamager();

        if (killer instanceof Player){
            //entity killed by player
            Player player = (Player) killer;
            if (enabled){
                addProtection(event.getDrops(), player.getName());
            }
        }
        else if ((killer instanceof Tameable && ((Tameable) killer).getOwner() instanceof Player)){
            //entity killed by player's pet
            Player player = ((Player) ((Tameable) killer).getOwner()).getPlayer();
            if (enabled){
                addProtection(event.getDrops(), player.getName());
            }
        }
    }
}
