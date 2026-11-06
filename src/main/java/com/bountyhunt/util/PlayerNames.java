package com.bountyhunt.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.UserCache;

import java.util.Optional;
import java.util.UUID;

public final class PlayerNames {
	private PlayerNames() {
	}

	public static String of(MinecraftServer server, UUID id) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
		if (online != null) {
			return online.getName().getString();
		}
		UserCache cache = server.getUserCache();
		if (cache != null) {
			Optional<GameProfile> profile = cache.getByUuid(id);
			if (profile.isPresent()) {
				return profile.get().getName();
			}
		}
		return "Unknown";
	}
}
