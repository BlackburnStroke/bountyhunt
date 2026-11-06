package com.bountyhunt.item;

import com.bountyhunt.hunt.HuntManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * The needle is driven client-side by the server-pushed target position (see CompassClient).
 * Right-click shows the exact target in the action bar above the experience bar for about 3 seconds.
 */
public class HuntingCompassItem extends Item {
	public HuntingCompassItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient && user instanceof ServerPlayerEntity player) {
			HuntManager.sendCompassReadout(player);
			player.getItemCooldownManager().set(this, 10);
		}
		return TypedActionResult.success(stack, world.isClient);
	}
}
