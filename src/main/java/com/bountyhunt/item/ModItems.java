package com.bountyhunt.item;

import com.bountyhunt.BountyHuntMod;
import com.bountyhunt.block.ModBlocks;
import com.bountyhunt.bounty.BountyDifficulty;
import com.bountyhunt.monster.ModEntities;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ModItems {
	/** Everything the mod adds lives in this dedicated creative tab. */
	public static final ItemGroup MAIN_GROUP = FabricItemGroup.builder()
			.icon(() -> new ItemStack(ModBlocks.BOUNTY_BOARD))
			.displayName(Text.translatable("itemgroup.bountyhunt"))
			.build();

	public static final Item HUNTING_COMPASS = new HuntingCompassItem(new FabricItemSettings().maxCount(1));

	// Scrolls only exist to represent bounties inside the board GUI; each difficulty has its own texture.
	public static final Item SCROLL_NORMAL = new Item(new FabricItemSettings().maxCount(1));
	public static final Item SCROLL_MEDIUM = new Item(new FabricItemSettings().maxCount(1));
	public static final Item SCROLL_HARD = new Item(new FabricItemSettings().maxCount(1));
	public static final Item SCROLL_LEGENDARY = new Item(new FabricItemSettings().maxCount(1));

	/** All bounty spawn eggs, in tab order (mob groups, tier order inside each). */
	private static final List<Item> TAB_ITEMS = new ArrayList<>();
	private static final List<BountySpawnEggItem> SPAWN_EGGS = new ArrayList<>();

	// Spawn egg icon colours: mob body (primary) + the vanilla-like spot accent each tier gets.
	private static final int MEDIUM_SPOT = 0x2FAE9C;   // teal
	private static final int HARD_SPOT = 0xC3372C;    // red
	private static final int LEGENDARY_SPOT = 0xE8B923;

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

	/** Eggs in registration order, for the client-side tint provider. */
	public static BountySpawnEggItem[] spawnEggs() {
		return SPAWN_EGGS.toArray(new BountySpawnEggItem[0]);
	}

	private static Item register(String path, Item item) {
		Item registered = Registry.register(Registries.ITEM, BountyHuntMod.id(path), item);
		TAB_ITEMS.add(registered);
		return registered;
	}

	private static void registerBountyEggs() {
		Map<String, Supplier<? extends EntityType<? extends MobEntity>>> mobs = new LinkedHashMap<>();
		// zombie and skeleton use the custom bounty entities (per-tier model + loot); the rest are vanilla mobs.
		mobs.put("zombie", () -> ModEntities.BOUNTY_ZOMBIE);
		mobs.put("skeleton", () -> ModEntities.BOUNTY_SKELETON);
		mobs.put("husk", () -> EntityType.HUSK);
		mobs.put("stray", () -> EntityType.STRAY);
		mobs.put("spider", () -> EntityType.SPIDER);
		mobs.put("cave_spider", () -> EntityType.CAVE_SPIDER);
		mobs.put("creeper", () -> EntityType.CREEPER);
		mobs.put("witch", () -> EntityType.WITCH);
		mobs.put("enderman", () -> EntityType.ENDERMAN);

		Map<String, Integer> primary = Map.ofEntries(
				Map.entry("zombie", 0x00AFA0),
				Map.entry("skeleton", 0xC9C9C9),
				Map.entry("husk", 0xC6BA90),
				Map.entry("stray", 0x55B1BF),
				Map.entry("spider", 0x342927),
				Map.entry("cave_spider", 0x212121),
				Map.entry("creeper", 0x4CBE53),
				Map.entry("witch", 0x00594D),
				Map.entry("enderman", 0x16161D));
		Map<String, Integer> normalSpot = Map.ofEntries(
				Map.entry("zombie", 0x796A41),
				Map.entry("skeleton", 0x49535A),
				Map.entry("husk", 0x6D5E3A),
				Map.entry("stray", 0x5B6166),
				Map.entry("spider", 0x9A4A3D),
				Map.entry("cave_spider", 0x653034),
				Map.entry("creeper", 0x0D2233),
				Map.entry("witch", 0x2D4985),
				Map.entry("enderman", 0x3FD4DF));

		for (Map.Entry<String, Supplier<? extends EntityType<? extends MobEntity>>> entry : mobs.entrySet()) {
			String mob = entry.getKey();
			for (BountyDifficulty difficulty : BountyDifficulty.values()) {
				int spot = switch (difficulty) {
					case NORMAL -> normalSpot.get(mob);
					case MEDIUM -> MEDIUM_SPOT;
					case HARD -> HARD_SPOT;
					case LEGENDARY -> LEGENDARY_SPOT;
				};
				String id = "bounty_egg_" + mob + "_" + difficulty.name().toLowerCase();
				BountySpawnEggItem egg = new BountySpawnEggItem(entry.getValue(), difficulty,
						primary.get(mob), spot, new FabricItemSettings());
				register(id, egg);
				SPAWN_EGGS.add(egg);
			}
		}
	}

	public static void register() {
		Registry.register(Registries.ITEM_GROUP, BountyHuntMod.id("main"), MAIN_GROUP);

		register("hunting_compass", HUNTING_COMPASS);
		register("bounty_scroll_normal", SCROLL_NORMAL);
		register("bounty_scroll_medium", SCROLL_MEDIUM);
		register("bounty_scroll_hard", SCROLL_HARD);
		register("bounty_scroll_legendary", SCROLL_LEGENDARY);
		registerBountyEggs();

		ItemGroupEvents.modifyEntriesEvent(BountyHuntMod.id("main")).register(entries -> {
			for (Item item : TAB_ITEMS) {
				entries.add(item);
			}
		});
	}
}
