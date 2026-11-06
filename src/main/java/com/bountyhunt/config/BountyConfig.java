package com.bountyhunt.config;

import com.bountyhunt.BountyHuntMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Balancing values. Written to config/bountyhunt.json on first launch; edit and restart to change.
 */
public class BountyConfig {
	// Main board generation (per Minecraft day)
	public int normalPerDay = 5;
	public int mediumPerDay = 3;
	public int hardPerDay = 1;
	public double legendaryChance = 0.25;
	public int expirationDays = 3;

	// Personal bonus bounties
	public int personalSlots = 3;
	public int maxRerollsPerDay = 3;

	// Parties and hunts
	public int maxPartySize = 16;
	public int spawnRadius = 50;
	public boolean giveCompassOnCommence = true;

	// Target location generation (horizontal distance from world spawn)
	public int minTargetDistance = 600;
	public int maxTargetDistance = 3000;
	public int locationAttempts = 40;

	// Misc
	public int ticksPerDay = 24000;
	public double zeroArmorBaseline = 2.0;
	public int distanceRounding = 10;

	private static BountyConfig instance = new BountyConfig();

	public static BountyConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("bountyhunt.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					BountyConfig loaded = gson.fromJson(reader, BountyConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
			try (Writer writer = Files.newBufferedWriter(path)) {
				gson.toJson(instance, writer);
			}
		} catch (IOException | RuntimeException e) {
			BountyHuntMod.LOGGER.error("Could not read/write bountyhunt.json, using defaults", e);
		}
	}
}
