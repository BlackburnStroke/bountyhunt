package com.bountyhunt.block;

import com.bountyhunt.BountyHuntMod;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
	/** 3x3 notice board. Not craftable and does not generate; place it from the creative inventory (Functional tab). */
	public static final Block BOUNTY_BOARD = new BountyBoardBlock(
			FabricBlockSettings.create().strength(2.5f).sounds(BlockSoundGroup.WOOD).nonOpaque());

	private ModBlocks() {
	}

	public static void register() {
		Registry.register(Registries.BLOCK, BountyHuntMod.id("bounty_board"), BOUNTY_BOARD);
		Registry.register(Registries.ITEM, BountyHuntMod.id("bounty_board"), new BlockItem(BOUNTY_BOARD, new FabricItemSettings()));
	}
}
