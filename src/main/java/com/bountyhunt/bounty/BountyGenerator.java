package com.bountyhunt.bounty;

import com.bountyhunt.config.BountyConfig;
import com.bountyhunt.monster.MonsterRegistry;
import com.bountyhunt.monster.SpawnLocator;
import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BountyGenerator {
	private BountyGenerator() {
	}

	/** One day's worth of main-board contracts: 5 normal, 3 medium, 1 hard, 25% chance of 1 legendary. */
	public static List<Bounty> generateDailyMain(ServerWorld world) {
		BountyConfig cfg = BountyConfig.get();
		List<Bounty> out = new ArrayList<>();
		for (int i = 0; i < cfg.normalPerDay; i++) {
			out.add(create(world, BountyDifficulty.NORMAL, null));
		}
		for (int i = 0; i < cfg.mediumPerDay; i++) {
			out.add(create(world, BountyDifficulty.MEDIUM, null));
		}
		for (int i = 0; i < cfg.hardPerDay; i++) {
			out.add(create(world, BountyDifficulty.HARD, null));
		}
		if (world.getRandom().nextDouble() < cfg.legendaryChance) {
			out.add(create(world, BountyDifficulty.LEGENDARY, null));
		}
		return out;
	}

	/** One personal contract; difficulty is rolled 50/30/15/5. */
	public static Bounty createPersonal(ServerWorld world, UUID owner) {
		return create(world, BountyDifficulty.rollPersonal(world.getRandom()), owner);
	}

	public static Bounty create(ServerWorld world, BountyDifficulty difficulty, UUID owner) {
		Random random = world.getRandom();
		return new Bounty(
				UUID.randomUUID(),
				difficulty,
				MonsterRegistry.randomType(random),
				pickLocation(world, random),
				world.getTimeOfDay(),
				owner);
	}

	/**
	 * Picks a land surface position between min and max distance from world spawn.
	 * Oceans/rivers are rejected by biome (without loading chunks); the chunk is then generated
	 * to read the real surface height and confirm solid, non-liquid ground.
	 */
	private static BlockPos pickLocation(ServerWorld world, Random random) {
		BountyConfig cfg = BountyConfig.get();
		BlockPos center = world.getSpawnPos();
		BlockPos fallback = null;

		for (int attempt = 0; attempt < cfg.locationAttempts; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double dist = cfg.minTargetDistance + random.nextDouble() * (cfg.maxTargetDistance - cfg.minTargetDistance);
			int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
			int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);

			if (!world.getWorldBorder().contains(new BlockPos(x, 0, z))) {
				continue;
			}
			RegistryEntry<Biome> biome = world.getBiome(new BlockPos(x, world.getSeaLevel(), z));
			if (biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_RIVER)) {
				continue;
			}

			Integer ground = SpawnLocator.groundY(world, x, z); // real ground: not water, leaves or tree tops
			if (ground == null) {
				continue;
			}
			int y = ground;
			BlockPos pos = new BlockPos(x, y, z);
			fallback = pos;
			if (y < world.getSeaLevel()) {
				continue;
			}
			return pos;
		}

		if (fallback != null) {
			return fallback;
		}
		int x = center.getX() + cfg.minTargetDistance;
		int z = center.getZ();
		return new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}
}
