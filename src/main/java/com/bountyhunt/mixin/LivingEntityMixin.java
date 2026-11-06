package com.bountyhunt.mixin;

import com.bountyhunt.monster.BountyScaled;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives every LivingEntity a synced scale and a server-side damage multiplier.
 * Vanilla mobs stay at 1.0 / 1.0, so nothing changes unless the bounty system sets them.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements BountyScaled {
	@Unique
	private static final TrackedData<Float> BOUNTYHUNT_SCALE =
			DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.FLOAT);

	@Unique
	private static final TrackedData<Boolean> BOUNTYHUNT_DOWNED =
			DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	@Unique
	private float bountyhunt$damageMultiplier = 1.0f;

	@Inject(method = "initDataTracker", at = @At("TAIL"))
	private void bountyhunt$initDataTracker(CallbackInfo ci) {
		((LivingEntity) (Object) this).getDataTracker().startTracking(BOUNTYHUNT_SCALE, 1.0f);
		((LivingEntity) (Object) this).getDataTracker().startTracking(BOUNTYHUNT_DOWNED, false);
	}

	@Override
	public float bountyhunt$getScale() {
		DataTracker tracker = ((LivingEntity) (Object) this).getDataTracker();
		// The tracker can be missing/uninitialised while the entity is still being constructed.
		return tracker != null && tracker.containsKey(BOUNTYHUNT_SCALE) ? tracker.get(BOUNTYHUNT_SCALE) : 1.0f;
	}

	@Override
	public void bountyhunt$setScale(float scale) {
		LivingEntity self = (LivingEntity) (Object) this;
		self.getDataTracker().set(BOUNTYHUNT_SCALE, scale);
		self.calculateDimensions();
	}

	@Override
	public boolean bountyhunt$isDowned() {
		DataTracker tracker = ((LivingEntity) (Object) this).getDataTracker();
		return tracker != null && tracker.containsKey(BOUNTYHUNT_DOWNED) && tracker.get(BOUNTYHUNT_DOWNED);
	}

	@Override
	public void bountyhunt$setDowned(boolean downed) {
		((LivingEntity) (Object) this).getDataTracker().set(BOUNTYHUNT_DOWNED, downed);
	}

	@Override
	public float bountyhunt$getDamageMultiplier() {
		return bountyhunt$damageMultiplier;
	}

	@Override
	public void bountyhunt$setDamageMultiplier(float multiplier) {
		this.bountyhunt$damageMultiplier = multiplier;
	}

	@Override
	public void bountyhunt$onTrackedData(TrackedData<?> data) {
		if (BOUNTYHUNT_SCALE.equals(data)) {
			((LivingEntity) (Object) this).calculateDimensions();
		}
	}

	/** Multiplies damage dealt BY a bounty monster (melee, arrows, potions, explosions all report it as attacker). */
	@ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
	private float bountyhunt$scaleOutgoingDamage(float amount, DamageSource source) {
		Entity attacker = source.getAttacker();
		if (attacker instanceof BountyScaled scaled && attacker != (Object) this) {
			float multiplier = scaled.bountyhunt$getDamageMultiplier();
			if (multiplier != 1.0f) {
				return amount * multiplier;
			}
		}
		return amount;
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void bountyhunt$writeNbt(NbtCompound nbt, CallbackInfo ci) {
		float scale = bountyhunt$getScale();
		if (scale != 1.0f) {
			nbt.putFloat("BountyHuntScale", scale);
		}
		if (bountyhunt$damageMultiplier != 1.0f) {
			nbt.putFloat("BountyHuntDamage", bountyhunt$damageMultiplier);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void bountyhunt$readNbt(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains("BountyHuntScale")) {
			bountyhunt$setScale(nbt.getFloat("BountyHuntScale"));
		}
		if (nbt.contains("BountyHuntDamage")) {
			bountyhunt$damageMultiplier = nbt.getFloat("BountyHuntDamage");
		}
	}
}
