package com.bountyhunt.gui;

import com.bountyhunt.BountyHuntMod;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;

public final class ModScreens {
	public static final ScreenHandlerType<BoardScreenHandler> BOARD =
			new ExtendedScreenHandlerType<>((syncId, inventory, buf) -> new BoardScreenHandler(syncId));

	private ModScreens() {
	}

	public static void register() {
		Registry.register(Registries.SCREEN_HANDLER, BountyHuntMod.id("board"), BOARD);
	}
}
