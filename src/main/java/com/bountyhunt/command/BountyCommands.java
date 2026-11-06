package com.bountyhunt.command;

import com.bountyhunt.bounty.BountyManager;
import com.bountyhunt.data.BountyWorldData;
import com.bountyhunt.gui.GuiService;
import com.bountyhunt.hunt.HuntInstance;
import com.bountyhunt.hunt.HuntManager;
import com.bountyhunt.party.PartyManager;
import com.bountyhunt.util.PlayerNames;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;
import java.util.function.Function;

/** /bounty lobby | leave | abandon | status, plus /bounty regenerate (ops) for testing. */
public final class BountyCommands {
	private BountyCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("bounty")
						.then(CommandManager.literal("leave").executes(ctx -> run(ctx, PartyManager::leave)))
						.then(CommandManager.literal("abandon").executes(ctx -> run(ctx, HuntManager::abandon)))
						.then(CommandManager.literal("lobby").executes(ctx -> run(ctx, GuiService::openLobby)))
						.then(CommandManager.literal("status").executes(BountyCommands::status))
						.then(CommandManager.literal("regenerate")
								.requires(source -> source.hasPermissionLevel(2))
								.executes(BountyCommands::regenerate))));
	}

	private static int run(CommandContext<ServerCommandSource> ctx, Function<ServerPlayerEntity, String> action) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		String error = action.apply(player);
		if (error != null) {
			ctx.getSource().sendError(Text.literal(error));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Text.literal("Done."), false);
		return 1;
	}

	private static int status(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		MinecraftServer server = ctx.getSource().getServer();
		HuntInstance hunt = BountyWorldData.get(server).findHuntOf(player.getUuid());
		if (hunt == null) {
			ctx.getSource().sendFeedback(() -> Text.literal("You are not in a bounty party."), false);
			return 1;
		}
		StringBuilder members = new StringBuilder();
		for (UUID id : hunt.party().members()) {
			if (members.length() > 0) {
				members.append(", ");
			}
			members.append(PlayerNames.of(server, id));
			if (hunt.party().isHost(id)) {
				members.append(" (host)");
			}
		}
		String message = hunt.bounty().difficulty.label() + " " + hunt.bounty().monsterType.getPath()
				+ " | " + hunt.bounty().state + " | " + members;
		ctx.getSource().sendFeedback(() -> Text.literal(message), false);
		return 1;
	}

	private static int regenerate(CommandContext<ServerCommandSource> ctx) {
		BountyManager.regenerateMain(ctx.getSource().getServer());
		ctx.getSource().sendFeedback(() -> Text.literal("Main bounty board regenerated."), true);
		return 1;
	}
}
