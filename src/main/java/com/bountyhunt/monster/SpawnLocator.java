package com.bountyhunt.monster;

import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/** Finds real ground at the chunk surface for a bounty monster: never caves, water, leaves or tree tops. */
public final class SpawnLocator {
	private static final int SEARCH_RADIUS = 8;

	private SpawnLocator() {
	}

	/**
	 * The first standing Y (air above ground) at x/z, found by reading the chunk's current surface heightmap and
	 * walking down past logs and leaves to solid, dry ground. Null if the column is water/lava or has no ground.
	 */
	public static Integer groundY(ServerWorld world, int x, int z) {
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		int min = Math.max(world.getBottomY() + 1, world.getSeaLevel() - 8);
		for (int y = top; y > min; y--) {
			BlockPos below = new BlockPos(x, y - 1, z);
			BlockState state = world.getBlockState(below);
			if (!state.getFluidState().isEmpty()) {
				return null; // water or lava surface
			}
			if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS)) {
				continue; // never stand on a tree; keep going down to the real ground
			}
			if (state.isSideSolidFullSquare(world, below, Direction.UP)) {
				return y;
			}
		}
		return null;
	}

	/**
	 * Surface spot for this (already scaled) monster: the target column first, then rings of columns around it,
	 * accepting the first where the monster's full hitbox fits. Null if nothing fits within the search radius.
	 */
	public static Vec3d find(ServerWorld world, MobEntity mob, BlockPos target) {
		for (int r = 0; r <= SEARCH_RADIUS; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue; // only the ring at distance r
					}
					int x = target.getX() + dx;
					int z = target.getZ() + dz;
					Integer y = groundY(world, x, z);
					if (y == null) {
						continue;
					}
					mob.setPosition(x + 0.5, y, z + 0.5);
					if (world.isSpaceEmpty(mob) && world.getFluidState(new BlockPos(x, y, z)).isEmpty()) {
						return new Vec3d(x + 0.5, y, z + 0.5);
					}
				}
			}
		}
		return null;
	}
}
