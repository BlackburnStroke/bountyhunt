package com.bountyhunt.monster;

import com.bountyhunt.BountyHuntMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Custom bounty monsters. They are only ever spawned by hunts, never naturally. */
public final class ModEntities {
	public static final EntityType<BountyZombieEntity> BOUNTY_ZOMBIE = Registry.register(Registries.ENTITY_TYPE,
			BountyHuntMod.id("bounty_zombie"),
			FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, BountyZombieEntity::new)
					.dimensions(EntityDimensions.fixed(0.6f, 1.95f))
					.trackRangeBlocks(96)
					.build());

	private ModEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(BOUNTY_ZOMBIE, ZombieEntity.createZombieAttributes());
	}
}
