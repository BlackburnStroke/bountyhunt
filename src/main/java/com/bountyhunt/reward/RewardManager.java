package com.bountyhunt.reward;

import com.bountyhunt.hunt.HuntInstance;
import com.bountyhunt.hunt.HuntManager;
import net.minecraft.server.MinecraftServer;

/**
 * Phase 8 hook. HuntManager calls this exactly once when the monster dies and the hunt is marked
 * Completed. Reward eligibility, claim screens and persistence are not implemented yet.
 */
public final class RewardManager {
	private RewardManager() {
	}

	public static void onHuntCompleted(MinecraftServer server, HuntInstance hunt) {
		HuntManager.broadcast(server, hunt.party(), "Bounty complete! (Reward claiming is not implemented yet.)");
	}
}
