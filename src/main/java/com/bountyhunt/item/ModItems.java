package com.bountyhunt.item;

import com.bountyhunt.BountyHuntMod;
import com.bountyhunt.block.ModBlocks;
import com.bountyhunt.bounty.BountyDifficulty;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
	public static final Item HUNTING_COMPASS = new HuntingCompassItem(new FabricItemSettings().maxCount(1));

	// Scrolls only exist to represent bounties inside the board GUI; each difficulty has its own texture.
	public static final Item SCROLL_NORMAL = new Item(new FabricItemSettings().maxCount(1));
	public static final Item SCROLL_MEDIUM = new Item(new FabricItemSettings().maxCount(1));
	public static final Item SCROLL_HARD = new Item(new FabricItemSettings().maxCount(1));
	public static final Item SCROLL_LEGENDARY = new Item(new FabricItemSettings().maxCount(1));

	private ModItems() {
	}

	public static Item scroll(BountyDifficulty difficulty) {
		return switch (difficulty) {
			case NORMAL -> SCROLL_NORMAL;
			case MEDIUM -> SCROLL_MEDIUM;
			case HARD -> SCROLL_HARD;
			case LEGENDARY -> SCROLL_LEGENDARY;
		};
	}

	public static void register() {
		Registry.register(Registries.ITEM, BountyHuntMod.id("hunting_compass"), HUNTING_COMPASS);
		Registry.register(Registries.ITEM, BountyHuntMod.id("bounty_scroll_normal"), SCROLL_NORMAL);
		Registry.register(Registries.ITEM, BountyHuntMod.id("bounty_scroll_medium"), SCROLL_MEDIUM);
		Registry.register(Registries.ITEM, BountyHuntMod.id("bounty_scroll_hard"), SCROLL_HARD);
		Registry.register(Registries.ITEM, BountyHuntMod.id("bounty_scroll_legendary"), SCROLL_LEGENDARY);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			entries.add(ModBlocks.BOUNTY_BOARD);
			entries.add(HUNTING_COMPASS);
		});
	}
}
