package com.bountyhunt.client;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

/**
 * Holds the latest server-pushed target. The needle itself is driven by the vanilla
 * CompassAnglePredicateProvider (same maths, wobble and 32 frames as a compass pointing at world spawn),
 * so this only has to say where the target is.
 */
public final class CompassClient {
	private static boolean hasTarget;
	private static double targetX;
	private static double targetZ;

	private CompassClient() {
	}

	public static void set(boolean has, double x, double z) {
		hasTarget = has;
		targetX = x;
		targetZ = z;
	}

	public static void clear() {
		hasTarget = false;
	}

	/** Null (needle spins, like a vanilla compass with nothing to point at) when there is no target here. */
	public static GlobalPos target(ClientWorld world) {
		if (!hasTarget || world == null || world.getRegistryKey() != World.OVERWORLD) {
			return null;
		}
		return GlobalPos.create(world.getRegistryKey(), BlockPos.ofFloored(targetX, 64.0, targetZ));
	}
}
