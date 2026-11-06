package com.bountyhunt.hunt;

import com.bountyhunt.bounty.Bounty;
import com.bountyhunt.party.HuntingParty;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** One contract that a party has taken on: the party, and the one-and-only monster association. */
public class HuntInstance {
	private final Bounty bounty;
	private final HuntingParty party;
	private boolean monsterSpawned;
	private UUID monsterUuid;
	/** Spectators: out of the hunt until it ends. */
	private final Set<UUID> eliminated = new LinkedHashSet<>();
	/** Currently downed (crawling, waiting for a revive). */
	private final Set<UUID> downed = new LinkedHashSet<>();
	/** Times each player was downed after the monster spawned. Reset naturally with each new hunt. */
	private final Map<UUID, Integer> downs = new HashMap<>();

	public HuntInstance(Bounty bounty, HuntingParty party) {
		this.bounty = bounty;
		this.party = party;
	}

	public Bounty bounty() {
		return bounty;
	}

	public HuntingParty party() {
		return party;
	}

	public boolean monsterSpawned() {
		return monsterSpawned;
	}

	public UUID monsterUuid() {
		return monsterUuid;
	}

	public void setMonster(UUID uuid) {
		this.monsterSpawned = true;
		this.monsterUuid = uuid;
	}

	public boolean isEliminated(UUID id) {
		return eliminated.contains(id);
	}

	public void eliminate(UUID id) {
		eliminated.add(id);
	}

	public boolean isDowned(UUID id) {
		return downed.contains(id);
	}

	public void setDowned(UUID id, boolean value) {
		if (value) {
			downed.add(id);
		} else {
			downed.remove(id);
		}
	}

	public int downs(UUID id) {
		return downs.getOrDefault(id, 0);
	}

	public void addDown(UUID id) {
		downs.merge(id, 1, Integer::sum);
	}

	/** Drop all survival state for a player who left or was removed from the party. */
	public void forget(UUID id) {
		eliminated.remove(id);
		downed.remove(id);
		downs.remove(id);
	}

	public boolean allEliminated() {
		if (party.size() == 0) {
			return false;
		}
		for (UUID id : party.members()) {
			if (!eliminated.contains(id)) {
				return false;
			}
		}
		return true;
	}

	public NbtCompound toNbt() {
		NbtCompound n = new NbtCompound();
		n.putUuid("Bounty", bounty.id);
		n.put("Party", party.toNbt());
		n.putBoolean("Spawned", monsterSpawned);
		if (monsterUuid != null) {
			n.putUuid("Monster", monsterUuid);
		}
		NbtList list = new NbtList();
		for (UUID id : eliminated) {
			NbtCompound c = new NbtCompound();
			c.putUuid("Id", id);
			list.add(c);
		}
		n.put("Eliminated", list);
		NbtList downedList = new NbtList();
		for (UUID id : downed) {
			NbtCompound c = new NbtCompound();
			c.putUuid("Id", id);
			downedList.add(c);
		}
		n.put("Downed", downedList);
		NbtList downsList = new NbtList();
		for (Map.Entry<UUID, Integer> e : downs.entrySet()) {
			NbtCompound c = new NbtCompound();
			c.putUuid("Id", e.getKey());
			c.putInt("N", e.getValue());
			downsList.add(c);
		}
		n.put("Downs", downsList);
		return n;
	}

	public static HuntInstance fromNbt(NbtCompound n, Function<UUID, Bounty> bountyResolver) {
		Bounty b = bountyResolver.apply(n.getUuid("Bounty"));
		if (b == null) {
			return null;
		}
		HuntInstance h = new HuntInstance(b, HuntingParty.fromNbt(n.getCompound("Party")));
		h.monsterSpawned = n.getBoolean("Spawned");
		if (n.containsUuid("Monster")) {
			h.monsterUuid = n.getUuid("Monster");
		}
		for (NbtElement e : n.getList("Eliminated", NbtElement.COMPOUND_TYPE)) {
			h.eliminated.add(((NbtCompound) e).getUuid("Id"));
		}
		for (NbtElement e : n.getList("Downed", NbtElement.COMPOUND_TYPE)) {
			h.downed.add(((NbtCompound) e).getUuid("Id"));
		}
		for (NbtElement e : n.getList("Downs", NbtElement.COMPOUND_TYPE)) {
			NbtCompound c = (NbtCompound) e;
			h.downs.put(c.getUuid("Id"), c.getInt("N"));
		}
		return h;
	}
}
