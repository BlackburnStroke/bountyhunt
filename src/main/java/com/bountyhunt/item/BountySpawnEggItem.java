package com.bountyhunt.item;

import com.bountyhunt.bounty.BountyDifficulty;
import com.bountyhunt.monster.MonsterScaling;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameEvent;
import net.minecraft.world.World;

import java.util.function.Supplier;

/**
 * A bounty spawn egg: spawns a bounty monster scaled to one difficulty tier.
 * Not a SpawnEggItem subclass - those register into vanilla's internal SPAWN_EGGS map
 * and assume fixed colours. Ours spawn through their own code so monster scaling
 * ( tougher stats, bigger model, glow, per-tier loot/model for custom entities ) is applied.
 * Icons reuse minecraft:item/template_spawn_egg: layer 0 tint = mob colour, layer 1 = tier accent.
 */
public class BountySpawnEggItem extends Item {
	private final Supplier<? extends EntityType<? extends MobEntity>> type;
	private final BountyDifficulty difficulty;
	private final int primaryColor;
	private final int spotColor;

	public BountySpawnEggItem(Supplier<? extends EntityType<? extends MobEntity>> type, BountyDifficulty difficulty,
			int primaryColor, int spotColor, Settings settings) {
		super(settings);
		this.type = type;
		this.difficulty = difficulty;
		this.primaryColor = primaryColor;
		this.spotColor = spotColor;
	}

	public EntityType<? extends MobEntity> getType() {
		return type.get();
	}

	public BountyDifficulty getDifficulty() {
		return difficulty;
	}

	/** Inventory icon tint, same pattern as vanilla spawn eggs (0 = body, 1 = spots). */
	public int tint(int tintIndex) {
		return tintIndex == 0 ? primaryColor : tintIndex == 1 ? spotColor : 0xFFFFFF;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (!(world instanceof ServerWorld serverWorld)) {
			return ActionResult.SUCCESS; // client side lets the hand swing
		}
		ItemStack stack = context.getStack();
		PlayerEntity player = context.getPlayer();
		BlockPos pos = context.getBlockPos();
		BlockState state = world.getBlockState(pos);

		// Right-clicking a spawner retargets it, like vanilla eggs do.
		if (state.isOf(Blocks.SPAWNER)) {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			if (blockEntity instanceof MobSpawnerBlockEntity spawner) {
				spawner.setEntityType(getType(), serverWorld.getRandom());
				spawner.markDirty();
				world.updateListeners(pos, state, state, 3);
				consume(stack, player);
				return ActionResult.CONSUME;
			}
			return ActionResult.FAIL;
		}

		// Spawn inside the target block when it has no collision (grass, snow layers), otherwise on the clicked face.
		BlockPos spawnPos = state.getCollisionShape(world, pos).isEmpty() ? pos : pos.offset(context.getSide());
		Entity entity = getType().spawnFromItemStack(serverWorld, stack, player, spawnPos,
				SpawnReason.SPAWN_EGG, true, !spawnPos.equals(pos));
		if (entity == null) {
			return ActionResult.FAIL;
		}
		if (entity instanceof MobEntity mob) {
			MonsterScaling.applyDifficulty(mob, difficulty);
		}
		consume(stack, player);
		if (player != null) {
			player.incrementStat(Stats.USED.getOrCreateStat(this));
		}
		serverWorld.emitGameEvent(entity, GameEvent.ENTITY_PLACE, entity.getPos());
		return ActionResult.CONSUME;
	}

	/** Vanilla 1.20.1 yarn has no decrementUnlessCreative, so the creative check is manual. */
	private static void consume(ItemStack stack, PlayerEntity player) {
		if (player == null || !player.getAbilities().creativeMode) {
			stack.decrement(1);
		}
	}
}
