package com.bountyhunt;

import com.bountyhunt.block.ModBlocks;
import com.bountyhunt.command.BountyCommands;
import com.bountyhunt.config.BountyConfig;
import com.bountyhunt.gui.ModScreens;
import com.bountyhunt.hunt.HuntManager;
import com.bountyhunt.item.ModItems;
import com.bountyhunt.monster.ModEntities;
import com.bountyhunt.network.ModNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BountyHuntMod implements ModInitializer {
	public static final String MOD_ID = "bountyhunt";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		BountyConfig.load();
		ModBlocks.register();
		ModItems.register();
		ModEntities.register();
		ModScreens.register();
		ModNetworking.registerServerReceivers();
		HuntManager.register();
		BountyCommands.register();
		LOGGER.info("Minecraft Bounty Hunt initialised");
	}
}
