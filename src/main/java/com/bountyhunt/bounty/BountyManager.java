package com.bountyhunt.bounty;

import com.bountyhunt.BountyHuntMod;
import com.bountyhunt.config.BountyConfig;
import com.bountyhunt.data.BountyWorldData;
import com.bountyhunt.data.PlayerBountyData;
import com.bountyhunt.hunt.HuntState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/**
 * Daily generation, expiry, personal slots and rerolls.
 * "Day" = overworld time-of-day / 24000, so sleeping or /time set advance the cycle like a vanilla day.
 */
public final class BountyManager {
	private BountyManager() {
	}

	public static long currentDay(ServerWorld world) {
		return Math.floorDiv(world.getTimeOfDay(), (long) BountyConfig.get().ticksPerDay);
	}

	/** Called about once per second from the server tick. */
	public static void tick(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		if (world == null) {
			return;
		}
		BountyWorldData data = BountyWorldData.get(server);
		long day = currentDay(world);
		expire(world, data);
		if (day > data.lastGeneratedDay()) {
			for (Bounty b : BountyGenerator.generateDailyMain(world)) {
				data.main().put(b.id, b);
			}
			data.setLastGeneratedDay(day);
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				ensurePersonal(server, p.getUuid());
			}
			BountyHuntMod.LOGGER.info("Generated daily bounties for day {}", day);
		}
	}

	/** Only unused (AVAILABLE) contracts expire; commenced ones persist until they end. */
	private static void expire(ServerWorld world, BountyWorldData data) {
		long now = world.getTimeOfDay();
		long ttl = (long) BountyConfig.get().expirationDays * BountyConfig.get().ticksPerDay;
		data.main().values().removeIf(b -> b.state == HuntState.AVAILABLE && now - b.createdTick >= ttl);
	}

	/** Admin/testing helper: drop all unused main bounties and roll a fresh daily batch. */
	public static void regenerateMain(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		BountyWorldData data = BountyWorldData.get(server);
		data.main().values().removeIf(b -> b.state == HuntState.AVAILABLE);
		for (Bounty b : BountyGenerator.generateDailyMain(world)) {
			data.main().put(b.id, b);
		}
	}

	/** Lazily refreshes a player's personal bounties when the day has changed. */
	public static PlayerBountyData ensurePersonal(MinecraftServer server, UUID player) {
		ServerWorld world = server.getOverworld();
		BountyWorldData data = BountyWorldData.get(server);
		PlayerBountyData pd = data.playerData(player);
		long day = currentDay(world);
		if (pd.generatedDay < day) {
			// Personal bounties never accumulate; only ones already in progress are kept.
			pd.bounties.removeIf(b -> b.state == HuntState.AVAILABLE);
			pd.rerollsUsed = 0;
			pd.generatedDay = day;
			fill(world, pd);
		}
		return pd;
	}

	private static void fill(ServerWorld world, PlayerBountyData pd) {
		while (pd.bounties.size() < BountyConfig.get().personalSlots) {
			pd.bounties.add(BountyGenerator.createPersonal(world, pd.player));
		}
	}

	/** Rerolls all personal contracts at once. Returns an error message or null on success. */
	public static String reroll(MinecraftServer server, UUID player) {
		PlayerBountyData pd = ensurePersonal(server, player);
		if (pd.rerollsUsed >= BountyConfig.get().maxRerollsPerDay) {
			return "No personal rerolls left today.";
		}
		for (Bounty b : pd.bounties) {
			if (b.state != HuntState.AVAILABLE) {
				return "You cannot reroll while a personal bounty is in progress.";
			}
		}
		pd.bounties.clear();
		pd.rerollsUsed++;
		fill(server.getOverworld(), pd);
		return null;
	}
}
