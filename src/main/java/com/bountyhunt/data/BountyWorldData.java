package com.bountyhunt.data;

import com.bountyhunt.bounty.Bounty;
import com.bountyhunt.hunt.HuntInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * All persistent bounty state, stored in the overworld's data folder (data/bountyhunt.dat).
 * Holds the main board, personal bounties, and active hunts (party, host, monster association).
 */
public class BountyWorldData extends PersistentState {
	public static final String KEY = "bountyhunt";

	private final Map<UUID, Bounty> main = new LinkedHashMap<>();
	private final Map<UUID, HuntInstance> hunts = new LinkedHashMap<>();
	private final Map<UUID, PlayerBountyData> players = new HashMap<>();
	private long lastGeneratedDay = -1;
	/** Eliminated (spectating) players that were offline when their hunt ended; set back to survival on join. */
	private final Set<UUID> restoreOnJoin = new LinkedHashSet<>();

	public static BountyWorldData get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager()
				.getOrCreate(BountyWorldData::fromNbt, BountyWorldData::new, KEY);
	}

	/** State is small, so it is simply always written on save; avoids missing a markDirty() call. */
	@Override
	public boolean isDirty() {
		return true;
	}

	public Map<UUID, Bounty> main() {
		return main;
	}

	public Map<UUID, HuntInstance> hunts() {
		return hunts;
	}

	public Collection<PlayerBountyData> players() {
		return players.values();
	}

	public PlayerBountyData playerData(UUID id) {
		return players.computeIfAbsent(id, PlayerBountyData::new);
	}

	public Set<UUID> restoreOnJoin() {
		return restoreOnJoin;
	}

	public long lastGeneratedDay() {
		return lastGeneratedDay;
	}

	public void setLastGeneratedDay(long day) {
		this.lastGeneratedDay = day;
	}

	public Bounty findBounty(UUID id) {
		Bounty b = main.get(id);
		if (b != null) {
			return b;
		}
		for (PlayerBountyData pd : players.values()) {
			for (Bounty pb : pd.bounties) {
				if (pb.id.equals(id)) {
					return pb;
				}
			}
		}
		return null;
	}

	public void removeBounty(Bounty bounty) {
		if (main.remove(bounty.id) == null && bounty.owner != null) {
			PlayerBountyData pd = players.get(bounty.owner);
			if (pd != null) {
				pd.bounties.removeIf(b -> b.id.equals(bounty.id));
			}
		}
	}

	public HuntInstance findHuntOf(UUID player) {
		for (HuntInstance h : hunts.values()) {
			if (h.party().contains(player)) {
				return h;
			}
		}
		return null;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.putLong("LastDay", lastGeneratedDay);
		NbtList mainList = new NbtList();
		for (Bounty b : main.values()) {
			mainList.add(b.toNbt());
		}
		nbt.put("Main", mainList);
		NbtList playerList = new NbtList();
		for (PlayerBountyData pd : players.values()) {
			playerList.add(pd.toNbt());
		}
		nbt.put("Players", playerList);
		NbtList huntList = new NbtList();
		for (HuntInstance h : hunts.values()) {
			huntList.add(h.toNbt());
		}
		nbt.put("Hunts", huntList);
		NbtList restore = new NbtList();
		for (UUID id : restoreOnJoin) {
			NbtCompound c = new NbtCompound();
			c.putUuid("Id", id);
			restore.add(c);
		}
		nbt.put("Restore", restore);
		return nbt;
	}

	public static BountyWorldData fromNbt(NbtCompound nbt) {
		BountyWorldData d = new BountyWorldData();
		d.lastGeneratedDay = nbt.contains("LastDay") ? nbt.getLong("LastDay") : -1;
		for (NbtElement e : nbt.getList("Main", NbtElement.COMPOUND_TYPE)) {
			Bounty b = Bounty.fromNbt((NbtCompound) e);
			d.main.put(b.id, b);
		}
		for (NbtElement e : nbt.getList("Players", NbtElement.COMPOUND_TYPE)) {
			PlayerBountyData pd = PlayerBountyData.fromNbt((NbtCompound) e);
			d.players.put(pd.player, pd);
		}
		// Hunts are loaded last so they can be linked to their (already loaded) bounty objects.
		for (NbtElement e : nbt.getList("Hunts", NbtElement.COMPOUND_TYPE)) {
			HuntInstance h = HuntInstance.fromNbt((NbtCompound) e, d::findBounty);
			if (h != null) {
				d.hunts.put(h.bounty().id, h);
			}
		}
		for (NbtElement e : nbt.getList("Restore", NbtElement.COMPOUND_TYPE)) {
			d.restoreOnJoin.add(((NbtCompound) e).getUuid("Id"));
		}
		return d;
	}
}
