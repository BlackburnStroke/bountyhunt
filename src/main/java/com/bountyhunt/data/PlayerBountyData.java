package com.bountyhunt.data;

import com.bountyhunt.bounty.Bounty;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Per-player (UUID) personal bonus bounties and reroll usage. */
public class PlayerBountyData {
	public final UUID player;
	public final List<Bounty> bounties = new ArrayList<>();
	public int rerollsUsed;
	/** Day index the current personal set was generated for. */
	public long generatedDay = -1;

	public PlayerBountyData(UUID player) {
		this.player = player;
	}

	public NbtCompound toNbt() {
		NbtCompound n = new NbtCompound();
		n.putUuid("Player", player);
		n.putInt("Rerolls", rerollsUsed);
		n.putLong("Day", generatedDay);
		NbtList list = new NbtList();
		for (Bounty b : bounties) {
			list.add(b.toNbt());
		}
		n.put("Bounties", list);
		return n;
	}

	public static PlayerBountyData fromNbt(NbtCompound n) {
		PlayerBountyData d = new PlayerBountyData(n.getUuid("Player"));
		d.rerollsUsed = n.getInt("Rerolls");
		d.generatedDay = n.contains("Day") ? n.getLong("Day") : -1;
		for (NbtElement e : n.getList("Bounties", NbtElement.COMPOUND_TYPE)) {
			d.bounties.add(Bounty.fromNbt((NbtCompound) e));
		}
		return d;
	}
}
