package com.bountyhunt.monster;

import net.minecraft.entity.EntityType;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * The bounty zombie: a vanilla zombie in behaviour, with its own model and loot per difficulty tier
 * (0 Normal, 1 Medium, 2 Hard, 3 Legendary). Stats, size and glow still come from MonsterScaling.
 */
public class BountyZombieEntity extends ZombieEntity implements BountyTiered {
	private static final TrackedData<Integer> TIER = DataTracker.registerData(BountyZombieEntity.class, TrackedDataHandlerRegistry.INTEGER);

	public BountyZombieEntity(EntityType<? extends ZombieEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker() {
		super.initDataTracker();
		this.dataTracker.startTracking(TIER, 0);
	}

	public int getTier() {
		return this.dataTracker.get(TIER);
	}

	@Override
	public void setTier(int tier) {
		this.dataTracker.set(TIER, MathHelper.clamp(tier, 0, 3));
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("BountyTier", getTier());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		setTier(nbt.getInt("BountyTier"));
	}

	/** Each difficulty has its own loot table: data/bountyhunt/loot_tables/entities/bounty_zombie/tier_N.json */
	@Override
	protected Identifier getLootTableId() {
		return new Identifier("bountyhunt", "entities/bounty_zombie/tier_" + getTier());
	}

	/** No random vanilla gear: the model carries the armour, and stray armour would change the stats. */
	@Override
	protected void initEquipment(Random random, LocalDifficulty difficulty) {
	}
}
