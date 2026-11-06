package com.bountyhunt.client;

import com.bountyhunt.monster.BountyScaled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Picks the tougher-looking texture for a bounty monster, if one exists:
 * assets/bountyhunt/textures/entity/<mob>_<tier>.png (tier 1 Medium, 2 Hard, 3 Legendary).
 * Generate them with tools/gen_monster_textures.py. Missing file = vanilla texture, so nothing breaks.
 * The tier is derived from the synced scale (x1 Normal, x1.5, x2, x2.5), so it also works in the lobby preview.
 */
public final class TierTextures {
	private static final Map<String, Optional<Identifier>> CACHE = new HashMap<>();

	private TierTextures() {
	}

	public static int tierOf(float scale) {
		return scale >= 2.4f ? 3 : scale >= 1.9f ? 2 : scale >= 1.4f ? 1 : 0;
	}

	public static Identifier of(LivingEntity entity) {
		if (!((Object) entity instanceof BountyScaled scaled)) {
			return null;
		}
		int tier = tierOf(scaled.bountyhunt$getScale());
		if (tier == 0) {
			return null; // Normal keeps the vanilla look
		}
		String key = Registries.ENTITY_TYPE.getId(entity.getType()).getPath() + "_" + tier;
		return CACHE.computeIfAbsent(key, k -> {
			Identifier id = new Identifier("bountyhunt", "textures/entity/" + k + ".png");
			return MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent() ? Optional.of(id) : Optional.empty();
		}).orElse(null);
	}

	public static void clear() {
		CACHE.clear();
	}
}
