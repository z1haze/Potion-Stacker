package me.z1haze.potionstacker;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Works out where stacked potions go, without touching Bukkit.
 * Each group's stacks go back into the group's own slots first (lowest slot first), then into any freed
 * or empty slots; whatever still doesn't fit is reported as a drop.
 */
public final class StackPlanner {

    /**
     * A set of identical items.
     *
     * @param total        how many items the group holds across all its slots
     * @param maxStackSize the largest stack the group may form
     * @param slots        the slots the group's items currently occupy
     */
    public record Group(int total, int maxStackSize, List<Integer> slots) {
        public Group {
            if (total < 1) throw new IllegalArgumentException("total must be positive");
            if (maxStackSize < 1) throw new IllegalArgumentException("maxStackSize must be positive");
            slots = List.copyOf(slots);
        }
    }

    /** A stack of {@code amount} items from {@code groups[group]} to place in {@code slot}. */
    public record Placement(int slot, int group, int amount) {}

    /** A stack of {@code amount} items from {@code groups[group]} that has no slot. */
    public record Drop(int group, int amount) {}

    /**
     * @param placements   stacks to put into slots
     * @param clearedSlots group slots that end up empty
     * @param drops        stacks that didn't fit anywhere
     */
    public record Plan(List<Placement> placements, List<Integer> clearedSlots, List<Drop> drops) {}

    private StackPlanner() {}

    public static Plan plan(List<Group> groups, List<Integer> emptySlots) {
        List<Placement> placements = new ArrayList<>();
        List<Drop> overflow = new ArrayList<>();
        TreeSet<Integer> freeSlots = new TreeSet<>(emptySlots);

        for (int g = 0; g < groups.size(); g++) {
            Group group = groups.get(g);
            List<Integer> ownSlots = group.slots().stream().sorted().toList();
            int remaining = group.total();
            int used = 0;

            while (remaining > 0) {
                int amount = Math.min(remaining, group.maxStackSize());
                remaining -= amount;

                if (used < ownSlots.size()) {
                    placements.add(new Placement(ownSlots.get(used++), g, amount));
                } else {
                    overflow.add(new Drop(g, amount));
                }
            }

            freeSlots.addAll(ownSlots.subList(used, ownSlots.size()));
        }

        List<Drop> drops = new ArrayList<>();
        for (Drop stack : overflow) {
            Integer slot = freeSlots.pollFirst();
            if (slot == null) {
                drops.add(stack);
            } else {
                placements.add(new Placement(slot, stack.group(), stack.amount()));
            }
        }

        // Free slots that started out empty stay empty, so only report group slots as cleared.
        List<Integer> clearedSlots = new ArrayList<>(freeSlots);
        clearedSlots.removeAll(emptySlots);

        return new Plan(placements, clearedSlots, drops);
    }
}
