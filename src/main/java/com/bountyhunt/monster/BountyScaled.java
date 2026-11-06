package com.bountyhunt.monster;

import net.minecraft.entity.data.TrackedData;

/**
 * Duck interface mixed into every LivingEntity (see LivingEntityMixin).
 * Lives outside the mixin package because mixin-package classes must not be referenced directly.
 */
public interface BountyScaled {
	/** Visual + hitbox scale (1.0 = vanilla). Synced to clients through the data tracker. */
	float bountyhunt$getScale();

	void bountyhunt$setScale(float scale);

	/** Multiplier applied to all damage this entity deals (melee, arrows, potions, explosions). */
	float bountyhunt$getDamageMultiplier();

	void bountyhunt$setDamageMultiplier(float multiplier);

	void bountyhunt$onTrackedData(TrackedData<?> data);

	/** Synced "downed" flag (players only): drives crawling pose on both sides. */
	boolean bountyhunt$isDowned();

	void bountyhunt$setDowned(boolean downed);
}
