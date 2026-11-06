package com.bountyhunt.monster;

import com.bountyhunt.bounty.Bounty;
import com.bountyhunt.bounty.BountyDifficulty;
import com.bountyhunt.config.BountyConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.UUID;

/** Applies difficulty to a freshly created vanilla mob and tags it so it can be recognised after reloads. */
public final class MonsterScaling {
	public static final String TAG = "bountyhunt_monster";
	public static final String ID_TAG_PREFIX = "bountyhunt_id:";

	private MonsterScaling() {
	}

	/**
	 * Applies the difficulty's stats, size, tier data, glow and behaviour to a freshly created mob.
	 * Shared by bounty hunts and bounty spawn eggs. Does NOT tag or name the mob.
	 */
	public static void applyDifficulty(MobEntity mob, BountyDifficulty difficulty) {
		double m = difficulty.statMultiplier();

		EntityAttributeInstance health = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(health.getBaseValue() * m);
		}
		mob.setHealth(mob.getMaxHealth());

		// Mobs with 0 base armor would stay at 0 when multiplied, so use a baseline for them.
		EntityAttributeInstance armor = mob.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
		if (armor != null) {
			double base = armor.getBaseValue();
			if (base <= 0.0) {
				base = BountyConfig.get().zeroArmorBaseline;
			}
			armor.setBaseValue(base * m);
		}

		if (mob instanceof ZombieEntity zombie) {
			zombie.setBaby(false);
		}
		if (mob instanceof BountyTiered tiered) {
			tiered.setTier(difficulty.ordinal()); // picks the model, texture and loot table
		}
		mob.setCanPickUpLoot(false);
		mob.setPersistent();

		// Damage and size live in the LivingEntity mixin (damage applies to every attack type).
		if ((Object) mob instanceof BountyScaled scaled) {
			scaled.bountyhunt$setDamageMultiplier((float) m);
			scaled.bountyhunt$setScale(difficulty.sizeMultiplier());
		}

		// Permanent highlight (outline visible through walls). Duration -1 = infinite.
		mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, -1, 0, false, false));
	}

	public static void apply(MobEntity mob, Bounty bounty) {
		BountyDifficulty difficulty = bounty.difficulty;
		applyDifficulty(mob, difficulty);

		mob.addCommandTag(TAG);
		mob.addCommandTag(ID_TAG_PREFIX + bounty.id);
		mob.setCustomName(Text.empty().append(difficulty.displayName()).append(" ").append(Registries.ENTITY_TYPE.get(bounty.monsterType).getName()));
		mob.setCustomNameVisible(true);
	}

	public static boolean isBountyMonster(Entity entity) {
		return entity.getCommandTags().contains(TAG);
	}

	public static UUID getBountyId(Entity entity) {
		for (String tag : entity.getCommandTags()) {
			if (tag.startsWith(ID_TAG_PREFIX)) {
				try {
					return UUID.fromString(tag.substring(ID_TAG_PREFIX.length()));
				} catch (IllegalArgumentException ignored) {
					// malformed tag, keep looking
				}
			}
		}
		return null;
	}
}
