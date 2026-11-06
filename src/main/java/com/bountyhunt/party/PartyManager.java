package com.bountyhunt.party;

import com.bountyhunt.bounty.Bounty;
import com.bountyhunt.config.BountyConfig;
import com.bountyhunt.data.BountyWorldData;
import com.bountyhunt.hunt.HuntInstance;
import com.bountyhunt.hunt.HuntManager;
import com.bountyhunt.hunt.HuntState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Party membership and leadership rules. Every method returns an error message, or null on success.
 * A bounty gets exactly one party: the first player to accept it becomes host and others join that party.
 */
public final class PartyManager {
	private PartyManager() {
	}

	public static String accept(ServerPlayerEntity player, UUID bountyId) {
		MinecraftServer server = player.getServer();
		BountyWorldData data = BountyWorldData.get(server);
		if (data.findHuntOf(player.getUuid()) != null) {
			return "You are already in a bounty party. Leave it first.";
		}
		Bounty bounty = data.findBounty(bountyId);
		if (bounty == null) {
			return "That bounty no longer exists.";
		}
		if (bounty.isPersonal() && !player.getUuid().equals(bounty.owner) && bounty.state != HuntState.PARTY_FORMING) {
			return "That personal bounty is not open for parties.";
		}

		if (bounty.state == HuntState.AVAILABLE) {
			data.hunts().put(bounty.id, new HuntInstance(bounty, new HuntingParty(player.getUuid())));
			bounty.state = HuntState.PARTY_FORMING;
			return null;
		}
		if (bounty.state == HuntState.PARTY_FORMING) {
			HuntInstance hunt = data.hunts().get(bounty.id);
			if (hunt == null) {
				bounty.state = HuntState.AVAILABLE;
				return "That party no longer exists. Try again.";
			}
			if (hunt.party().size() >= BountyConfig.get().maxPartySize) {
				return "That party is full.";
			}
			hunt.party().add(player.getUuid());
			HuntManager.broadcast(server, hunt.party(), player.getName().getString() + " joined the party.");
			return null;
		}
		return "That bounty is already underway.";
	}

	public static String leave(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		BountyWorldData data = BountyWorldData.get(server);
		HuntInstance hunt = data.findHuntOf(player.getUuid());
		if (hunt == null) {
			return "You are not in a bounty party.";
		}
		removeMember(server, data, hunt, player.getUuid());
		return null;
	}

	/** Host cancels party formation: the contract goes back on the board. */
	public static String disband(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		BountyWorldData data = BountyWorldData.get(server);
		HuntInstance hunt = data.findHuntOf(player.getUuid());
		if (hunt == null) {
			return "You are not in a bounty party.";
		}
		if (!hunt.party().isHost(player.getUuid())) {
			return "Only the host can disband the party.";
		}
		if (hunt.bounty().state != HuntState.PARTY_FORMING) {
			return "The hunt has already commenced. Use Abandon instead.";
		}
		HuntManager.broadcast(server, hunt.party(), "The host disbanded the party. The contract is back on the board.");
		returnToBoard(data, hunt);
		return null;
	}

	public static String kick(ServerPlayerEntity host, UUID target) {
		MinecraftServer server = host.getServer();
		BountyWorldData data = BountyWorldData.get(server);
		HuntInstance hunt = data.findHuntOf(host.getUuid());
		if (hunt == null) {
			return "You are not in a bounty party.";
		}
		HuntingParty party = hunt.party();
		if (!party.isHost(host.getUuid())) {
			return "Only the host can kick members.";
		}
		if (party.hostByBetrayal()) {
			return "A host who took power by betrayal cannot kick members.";
		}
		if (hunt.bounty().state != HuntState.PARTY_FORMING) {
			return "The roster is locked; members cannot be kicked after commencing.";
		}
		if (target.equals(host.getUuid()) || !party.contains(target)) {
			return "That player cannot be kicked.";
		}
		party.remove(target);
		HuntManager.onMemberRemoved(server, hunt, target);
		ServerPlayerEntity kicked = server.getPlayerManager().getPlayer(target);
		if (kicked != null) {
			HuntManager.clearCompass(kicked);
			kicked.sendMessage(HuntManager.tag("You were removed from the bounty party."), false);
		}
		HuntManager.broadcast(server, party, "A member was removed from the party.");
		return null;
	}

	/** Shared by voluntary leaving: handles host succession and an emptied party. */
	private static void removeMember(MinecraftServer server, BountyWorldData data, HuntInstance hunt, UUID leaver) {
		HuntingParty party = hunt.party();
		boolean wasHost = party.isHost(leaver);
		party.remove(leaver);
		ServerPlayerEntity leaverPlayer = server.getPlayerManager().getPlayer(leaver);
		if (leaverPlayer != null) {
			HuntManager.clearCompass(leaverPlayer);
		}
		HuntManager.onMemberRemoved(server, hunt, leaver);

		if (party.size() == 0) {
			if (hunt.bounty().state == HuntState.PARTY_FORMING) {
				returnToBoard(data, hunt);
			} else {
				HuntManager.finish(server, hunt, HuntState.ABANDONED, "Everyone left the hunt.");
			}
			return;
		}
		if (wasHost) {
			// Host left: a random remaining member takes over.
			List<UUID> remaining = new ArrayList<>(party.members());
			UUID next = remaining.get(server.getOverworld().getRandom().nextInt(remaining.size()));
			party.setCurrentHost(next, false);
			if (hunt.bounty().state == HuntState.PARTY_FORMING) {
				party.markCommenced(); // keeps originalHost in sync until the real commence
			}
			HuntManager.broadcast(server, party, "The host left. A new host has been chosen.");
		} else {
			HuntManager.broadcast(server, party, "A member left the party.");
		}
	}

	private static void returnToBoard(BountyWorldData data, HuntInstance hunt) {
		hunt.bounty().state = HuntState.AVAILABLE;
		data.hunts().remove(hunt.bounty().id);
	}

	// ---- Events -------------------------------------------------------------------------------

	/** Betrayal: a party member who kills the current host takes over and the old host is removed. */
	public static void onPlayerDeath(ServerPlayerEntity victim, DamageSource source) {
		MinecraftServer server = victim.getServer();
		BountyWorldData data = BountyWorldData.get(server);
		HuntInstance hunt = data.findHuntOf(victim.getUuid());
		if (hunt == null) {
			return;
		}
		HuntingParty party = hunt.party();
		if (!party.isHost(victim.getUuid())) {
			return;
		}
		Entity attacker = source.getAttacker();
		if (attacker instanceof ServerPlayerEntity killer
				&& !killer.getUuid().equals(victim.getUuid())
				&& party.contains(killer.getUuid())) {
			party.remove(victim.getUuid());
			HuntManager.onMemberRemoved(server, hunt, victim.getUuid());
			party.setCurrentHost(killer.getUuid(), true);
			HuntManager.broadcast(server, party, killer.getName().getString() + " betrayed and killed the host and now leads the party!");
			victim.sendMessage(HuntManager.tag("You were betrayed and removed from the party."), false);
			HuntManager.clearCompass(victim);
		}
	}

	/** If the current host disconnects, leadership moves to another online member. */
	public static void onPlayerDisconnected(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		BountyWorldData data = BountyWorldData.get(server);
		HuntInstance hunt = data.findHuntOf(player.getUuid());
		if (hunt == null || !hunt.party().isHost(player.getUuid())) {
			return;
		}
		List<UUID> candidates = new ArrayList<>();
		for (UUID id : hunt.party().members()) {
			if (!id.equals(player.getUuid()) && server.getPlayerManager().getPlayer(id) != null) {
				candidates.add(id);
			}
		}
		if (candidates.isEmpty()) {
			return;
		}
		UUID next = candidates.get(server.getOverworld().getRandom().nextInt(candidates.size()));
		hunt.party().transferBecauseDisconnected(next);
		HuntManager.broadcast(server, hunt.party(), "The host disconnected. Leadership passed to another member.");
	}

	/** The original host regains leadership if they only lost it by disconnecting. */
	public static void onPlayerJoined(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		BountyWorldData data = BountyWorldData.get(server);
		for (HuntInstance hunt : data.hunts().values()) {
			HuntingParty party = hunt.party();
			if (party.originalHostAway()
					&& party.originalHost().equals(player.getUuid())
					&& party.contains(player.getUuid())
					&& !hunt.bounty().state.isTerminal()) {
				party.restoreOriginalHost();
				HuntManager.broadcast(server, party, "The original host returned and regained leadership.");
			}
		}
	}
}
