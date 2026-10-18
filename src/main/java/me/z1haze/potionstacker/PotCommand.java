package me.z1haze.potionstacker;

import me.z1haze.potionstacker.StackPlanner.Drop;
import me.z1haze.potionstacker.StackPlanner.Group;
import me.z1haze.potionstacker.StackPlanner.Placement;
import me.z1haze.potionstacker.StackPlanner.Plan;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /pot} stacks the player's potions; {@code /pot reload} reloads the config.
 */
public final class PotCommand implements TabExecutor {

    private static final int MAX_STACK_SIZE_LIMIT = 99;

    private final PotionStacker plugin;

    public PotCommand(PotionStacker plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("potionstacker.reload")) {
                plugin.sendMessage(sender, "no-permission");
                return true;
            }

            plugin.reloadConfig();
            plugin.sendMessage(sender, "reloaded");
            return true;
        }

        if (args.length > 0) {
            plugin.sendMessage(sender, "usage", Map.of("{label}", label));
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "player-only");
            return true;
        }

        if (!player.hasPermission("potionstacker.use")) {
            plugin.sendMessage(player, "no-permission");
            return true;
        }

        stack(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("potionstacker.reload") && "reload".startsWith(args[0].toLowerCase())) {
            return List.of("reload");
        }
        return List.of();
    }

    private void stack(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();

        List<ItemStack> keys = new ArrayList<>();
        List<Integer> totals = new ArrayList<>();
        List<List<Integer>> slots = new ArrayList<>();
        List<Integer> emptySlots = new ArrayList<>();
        int potionCount = 0;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() == Material.AIR) {
                emptySlots.add(slot);
                continue;
            }
            if (!isStackable(item)) continue;

            ItemStack key = normalize(item);
            int index = indexOf(keys, key);
            if (index == -1) {
                index = keys.size();
                keys.add(key);
                totals.add(0);
                slots.add(new ArrayList<>());
            }

            totals.set(index, totals.get(index) + item.getAmount());
            slots.get(index).add(slot);
            potionCount += item.getAmount();
        }

        if (keys.isEmpty()) {
            plugin.sendMessage(player, "nothing-to-stack");
            return;
        }

        List<Group> groups = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            groups.add(new Group(totals.get(i), stackSizeFor(keys.get(i)), slots.get(i)));
        }

        Plan plan = StackPlanner.plan(groups, emptySlots);

        for (int slot : plan.clearedSlots()) {
            inventory.setItem(slot, null);
        }
        for (Placement placement : plan.placements()) {
            inventory.setItem(placement.slot(), createStack(groups, keys, placement.group(), placement.amount()));
        }
        for (Drop drop : plan.drops()) {
            player.getWorld().dropItem(player.getLocation(), createStack(groups, keys, drop.group(), drop.amount()));
        }

        int stackCount = plan.placements().size() + plan.drops().size();
        plugin.sendMessage(player, "stacked", Map.of(
                "{potions}", String.valueOf(potionCount),
                "{stacks}", String.valueOf(stackCount)
        ));

        if (!plan.drops().isEmpty()) {
            plugin.sendMessage(player, "dropped-overflow");
        }
    }

    private boolean isStackable(ItemStack item) {
        String setting = switch (item.getType()) {
            case POTION -> "potion";
            case SPLASH_POTION -> "splash";
            case LINGERING_POTION -> "lingering";
            default -> null;
        };
        return setting != null && plugin.getConfig().getBoolean("potions." + setting, true);
    }

    /**
     * A single copy of the item with any stack size we gave it removed, so stacked and fresh potions compare equal.
     */
    private static ItemStack normalize(ItemStack item) {
        ItemStack key = item.clone();
        key.setAmount(1);

        ItemMeta meta = key.getItemMeta();
        if (meta != null && meta.hasMaxStackSize()) {
            meta.setMaxStackSize(null);
            key.setItemMeta(meta);
        }
        return key;
    }

    private static int indexOf(List<ItemStack> keys, ItemStack key) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).isSimilar(key)) return i;
        }
        return -1;
    }

    /**
     * The configured stack size for the potion's type, falling back from LONG_/STRONG_ variants to the base type,
     * then to the default.
     */
    private int stackSizeFor(ItemStack potion) {
        int size = plugin.getConfig().getInt("default-stack-size", 64);

        if (potion.getItemMeta() instanceof PotionMeta meta && meta.hasBasePotionType()) {
            PotionType type = meta.getBasePotionType();
            String name = type.name();
            String baseName = name.replaceFirst("^(LONG|STRONG)_", "");

            if (plugin.getConfig().isInt("stack-sizes." + name)) {
                size = plugin.getConfig().getInt("stack-sizes." + name);
            } else if (plugin.getConfig().isInt("stack-sizes." + baseName)) {
                size = plugin.getConfig().getInt("stack-sizes." + baseName);
            }
        }

        return Math.clamp(size, 1, MAX_STACK_SIZE_LIMIT);
    }

    private static ItemStack createStack(List<Group> groups, List<ItemStack> keys, int group, int amount) {
        ItemStack stack = keys.get(group).clone();

        // A lone potion stays vanilla; anything we actually merged carries its stack size.
        if (groups.get(group).total() > 1) {
            ItemMeta meta = stack.getItemMeta();
            meta.setMaxStackSize(groups.get(group).maxStackSize());
            stack.setItemMeta(meta);
        }

        stack.setAmount(amount);
        return stack;
    }
}
