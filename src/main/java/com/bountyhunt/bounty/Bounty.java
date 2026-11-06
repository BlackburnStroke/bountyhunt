package com.bountyhunt.bounty;

import com.bountyhunt.hunt.HuntState;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public class Bounty {
	public final UUID id;
	public final BountyDifficulty difficulty;
	public final Identifier monsterType;
	/** Fixed at creation; never changes on accept/commence. */
	public final BlockPos target;
	/** World time-of-day tick at creation; expiry is measured from this. */
	public final long createdTick;
	/** Null for main-board bounties, player UUID for personal bounties. */
	public final UUID owner;
	public HuntState state = HuntState.AVAILABLE;

	public Bounty(UUID id, BountyDifficulty difficulty, Identifier monsterType, BlockPos target, long createdTick, UUID owner) {
		this.id = id;
		this.difficulty = difficulty;
		this.monsterType = monsterType;
		this.target = target;
		this.createdTick = createdTick;
		this.owner = owner;
	}

	public boolean isPersonal() {
		return owner != null;
	}

	public NbtCompound toNbt() {
		NbtCompound n = new NbtCompound();
		n.putUuid("Id", id);
		n.putString("Difficulty", difficulty.name());
		n.putString("Monster", monsterType.toString());
		n.putLong("Target", target.asLong());
		n.putLong("Created", createdTick);
		n.putString("State", state.name());
		if (owner != null) {
			n.putUuid("Owner", owner);
		}
		return n;
	}

	public static Bounty fromNbt(NbtCompound n) {
		Identifier monster = Identifier.tryParse(n.getString("Monster"));
		if (monster == null) {
			monster = Registries.ENTITY_TYPE.getId(EntityType.ZOMBIE);
		}
		Bounty b = new Bounty(
				n.getUuid("Id"),
				BountyDifficulty.byName(n.getString("Difficulty")),
				monster,
				BlockPos.fromLong(n.getLong("Target")),
				n.getLong("Created"),
				n.containsUuid("Owner") ? n.getUuid("Owner") : null);
		b.state = HuntState.byName(n.getString("State"));
		return b;
	}
}
