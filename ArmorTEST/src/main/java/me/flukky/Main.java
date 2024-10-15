package me.flukky;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.StringUtil;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

public class Main extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private HashMap<Player, ItemStack> equippedArmor = new HashMap<>(); // บันทึกเกราะที่ผู้เล่นสวมใส่
    private HashMap<Player, Double> defaultHealth = new HashMap<>(); // เก็บค่าหัวใจเริ่มต้นของผู้เล่น
    private List<String> godItems; // สำหรับเก็บรายการจาก config

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig(); // โหลดไฟล์ config.yml เริ่มต้น

        this.getCommand("givecustomarmor").setExecutor(this); // ตั้งค่า command ให้เสกไอเท็ม
        loadGodItems(); // โหลดรายการจาก config
    }

    // สร้าง ItemStack เกราะตามชื่อ
    public ItemStack createCustomArmor(String name) {
        if (getConfig().contains("armorAbilities." + name)) {
            Material material = Material.valueOf(getConfig().getString("armorAbilities." + name + ".material"));
            ItemStack armor = new ItemStack(material);
            ItemMeta meta = armor.getItemMeta();
    
            if (meta != null) {
                // สร้าง AttributeModifier สำหรับ Armor และ Toughness
                int armorValue = getConfig().getInt("armorAbilities." + name + ".armor", 0);
                int toughnessValue = getConfig().getInt("armorAbilities." + name + ".toughness", 0);
    
                // สร้าง AttributeModifier สำหรับเกราะ
                AttributeModifier armorModifier = new AttributeModifier(UUID.randomUUID(), "generic.armor", armorValue,
                        AttributeModifier.Operation.ADD_NUMBER);
                AttributeModifier toughnessModifier = new AttributeModifier(UUID.randomUUID(),
                        "generic.armor_toughness", toughnessValue, AttributeModifier.Operation.ADD_NUMBER);
    
                // ตั้งชื่อเกราะ
                meta.setDisplayName(name);
                meta.setCustomModelData(getConfig().getInt("armorAbilities." + name + ".customModelData"));
    
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                // เพิ่ม AttributeModifier ไปยัง ItemMeta
                meta.addAttributeModifier(Attribute.GENERIC_ARMOR, armorModifier);
                meta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, toughnessModifier);
    
                // ซ่อนค่าของ UI เกราะ
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    
                List<String> lore = new ArrayList<>(getConfig().getStringList("armorAbilities." + name + ".lore"));
                lore.add("");
    
                // ตรวจสอบว่าเป็นเกราะหรือไม่
                if (material.name().endsWith("_HELMET") || material.name().endsWith("_CHESTPLATE") ||
                        material.name().endsWith("_LEGGINGS") || material.name().endsWith("_BOOTS")) {
                    // ตรวจสอบประเภทของเกราะ
                    if (material.name().endsWith("_HELMET")) {
                        lore.add(ChatColor.GRAY + "When on Head:");
                    } else if (material.name().endsWith("_CHESTPLATE")) {
                        lore.add(ChatColor.GRAY + "When on Body:");
                    } else if (material.name().endsWith("_LEGGINGS")) {
                        lore.add(ChatColor.GRAY + "When on Legs:");
                    } else if (material.name().endsWith("_BOOTS")) {
                        lore.add(ChatColor.GRAY + "When on Feet:");
                    }
    
                    lore.add(ChatColor.BLUE + "+" + armorValue + " Armor");
                    lore.add(ChatColor.BLUE + "+" + toughnessValue + " Armor Toughness");
                } else {
                    // ถ้าไม่ใช่เกราะ แสดงข้อความอื่น
                    lore.add(ChatColor.GRAY + "Effect when used:");
                    lore.add(ChatColor.BLUE + "Custom effect description for " + name);
                }
                meta.setLore(lore);
    
                armor.setItemMeta(meta); // ตั้งค่า ItemMeta ให้กับ ItemStack
            }
            return armor;
        }
        return null; // ถ้าไม่พบเกราะ
    }
    

    @EventHandler
    public void onArmorChange(InventoryClickEvent event) {

        ItemStack currentItem = event.getCurrentItem(); // เกราะที่ถูกถอด
        ItemStack cursorItem = event.getCursor(); // เกราะที่สวมใส่

        Player player = (Player) event.getWhoClicked();

        boolean isWearingArmor = true; // ตัวแปรเพื่อเช็คว่ากำลังสวมใส่เกราะอยู่หรือไม่
        boolean isRemovingArmor = false; // ตัวแปรเพื่อเช็คว่ากำลังถอดเกราะอยู่หรือไม่

        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {

            // ตรวจสอบว่าเกราะเป็นเกราะที่เราสร้างขึ้นเองหรือไม่
            if (currentItem != null && isCustomArmor(currentItem)) {
                isRemovingArmor = true; // ตั้ง flag ว่ากำลังถอดเกราะ
                removeArmorAbility(player, currentItem); // ลบความสามารถเมื่อถอดเกราะ
                equippedArmor.remove(player); // ลบเกราะจาก HashMap
            }

            if (cursorItem != null && isCustomArmor(cursorItem)) {
                isWearingArmor = true; // ตั้ง flag ว่ากำลังใส่เกราะ
                applyArmorAbility(player, cursorItem); // เพิ่มความสามารถเมื่อสวมใส่เกราะ
                equippedArmor.put(player, cursorItem); // บันทึกเกราะลง HashMap
            }
        }

        // เช็คว่าเป็นการคลิก Shift
        if (event.isShiftClick()) {
            // หากกำลังถอดเกราะ
            if (isRemovingArmor) {
                if (cursorItem != null && isCustomArmor(cursorItem)) {
                    removeArmorAbility(player, cursorItem); // ลบความสามารถเมื่อถอดเกราะ
                    isWearingArmor = true;
                    return; // ออกจากเมธอด
                }
                return;
            }

            // หากกำลังใส่เกราะ
            if (isWearingArmor) {
                if (currentItem != null && isCustomArmor(currentItem)) {
                    applyArmorAbility(player, currentItem); // เพิ่มความสามารถเมื่อใส่เกราะ
                    isWearingArmor = false;
                    return; // ออกจากเมธอด
                }
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ItemStack armor = equippedArmor.get(player); // โหลดเกราะจาก HashMap

        if (armor != null && isCustomArmor(armor)) {
            applyArmorAbility(player, armor); // เพิ่มความสามารถเมื่อเข้าสู่เซิร์ฟเวอร์
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (item != null && isCustomArmor(item)) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                // applyArmorAbility(player, item); // เพิ่มความสามารถเมื่อสวมใส่เกราะ
                player.sendMessage(ChatColor.RED + "การใส่เกราะแบบนี้ (คลิ๊กขวา) จะไม่ได้รับ Ability โดยตรง");
            }
        }
    }

    // เช็คว่าไอเท็มเป็นเกราะใหม่ที่เราสร้างหรือไม่
    private boolean isCustomArmor(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String itemName = item.getItemMeta().getDisplayName();
            return getConfig().contains("armorAbilities." + itemName); // ตรวจสอบชื่อเกราะจาก config
        }
        return false;
    }

    // ฟังก์ชันสำหรับเพิ่มความสามารถพิเศษให้กับผู้เล่น
    private void applyArmorAbility(Player player, ItemStack item) {

        // เก็บค่าเริ่มต้นของหัวใจถ้ายังไม่ถูกเก็บ
        if (!defaultHealth.containsKey(player)) {
            defaultHealth.put(player, player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue());
        }

        String armorName = item.getItemMeta().getDisplayName();

        if (getConfig().contains("armorAbilities." + armorName + ".abilities")) {
            List<Map<?, ?>> abilitiesConfig = getConfig().getMapList("armorAbilities." + armorName + ".abilities");

            for (Map<?, ?> ability : abilitiesConfig) {
                // ตรวจสอบว่า ability เป็น Map ที่มีคีย์ "name", "duration", "amplifier"
                if (ability.containsKey("name") && ability.containsKey("duration")
                        && ability.containsKey("amplifier")) {
                    String abilityName = (String) ability.get("name");

                    if ("EXTRA_HEARTS".equals(abilityName)) {
                        int amount = (int) ability.get("amount");
                        addExtraHearts(player, amount);
                    }

                    int duration = ((Number) ability.get("duration")).intValue();
                    int amplifier = ((Number) ability.get("amplifier")).intValue();

                    PotionEffectType effectType = PotionEffectType.getByName(abilityName);
                    if (effectType != null) {
                        player.addPotionEffect(new PotionEffect(effectType, duration * 20, amplifier));
                        player.sendMessage("คุณได้รับความสามารถพิเศษจาก " + armorName + ": " + abilityName);
                    }
                } else {
                    getLogger().warning("Ability entry is missing required fields: " + ability);
                }
            }
        }
    }

    private void addExtraHearts(Player player, int amount) {
        // เพิ่มหัวใจให้กับผู้เล่น
        AttributeInstance health = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (health != null) {
            double newHealth = health.getBaseValue() + (amount * 2); // 1 หัวใจ = 2 HP
            health.setBaseValue(newHealth);
        }
    }

    // ฟังก์ชันสำหรับลบความสามารถพิเศษเมื่อถอดเกราะ
    private void removeArmorAbility(Player player, ItemStack item) {
        String armorName = item.getItemMeta().getDisplayName();

        if (getConfig().contains("armorAbilities." + armorName + ".abilities")) {
            List<Map<?, ?>> abilitiesConfig = getConfig().getMapList("armorAbilities." + armorName + ".abilities");

            for (Map<?, ?> ability : abilitiesConfig) {
                // ตรวจสอบว่า ability เป็น Map ที่มีคีย์ "name"
                if (ability.containsKey("name")) {
                    String abilityName = (String) ability.get("name");

                    // คืนค่าหัวใจให้ผู้เล่น
                    if ("EXTRA_HEARTS".equals(abilityName)) {
                        if (defaultHealth.containsKey(player)) {
                            double originalHealth = defaultHealth.get(player);
                            player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(originalHealth);
                            defaultHealth.remove(player); // ลบค่าเริ่มต้นหลังจากคืนค่า
                        }
                    }

                    PotionEffectType effectType = PotionEffectType.getByName(abilityName);
                    if (effectType != null) {
                        player.removePotionEffect(effectType);
                        player.sendMessage("คุณสูญเสียความสามารถพิเศษจาก " + armorName + ": " + abilityName);
                    }
                } else {
                    getLogger().warning("Ability entry is missing required fields: " + ability);
                }
            }
        }
    }

    private void loadGodItems() {
        // ดึงรายการเกราะทั้งหมดจาก config
        godItems = new ArrayList<>();
        for (String key : getConfig().getConfigurationSection("armorAbilities").getKeys(false)) {
            godItems.add(key); // เพิ่มชื่อเกราะในลิสต์
        }
    }

    // การจัดการคำสั่ง /givecustomarmor
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("givecustomarmor")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                if (args.length == 1) {
                    String armorName = args[0]; // ชื่อเกราะจาก args

                    // ตรวจสอบว่าชื่อเกราะอยู่ใน config หรือไม่
                    if (getConfig().contains("armorAbilities." + armorName)) {
                        ItemStack customArmor = createCustomArmor(armorName);

                        player.getInventory().addItem(customArmor); // เสกเกราะให้ผู้เล่น
                        player.sendMessage("คุณได้รับ " + armorName);
                    } else {
                        player.sendMessage("ไม่มีเกราะชื่อ " + armorName + " ใน config.yml");
                    }
                } else {
                    player.sendMessage("โปรดระบุชื่อเกราะในคำสั่ง /givecustomarmor <armorName>");
                }
            }
            return true;
        }
        return false;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        final List<String> results = new ArrayList<>();

        // สำหรับ args[0] (อาจจะเป็นคำสั่งหรือการเลือกไอเทม)
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], godItems, results); // ใช้ godItems ที่โหลดจาก config
            Collections.sort(results);
            return results;
        }

        // สำหรับ args[1] (ผู้เล่น)
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            names.add("@a"); // ตัวเลือกที่ให้เลือกผู้เล่นทุกคน

            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName()); // เพิ่มชื่อผู้เล่นออนไลน์
            }
            StringUtil.copyPartialMatches(args[1], names, results);
            Collections.sort(results);
            return results;
        }

        return Collections.emptyList(); // ถ้าไม่มีค่าที่จะเติม
    }
}
