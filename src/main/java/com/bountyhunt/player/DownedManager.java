package com.bountyhunt.player;

import com.bountyhunt.data.BountyWorldData;
import com.bountyhunt.hunt.HuntInstance;
import com.bountyhunt.hunt.HuntManager;
import com.bountyhunt.hunt.HuntState;
import com.bountyhunt.monster.BountyScaled;
import com.bountyhunt.party.HuntingParty;
import com.bountyhunt.util.PlayerNames;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 7: Alive -> Downed -> Spectator.
 * - A lethal hit on a hunter in a commenced hunt downs them instead of killing them (6 hearts, 25% speed, crawling).
 * - Before the monster spawns downs are unlimited; after it spawns each down counts and the 4th sends the player to spectator.
 * - A teammate revives by holding right-click on the downed player for 5 seconds; they return with 50% max health.
 * - Monsters only keep targeting a downed player while that player is the nearest valid hunter.
 */
public final class DownedManager {
	private static final UUID SPEED_ID = UUID.fromString("c3d1a1f6-6b4c-4f0e-9d58-2d9b1f0a7e11");
	private static final int REVIVE_TICKS = 100; // 5 seconds
	private static final int GRACE_TICKS = 8;    // the client repeats the interaction roughly every 4 ticks while held
	private static final double REVIVE_RANGE_SQ = 4.5 * 4.5;
	private static final int MAX_DOWNS_AFTER_SPAWN = 3;

	private static final class Session {
		final UUID reviver;
		final int start;
		int last;

		Session(UUID reviver, int now) {
			this.reviver = reviver;
			this.start = now;
			this.last = now;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	private DownedManager() {
	}

	public static boolean isDowned(PlayerEntity player) {
		return (Object) player instanceof BountyScaled scaled && scaled.bountyhunt$isDowned();
	}

	public static void register() {
		// Downed players cannot fight, build or use items.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> isDowned(player) ? ActionResult.FAIL : ActionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> isDowned(player) ? ActionResult.FAIL : ActionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> isDowned(player) ? ActionResult.FAIL : ActionResult.PASS);
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			return isDowned(player) ? TypedActionResult.fail(stack) : TypedActionResult.pass(stack);
		});
		UseEntityCallback.EVENT.register(DownedManager::onUseEntity);
	}

	// ---- Going down ---------------------------------------------------------------------------

	/** Returns true if the lethal damage was converted into a down/elimination, so death must be cancelled. */
	public static boolean onLethalDamage(ServerPlayerEntity player, DamageSource source) {
		MinecraftServer server = player.getServer();
		BountyWorldData data = BountyWorldData.get(server);
		HuntInstance hunt = data.findHuntOf(player.getUuid());
		if (hunt == null || !hunt.bounty().state.isLocked() || hunt.isEliminated(player.getUuid())) {
			return false;
		}
		if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false; // void and /kill still kill
		}
		HuntingParty party = hunt.party();
		if (party.isHost(player.getUuid()) && source.getAttacker() instanceof ServerPlayerEntity killer
				&& !killer.getUuid().equals(player.getUuid()) && party.contains(killer.getUuid())) {
			return false; // betrayal: a real kill, handled by PartyManager
		}
		UUID id = player.getUuid();
		if (hunt.isDowned(id)) {
			eliminate(server, hunt, player, " was finished off.");
			return true;
		}
		if (hunt.bounty().state == HuntState.ACTIVE) {
			if (hunt.downs(id) >= MAX_DOWNS_AFTER_SPAWN) {
				eliminate(server, hunt, player, " has fallen for the last time.");
				return true;
			}
			hunt.addDown(id);
		}
		down(server, hunt, player);
		return true;
	}

	private static void down(MinecraftServer server, HuntInstance hunt, ServerPlayerEntity player) {
		hunt.setDowned(player.getUuid(), true);
		player.setHealth(Math.min(12.0f, player.getMaxHealth())); // 6 hearts
		player.extinguish();
		player.clearStatusEffects();
		applyDownedEffects(player);
		player.sendMessage(Text.literal("You are down! Hold on until a teammate revives you.").formatted(Formatting.RED), true);
		HuntManager.broadcast(server, hunt.party(), PlayerNames.of(server, player.getUuid())
				+ " is down! Hold right-click on them for 5 seconds to revive.");
		checkWipe(server, hunt);
	}

	/** A hunter that died for real (void, /kill) while in a hunt is eliminated. */
	public static void onPlayerDied(MinecraftServer server, HuntInstance hunt, ServerPlayerEntity player) {
		if (hunt == null || !hunt.bounty().state.isLocked() || hunt.isEliminated(player.getUuid())) {
			return;
		}
		hunt.eliminate(player.getUuid());
		hunt.setDowned(player.getUuid(), false);
		SESSIONS.remove(player.getUuid());
		HuntManager.broadcast(server, hunt.party(), player.getName().getString() + " has been eliminated.");
		checkWipe(server, hunt);
	}

	public static void eliminate(MinecraftServer server, HuntInstance hunt, ServerPlayerEntity player, String reason) {
		UUID id = player.getUuid();
		hunt.eliminate(id);
		hunt.setDowned(id, false);
		SESSIONS.remove(id);
		clearDownedEffects(player);
		player.setHealth(player.getMaxHealth());
		player.changeGameMode(GameMode.SPECTATOR);
		HuntManager.broadcast(server, hunt.party(), player.getName().getString() + reason);
		checkWipe(server, hunt);
	}

	/**
	 * The hunt fails when nobody is left standing: every member is a spectator, or the rest are all downed
	 * with nobody able to revive them (otherwise the hunt could never end).
	 */
	public static void checkWipe(MinecraftServer server, HuntInstance hunt) {
		if (!hunt.bounty().state.isLocked()) {
			return;
		}
		for (UUID id : hunt.party().members()) {
			if (!hunt.isEliminated(id) && !hunt.isDowned(id)) {
				return;
			}
		}
		HuntManager.finish(server, hunt, HuntState.FAILED, "");
	}

	// ---- Effects ------------------------------------------------------------------------------

	public static void applyDownedEffects(ServerPlayerEntity player) {
		if ((Object) player instanceof BountyScaled scaled) {
			scaled.bountyhunt$setDowned(true);
		}
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null && speed.getModifier(SPEED_ID) == null) {
			speed.addPersistentModifier(new EntityAttributeModifier(SPEED_ID, "Bounty Hunt downed",
					-0.75, EntityAttributeModifier.Operation.MULTIPLY_TOTAL)); // 25% speed
		}
		player.setSprinting(false);
	}

	public static void clearDownedEffects(ServerPlayerEntity player) {
		if ((Object) player instanceof BountyScaled scaled) {
			scaled.bountyhunt$setDowned(false);
		}
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SPEED_ID);
		}
	}

	/** On join/respawn: restore or clean up downed effects to match the hunt's saved state. */
	public static void syncState(MinecraftServer server, ServerPlayerEntity player) {
		HuntInstance hunt = BountyWorldData.get(server).findHuntOf(player.getUuid());
		if (hunt != null && hunt.bounty().state.isLocked() && hunt.isDowned(player.getUuid())) {
			applyDownedEffects(player);
		} else {
			clearDownedEffects(player);
		}
	}

	// ---- Reviving -----------------------------------------------------------------------------

	private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hit) {
		if (isDowned(player)) {
			return ActionResult.FAIL;
		}
		if (hand != Hand.MAIN_HAND || !(entity instanceof PlayerEntity target) || !isDowned(target)) {
			return ActionResult.PASS;
		}
		if (world.isClient) {
			return ActionResult.SUCCESS; // consume it so held items are not used; the server gets the interaction
		}
		ServerPlayerEntity reviver = (ServerPlayerEntity) player;
		ServerPlayerEntity downed = (ServerPlayerEntity) target;
		MinecraftServer server = reviver.getServer();
		HuntInstance hunt = BountyWorldData.get(server).findHuntOf(downed.getUuid());
		if (hunt == null || !hunt.party().contains(reviver.getUuid()) || !hunt.isDowned(downed.getUuid())
				|| hunt.isDowned(reviver.getUuid()) || hunt.isEliminated(reviver.getUuid())) {
			return ActionResult.PASS;
		}
		if (reviver.squaredDistanceTo(downed) > REVIVE_RANGE_SQ) {
			return ActionResult.PASS;
		}
		int now = server.getTicks();
		Session session = SESSIONS.get(downed.getUuid());
		if (session != null && !session.reviver.equals(reviver.getUuid()) && now - session.last <= GRACE_TICKS) {
			reviver.sendMessage(Text.literal("Someone is already reviving them.").formatted(Formatting.RED), true);
			return ActionResult.SUCCESS; // only one active revival per downed player
		}
		if (session == null || !session.reviver.equals(reviver.getUuid()) || now - session.last > GRACE_TICKS) {
			session = new Session(reviver.getUuid(), now);
			SESSIONS.put(downed.getUuid(), session);
		}
		session.last = now;
		return ActionResult.SUCCESS;
	}

	public static void tick(MinecraftServer server) {
		if (!SESSIONS.isEmpty()) {
			tickRevives(server);
		}
		if (server.getTicks() % 5 == 0) {
			tickTargeting(server);
		}
	}

	private static void tickRevives(MinecraftServer server) {
		BountyWorldData data = BountyWorldData.get(server);
		int now = server.getTicks();
		for (Map.Entry<UUID, Session> entry : new ArrayList<>(SESSIONS.entrySet())) {
			UUID targetId = entry.getKey();
			Session s = entry.getValue();
			ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetId);
			ServerPlayerEntity reviver = server.getPlayerManager().getPlayer(s.reviver);
			HuntInstance hunt = target == null ? null : data.findHuntOf(targetId);
			boolean valid = target != null && reviver != null && hunt != null
					&& hunt.bounty().state.isLocked()
					&& hunt.isDowned(targetId)
					&& hunt.party().contains(s.reviver)
					&& !hunt.isDowned(s.reviver) && !hunt.isEliminated(s.reviver)
					&& reviver.isAlive()
					&& reviver.squaredDistanceTo(target) <= REVIVE_RANGE_SQ
					&& now - s.last <= GRACE_TICKS;
			if (!valid) {
				SESSIONS.remove(targetId);
				if (reviver != null) {
					reviver.sendMessage(Text.literal("Revive interrupted.").formatted(Formatting.RED), true);
				}
				continue;
			}
			int progress = now - s.start;
			if (progress >= REVIVE_TICKS) {
				SESSIONS.remove(targetId);
				revive(server, hunt, target, reviver);
				continue;
			}
			if (progress % 4 == 0) {
				int filled = progress * 10 / REVIVE_TICKS;
				String bar = "\u2588".repeat(filled) + "\u2591".repeat(10 - filled);
				String secs = String.format(Locale.US, "%.1f", progress / 20.0) + "s / 5s";
				reviver.sendMessage(Text.literal("Reviving " + target.getName().getString() + "  ").formatted(Formatting.GREEN)
						.append(Text.literal(bar).formatted(Formatting.YELLOW)).append(Text.literal("  " + secs).formatted(Formatting.WHITE)), true);
				target.sendMessage(Text.literal("Being revived by " + reviver.getName().getString() + "  ").formatted(Formatting.GREEN)
						.append(Text.literal(bar).formatted(Formatting.YELLOW)), true);
			}
		}
	}

	private static void revive(MinecraftServer server, HuntInstance hunt, ServerPlayerEntity target, ServerPlayerEntity reviver) {
		hunt.setDowned(target.getUuid(), false);
		clearDownedEffects(target);
		target.setHealth(target.getMaxHealth() * 0.5f); // 50% of max, including equipment bonuses
		HuntManager.broadcast(server, hunt.party(), target.getName().getString() + " was revived by " + reviver.getName().getString() + "!");
	}

	// ---- Monster targeting --------------------------------------------------------------------

	/** A downed player is only targeted while they are the nearest valid hunter; otherwise the monster switches. */
	private static void tickTargeting(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		for (HuntInstance hunt : BountyWorldData.get(server).hunts().values()) {
			if (hunt.bounty().state != HuntState.ACTIVE || hunt.monsterUuid() == null) {
				continue;
			}
			if (!(world.getEntity(hunt.monsterUuid()) instanceof MobEntity mob)) {
				continue;
			}
			LivingEntity current = mob.getTarget();
			if (!(current instanceof ServerPlayerEntity targeted) || !hunt.isDowned(targeted.getUuid())) {
				continue;
			}
			ServerPlayerEntity nearest = null;
			double best = Double.MAX_VALUE;
			for (UUID id : hunt.party().members()) {
				ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
				if (p == null || p.getServerWorld() != world || hunt.isEliminated(id) || !p.isAlive()) {
					continue;
				}
				double d = p.squaredDistanceTo(mob);
				if (d < best) {
					best = d;
					nearest = p;
				}
			}
			if (nearest != null && nearest != targeted) {
				mob.setTarget(nearest);
			}
		}
	}
}
