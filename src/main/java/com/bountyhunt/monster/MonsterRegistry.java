package com.bountyhunt.monster;

import com.bountyhunt.BountyHuntMod;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * Eligible bounty monsters (overworld land hostiles only).
 * Not enabled yet because they need per-mob handling: Drowned (aquatic), Slime and Phantom
 * (own size systems / dimension overrides).
 */
public final class MonsterRegistry {
	private static final List<EntityType<?>> ELIGIBLE = List.of(
			EntityType.ZOMBIE,
			EntityType.HUSK,
			EntityType.SKELETON,
			EntityType.STRAY,
			EntityType.SPIDER,
			EntityType.CAVE_SPIDER,
			EntityType.CREEPER,
			EntityType.WITCH,
			EntityType.ENDERMAN);

	private MonsterRegistry() {
	}

	public static Identifier randomType(Random random) {
		return Registries.ENTITY_TYPE.getId(ELIGIBLE.get(random.nextInt(ELIGIBLE.size())));
	}

	/** The entity actually spawned for a bounty monster id: zombie and skeleton are custom bounty monsters, the rest are still vanilla. */
	public static Identifier spawnType(Identifier bountyType) {
		if (bountyType.equals(new Identifier("zombie"))) {
			return BountyHuntMod.id("bounty_zombie");
		}
		if (bountyType.equals(new Identifier("skeleton"))) {
			return BountyHuntMod.id("bounty_skeleton");
		}
		return bountyType;
	}
}
