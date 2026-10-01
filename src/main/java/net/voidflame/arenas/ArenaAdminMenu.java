package net.voidflame.arenas;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArenaAdminMenu implements Listener {
    private static final String MAIN="§8VoidFlame §7• §bArena Admin";
    private static final String EDIT="§8VoidFlame §7• §bArena Editor";
    private final VoidFlameArenasPlugin plugin;
    private final ArenaManager manager;
    private final Map<UUID,String> inputs=new ConcurrentHashMap<>();

    public ArenaAdminMenu(VoidFlameArenasPlugin plugin,ArenaManager manager){this.plugin=plugin;this.manager=manager;}
    public void open(Player p){
        Inventory inv=Bukkit.createInventory(null,27,MAIN);fill(inv);
        int slot=0;for(Arena a:manager.all()){if(slot>=18)break;inv.setItem(slot++,item(Material.IRON_BARS,"§b"+a.name(),"§7World: §f"+a.world().getName(),"§7State: §f"+a.state().name(),"§dClick §8» §fEdit"));}
        inv.setItem(21,item(Material.EMERALD,"§a§lCreate Arena","§7Type the arena name in chat."));
        inv.setItem(22,item(Material.COMPASS,"§b§lReload","§7Reload arena configuration."));
        inv.setItem(18,item(Material.ARROW,"§7§lBack"));
        inv.setItem(26,item(Material.BARRIER,"§c§lClose"));
        p.openInventory(inv);
    }
    private void edit(Player p,String name){
        Inventory inv=Bukkit.createInventory(null,27,EDIT);fill(inv);
        inv.setItem(4,item(Material.IRON_BARS,"§b§lArena: §f"+name));
        inv.setItem(10,item(Material.ENDER_PEARL,"§b§lSpawn A","§7Set your current location."));
        inv.setItem(12,item(Material.ENDER_EYE,"§3§lSpawn B","§7Set your current location."));
        inv.setItem(14,item(Material.BOOK,"§e§lTemplate","§7Type template name in chat."));
        inv.setItem(16,item(Material.LEVER,"§a§lEnable"));
        inv.setItem(19,item(Material.REDSTONE_TORCH,"§c§lDisable"));
        inv.setItem(21,item(Material.TNT,"§6§lReset"));
        inv.setItem(23,item(Material.BARRIER,"§c§lDelete"));
        inv.setItem(18,item(Material.ARROW,"§7§lBack"));
        inv.setItem(26,item(Material.BARRIER,"§c§lClose"));
        p.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        String t=e.getView().getTitle();if(!t.equals(MAIN)&&!t.equals(EDIT))return;e.setCancelled(true);
        int s=e.getRawSlot();if(s<0||s>=27)return;
        if(s==26){p.closeInventory();return;}
        if(t.equals(MAIN)){
            if(s==21){begin(p,"CREATE");return;}
            if(s==22){if(manager.reload())p.sendMessage("§aArena configuration reloaded.");else p.sendMessage("§cReload blocked.");open(p);return;}
            if(s==18){p.closeInventory();return;}
            ItemStack x=e.getCurrentItem();if(x!=null&&x.getType()==Material.IRON_BARS&&x.getItemMeta()!=null)edit(p,org.bukkit.ChatColor.stripColor(x.getItemMeta().getDisplayName()));
            return;
        }
        String name=org.bukkit.ChatColor.stripColor(e.getInventory().getItem(4).getItemMeta().getDisplayName()).replace("Arena: ","").trim();
        switch(s){
            case 10->{if(manager.setSpawn(name,true,p.getLocation()))p.sendMessage("§aSpawn A set.");else p.sendMessage("§cCould not set Spawn A.");}
            case 12->{if(manager.setSpawn(name,false,p.getLocation()))p.sendMessage("§aSpawn B set.");else p.sendMessage("§cCould not set Spawn B.");}
            case 14->{inputs.put(p.getUniqueId(),"TEMPLATE|"+name);p.closeInventory();p.sendMessage("§eType the template name.");}
            case 16->{if(manager.setEnabled(name,true))p.sendMessage("§aArena enabled.");open(p);}
            case 19->{if(manager.setEnabled(name,false))p.sendMessage("§cArena disabled.");open(p);}
            case 21->{manager.find(name).ifPresent(a->manager.reset(a));p.sendMessage("§eArena reset started.");}
            case 23->{if(manager.delete(name))p.sendMessage("§aArena deleted.");open(p);}
            case 18->open(p);
            default->{}
        }
    }
    private void begin(Player p,String type){inputs.put(p.getUniqueId(),type);p.closeInventory();p.sendMessage("§eType the arena name in chat. §7Type §ccancel §7to abort.");}
    @EventHandler public void chat(AsyncPlayerChatEvent e){
        String type=inputs.remove(e.getPlayer().getUniqueId());if(type==null)return;e.setCancelled(true);
        String msg=e.getMessage().trim();Player p=e.getPlayer();if(msg.equalsIgnoreCase("cancel")){p.sendMessage("§7Cancelled.");open(p);return;}
        Bukkit.getScheduler().runTask(plugin,()->{
            if(type.equals("CREATE")){if(manager.create(msg,p.getWorld())){p.sendMessage("§aArena created.");open(p);}else p.sendMessage("§cArena already exists.");}
            else if(type.startsWith("TEMPLATE|")){String name=type.substring(9);if(manager.setTemplate(name,msg,p.getLocation()))p.sendMessage("§aTemplate set.");else p.sendMessage("§cCould not set template.");edit(p,name);}
        });
    }
    private void fill(Inventory inv){ItemStack x=item(Material.GRAY_STAINED_GLASS_PANE," ");for(int i=0;i<27;i++)inv.setItem(i,x.clone());}
    private ItemStack item(Material m,String n,String...l){ItemStack x=new ItemStack(m);ItemMeta meta=x.getItemMeta();if(meta!=null){meta.setDisplayName(n);meta.setLore(List.of(l));x.setItemMeta(meta);}return x;}
}