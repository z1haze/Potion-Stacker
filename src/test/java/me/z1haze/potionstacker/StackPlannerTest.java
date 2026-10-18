package me.z1haze.potionstacker;

import me.z1haze.potionstacker.StackPlanner.Drop;
import me.z1haze.potionstacker.StackPlanner.Group;
import me.z1haze.potionstacker.StackPlanner.Placement;
import me.z1haze.potionstacker.StackPlanner.Plan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StackPlannerTest {

    @Test
    void mergesIntoLowestOwnSlot() {
        Plan plan = StackPlanner.plan(List.of(new Group(3, 64, List.of(20, 4, 9))), List.of());

        assertEquals(List.of(new Placement(4, 0, 3)), plan.placements());
        assertEquals(List.of(9, 20), plan.clearedSlots());
        assertEquals(List.of(), plan.drops());
    }

    @Test
    void splitsAtMaxStackSize() {
        Plan plan = StackPlanner.plan(List.of(new Group(40, 16, List.of(0, 1, 2, 3))), List.of());

        assertEquals(List.of(new Placement(0, 0, 16), new Placement(1, 0, 16), new Placement(2, 0, 8)),
                plan.placements());
        assertEquals(List.of(3), plan.clearedSlots());
    }

    @Test
    void groupsStayInTheirOwnSlots() {
        Plan plan = StackPlanner.plan(List.of(
                new Group(2, 64, List.of(5, 1)),
                new Group(2, 64, List.of(0, 3))
        ), List.of());

        assertEquals(List.of(new Placement(1, 0, 2), new Placement(0, 1, 2)), plan.placements());
        assertEquals(List.of(3, 5), plan.clearedSlots());
    }

    @Test
    void overflowUsesFreedSlotsThenEmptySlots() {
        // group 0 frees slot 2; group 1 was lowered to size 10 and needs two extra slots
        Plan plan = StackPlanner.plan(List.of(
                new Group(2, 64, List.of(1, 2)),
                new Group(30, 10, List.of(0))
        ), List.of(7));

        assertEquals(List.of(
                new Placement(1, 0, 2),
                new Placement(0, 1, 10),
                new Placement(2, 1, 10),
                new Placement(7, 1, 10)
        ), plan.placements());
        assertEquals(List.of(), plan.clearedSlots());
        assertEquals(List.of(), plan.drops());
    }

    @Test
    void dropsWhatDoesNotFit() {
        Plan plan = StackPlanner.plan(List.of(new Group(25, 10, List.of(0))), List.of(3));

        assertEquals(List.of(new Placement(0, 0, 10), new Placement(3, 0, 10)), plan.placements());
        assertEquals(List.of(new Drop(0, 5)), plan.drops());
    }

    @Test
    void untouchedEmptySlotsAreNotCleared() {
        Plan plan = StackPlanner.plan(List.of(new Group(1, 64, List.of(4))), List.of(0, 8));

        assertEquals(List.of(new Placement(4, 0, 1)), plan.placements());
        assertEquals(List.of(), plan.clearedSlots());
    }

    @Test
    void rejectsInvalidGroups() {
        assertThrows(IllegalArgumentException.class, () -> new Group(0, 64, List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> new Group(1, 0, List.of(0)));
    }
}
