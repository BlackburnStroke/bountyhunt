package com.bountyhunt.hunt;

import com.bountyhunt.BountyHuntMod;
import com.bountyhunt.bounty.Bounty;
import com.bountyhunt.bounty.BountyManager;
import com.bountyhunt.config.BountyConfig;
import com.bountyhunt.data.BountyWorldData;
import com.bountyhunt.gui.GuiService;
import com.bountyhunt.item.ModItems;
import com.bountyhunt.monster.MonsterRegistry;
import com.bountyhunt.monster.MonsterScaling;
import com.bountyhunt.monster.SpawnLocator;
import com.bountyhunt.network.ModNetworking;
import com.bountyhunt.party.HuntingParty;
import com.bountyhunt.party.PartyManager;
import com.bountyhunt.player.DownedManager;
import com.bountyhunt.reward.RewardManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

/** Hunt lifecycle: commence, encounter trigger, monster spawn, completion, failure and abandonment. */
public final class HuntManager {
	private HuntManager() {
	}

	public static void register() {
		DownedManager.register();
		ServerTickEvents.END_SERVER_TICK.register(HuntManager::onServerTick);
		ServerLivingEntityEvents.AFTER_DEATH.register(HuntManager::onDeath);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(HuntManager::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
				!(entity instanceof ServerPlayerEntity player) || !DownedManager.onLethalDamage(player, source));
		ServerEntityEvents.ENTITY_LOAD.register(HuntManager::onEntityLoad);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> applyGameMode(newPlayer));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity p = handler.player;
			BountyManager.ensurePersonal(server, p.getUuid());
			PartyManager.onPlayerJoined(p);
			applyGameMode(p);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			GuiService.forget(handler.player.getUuid());
			PartyManager.onPlayerDisconnected(handler.player);
		});
	}

	// ---- Messaging ----------------------------------------------------------------------------

	public static Text tag(String message) {
		return Text.literal("[Bounty] ").formatted(Formatting.GOLD).append(Text.literal(message).formatted(Formatting.YELLOW));
	}

	/** Minor party news stays in chat. */
	public static void broadcast(MinecraftServer server, HuntingParty party, String message) {
		Text text = tag(message);
		for (UUID id : party.members()) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
			if (p != null) {
				p.sendMessage(text, false);
			}
		}
	}

	/** Major events: big text on a black bar plus a sound, instead of chat. */
	public static void announce(MinecraftServer server, HuntingParty party, Text title, Identifier sound) {
		for (UUID id : party.members()) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
			if (p != null) {
				ModNetworking.announce(p, title, sound);
			}
		}
	}

	public static void clearCompass(ServerPlayerEntity player) {
		ModNetworking.sendCompass(player, false, 0, 0);
	}

	// ---- Player actions -----------------------------------------------------------------------

	/** Host locks the roster; coordinates are revealed and the hunt starts waiting for an encounter. */
	public static String commence(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		BountyWorldData data = BountyWorldData.get(server);
		HuntInstance hunt = data.findHuntOf(player.getUuid());
		if (hunt == null) {
			return "You are not in a bounty party.";
		}
		if (!hunt.party().isHost(player.getUuid())) {
			return "Only the host can commence the bounty.";
		}
		Bounty bounty = hunt.bounty();
		if (bounty.state != HuntState.PARTY_FORMING) {
			return "This bounty has already commenced.";
		}

		bounty.state = HuntState.WAITING_FOR_ENCOUNTER;
		hunt.party().markCommenced();
		announce(server, hunt.party(), Text.literal("Hunt Commence!").formatted(Formatting.GOLD), ModNetworking.SOUND_HORN);

		if (BountyConfig.get().giveCompassOnCommence) {
			for (UUID id : hunt.party().members()) {
				ServerPlayerEntity member = server.getPlayerManager().getPlayer(id);
				if (member != null && member.getInventory().count(ModItems.HUNTING_COMPASS) == 0) {
					member.getInventory().offerOrDrop(new ItemStack(ModItems.HUNTING_COMPASS));
				}
			}
		}
		pushCompass(server);
		return null;
	}

	/** Host ends a commenced hunt. The monster is removed and the contract is gone. */
	public static String abandon(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		BountyWorldData data = BountyWorldData.get(server);
		HuntInstance hunt = data.findHuntOf(player.getUuid());
		if (hunt == null) {
			return "You are not in a bounty party.";
		}
		if (!hunt.party().isHost(player.getUuid())) {
			return "Only the host can abandon the hunt.";
		}
		if (!hunt.bounty().state.isLocked()) {
			return "The hunt has not commenced. Use Disband instead.";
		}
		finish(server, hunt, HuntState.ABANDONED, "The host abandoned the hunt.");
		return null;
	}

	/** Called by PartyManager after a member left, was kicked or was removed by betrayal. */
	public static void onMemberRemoved(MinecraftServer server, HuntInstance hunt, UUID id) {
		boolean wasEliminated = hunt.isEliminated(id);
		hunt.forget(id);
		ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
		if (p != null) {
			DownedManager.clearDownedEffects(p);
			if (wasEliminated) {
				p.changeGameMode(GameMode.SURVIVAL);
			}
		} else if (wasEliminated) {
			BountyWorldData.get(server).restoreOnJoin().add(id);
		}
		if (hunt.party().size() > 0) {
			DownedManager.checkWipe(server, hunt);
		}
	}

	/** Where the compass points: the live monster if it is loaded, otherwise the spawn point. */
	public static Vec3d targetOf(MinecraftServer server, HuntInstance hunt) {
		if (hunt.monsterUuid() != null) {
			Entity monster = server.getOverworld().getEntity(hunt.monsterUuid());
			if (monster != null && monster.isAlive()) {
				return monster.getPos();
			}
		}
		BlockPos t = hunt.bounty().target;
		return new Vec3d(t.getX() + 0.5, t.getY(), t.getZ() + 0.5);
	}

	/** Right-click readout. The action bar sits above the XP bar and fades out after about 3 seconds. */
	public static void sendCompassReadout(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		HuntInstance hunt = BountyWorldData.get(server).findHuntOf(player.getUuid());
		if (hunt == null || !hunt.bounty().state.isLocked()) {
			player.sendMessage(Text.literal("Your compass has no target. Commence a bounty first.").formatted(Formatting.GRAY), true);
			return;
		}
		Vec3d t = targetOf(server, hunt);
		double dx = t.x - player.getX();
		double dz = t.z - player.getZ();
		long distance = Math.round(Math.sqrt(dx * dx + dz * dz));
		MutableText text = Text.literal("Target").formatted(Formatting.GOLD, Formatting.BOLD)
				.append(Text.literal("  X: " + (int) Math.floor(t.x) + "  Y: " + (int) Math.floor(t.y) + "  Z: " + (int) Math.floor(t.z)).formatted(Formatting.AQUA))
				.append(Text.literal("  |  ").formatted(Formatting.DARK_GRAY))
				.append(Text.literal(NumberFormat.getIntegerInstance(Locale.US).format(distance)).formatted(Formatting.AQUA))
				.append(Text.literal(" blocks").formatted(Formatting.LIGHT_PURPLE));
		player.sendMessage(text, true);
	}

	// ---- Server tick --------------------------------------------------------------------------

	private static void onServerTick(MinecraftServer server) {
		if (server.getOverworld() == null) {
			return;
		}
		int ticks = server.getTicks();
		DownedManager.tick(server);
		if (ticks % 5 == 0) {
			pushCompass(server); // 4 updates a second keeps the needle on a moving monster
		}
		if (ticks % 10 == 0) {
			GuiService.refreshLobbies(server);
		}
		if (ticks % 20 != 0) {
			return;
		}
		BountyManager.tick(server);

		BountyWorldData data = BountyWorldData.get(server);
		for (HuntInstance hunt : new ArrayList<>(data.hunts().values())) {
			if (hunt.bounty().state == HuntState.WAITING_FOR_ENCOUNTER && !hunt.monsterSpawned()) {
				checkProximity(server, hunt);
			}
		}
		GuiService.refreshBoards(server);
	}

	private static void pushCompass(MinecraftServer server) {
		BountyWorldData data = BountyWorldData.get(server);
		for (HuntInstance hunt : data.hunts().values()) {
			if (!hunt.bounty().state.isLocked()) {
				continue;
			}
			Vec3d t = targetOf(server, hunt);
			for (UUID id : hunt.party().members()) {
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
				if (p != null) {
					ModNetworking.sendCompass(p, true, t.x, t.z);
				}
			}
		}
	}

	private static void checkProximity(MinecraftServer server, HuntInstance hunt) {
		ServerWorld world = server.getOverworld();
		BlockPos t = hunt.bounty().target;
		double r = BountyConfig.get().spawnRadius;
		for (UUID id : hunt.party().members()) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
			if (p == null || p.getServerWorld() != world || hunt.isEliminated(id)) {
				continue;
			}
			double dx = p.getX() - (t.getX() + 0.5);
			double dz = p.getZ() - (t.getZ() + 0.5);
			if (dx * dx + dz * dz <= r * r) {
				spawnMonster(server, hunt);
				return;
			}
		}
	}

	/** Spawns the bounty monster exactly once. State is saved before the entity enters the world. */
	private static void spawnMonster(MinecraftServer server, HuntInstance hunt) {
		ServerWorld world = server.getOverworld();
		Bounty bounty = hunt.bounty();
		EntityType<?> type = Registries.ENTITY_TYPE.get(MonsterRegistry.spawnType(bounty.monsterType));
		Entity entity = type.create(world);
		if (!(entity instanceof MobEntity mob)) {
			BountyHuntMod.LOGGER.error("Bounty {} has a non-mob monster type {}", bounty.id, bounty.monsterType);
			finish(server, hunt, HuntState.ABANDONED, "The bounty target could not be created.");
			return;
		}

		BlockPos t = bounty.target;
		world.getChunk(t); // make sure the target chunk is loaded/generated
		mob.refreshPositionAndAngles(t.getX() + 0.5, t.getY(), t.getZ() + 0.5, world.getRandom().nextFloat() * 360.0f, 0.0f);
		mob.initialize(world, world.getLocalDifficulty(t), SpawnReason.EVENT, null, null);
		MonsterScaling.apply(mob, bounty);

		// Always spawn on the real ground surface, re-read from the chunk now (not the Y saved at creation),
		// at the nearest spot where the scaled hitbox fits. Fall back to lifting only if nothing fits.
		float yaw = mob.getYaw();
		Vec3d spot = SpawnLocator.find(world, mob, t);
		if (spot != null) {
			mob.refreshPositionAndAngles(spot.x, spot.y, spot.z, yaw, 0.0f);
		} else {
			BountyHuntMod.LOGGER.warn("No clear ground near {} for bounty {}; using the saved position", t, bounty.id);
			for (int i = 0; i < 12 && !world.isSpaceEmpty(mob); i++) {
				mob.setPosition(mob.getX(), mob.getY() + 1.0, mob.getZ());
			}
		}

		hunt.setMonster(mob.getUuid());
		bounty.state = HuntState.ACTIVE;
		world.spawnEntityAndPassengers(mob);
		announce(server, hunt.party(), Text.literal("Hunt or be Hunted").formatted(Formatting.DARK_RED), ModNetworking.SOUND_THUNDER);
	}

	/** Ends the hunt, removes the bounty from the board, and clears the monster unless it was slain. */
	public static void finish(MinecraftServer server, HuntInstance hunt, HuntState result, String message) {
		BountyWorldData data = BountyWorldData.get(server);
		Bounty bounty = hunt.bounty();
		bounty.state = result;

		switch (result) {
			case COMPLETED -> announce(server, hunt.party(), Text.literal("The monster have fallen").formatted(Formatting.GOLD), ModNetworking.SOUND_COMPLETE);
			case FAILED -> announce(server, hunt.party(), Text.literal("Your party have been slain").formatted(Formatting.DARK_RED), ModNetworking.SOUND_EXPLODE);
			default -> broadcast(server, hunt.party(), message);
		}

		for (UUID id : hunt.party().members()) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
			boolean wasEliminated = hunt.isEliminated(id);
			if (p != null) {
				clearCompass(p);
				DownedManager.clearDownedEffects(p);
				if (wasEliminated) {
					p.changeGameMode(GameMode.SURVIVAL);
				}
			} else if (wasEliminated) {
				data.restoreOnJoin().add(id);
			}
		}

		if (result == HuntState.COMPLETED) {
			RewardManager.onHuntCompleted(server, hunt);
		} else {
			discardMonster(server, hunt);
		}
		data.hunts().remove(bounty.id);
		data.removeBounty(bounty);
	}

	private static void discardMonster(MinecraftServer server, HuntInstance hunt) {
		if (hunt.monsterUuid() == null) {
			return;
		}
		for (ServerWorld world : server.getWorlds()) {
			Entity e = world.getEntity(hunt.monsterUuid());
			if (e != null) {
				e.discard();
			}
		}
		// If the monster sits in an unloaded chunk, onEntityLoad discards it when that chunk loads.
	}

	/** Eliminated players spectate until the hunt ends; downed state is restored after a reconnect. */
	private static void applyGameMode(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		BountyWorldData data = BountyWorldData.get(server);
		if (data.restoreOnJoin().remove(player.getUuid())) {
			player.changeGameMode(GameMode.SURVIVAL);
		}
		HuntInstance hunt = data.findHuntOf(player.getUuid());
		if (hunt != null && hunt.bounty().state.isLocked() && hunt.isEliminated(player.getUuid())) {
			player.changeGameMode(GameMode.SPECTATOR);
		}
		DownedManager.syncState(server, player);
	}

	// ---- Events -------------------------------------------------------------------------------

	private static void onDeath(LivingEntity entity, DamageSource source) {
		MinecraftServer server = entity.getServer();
		if (server == null) {
			return;
		}
		if (entity instanceof ServerPlayerEntity player) {
			PartyManager.onPlayerDeath(player, source); // betrayal removes the victim from the party first
			HuntInstance hunt = BountyWorldData.get(server).findHuntOf(player.getUuid());
			DownedManager.onPlayerDied(server, hunt, player);
			return;
		}
		if (MonsterScaling.isBountyMonster(entity)) {
			UUID bountyId = MonsterScaling.getBountyId(entity);
			BountyWorldData data = BountyWorldData.get(server);
			HuntInstance hunt = bountyId == null ? null : data.hunts().get(bountyId);
			if (hunt != null && hunt.bounty().state == HuntState.ACTIVE && entity.getUuid().equals(hunt.monsterUuid())) {
				finish(server, hunt, HuntState.COMPLETED, "");
			}
		}
	}

	/** Bounty monsters ignore sunlight burning so undead targets are not killed by the day. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (MonsterScaling.isBountyMonster(entity)
				&& (source.isOf(DamageTypes.ON_FIRE) || source.isOf(DamageTypes.IN_FIRE))
				&& entity.getWorld().isDay()
				&& entity.getWorld().isSkyVisible(entity.getBlockPos())) {
			entity.extinguish();
			return false;
		}
		return true;
	}

	/**
	 * Chunk reload guard: a tagged monster is only kept if it is the one monster its active hunt knows about.
	 * Anything else (hunt ended, duplicate) is removed, so reloading never leaves two monsters.
	 */
	private static void onEntityLoad(Entity entity, ServerWorld world) {
		UUID bountyId = MonsterScaling.getBountyId(entity);
		if (bountyId == null) {
			return;
		}
		BountyWorldData data = BountyWorldData.get(world.getServer());
		HuntInstance hunt = data.hunts().get(bountyId);
		boolean valid = hunt != null
				&& hunt.bounty().state == HuntState.ACTIVE
				&& entity.getUuid().equals(hunt.monsterUuid());
		if (!valid) {
			world.getServer().execute(entity::discard);
		}
	}
}
