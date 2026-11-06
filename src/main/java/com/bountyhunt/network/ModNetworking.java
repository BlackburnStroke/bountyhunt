package com.bountyhunt.network;

import com.bountyhunt.BountyHuntMod;
import com.bountyhunt.gui.GuiService;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** Networking. The server owns all state; the client only shows it and sends intents. */
public final class ModNetworking {
	// server -> client
	public static final Identifier ANNOUNCE = BountyHuntMod.id("announce");
	public static final Identifier COMPASS = BountyHuntMod.id("compass");
	public static final Identifier CONFIRM = BountyHuntMod.id("confirm");
	public static final Identifier LOBBY = BountyHuntMod.id("lobby");
	// client -> server
	public static final Identifier CONFIRM_REPLY = BountyHuntMod.id("confirm_reply");
	public static final Identifier LOBBY_ACTION = BountyHuntMod.id("lobby_action");

	public static final Identifier SOUND_HORN = new Identifier("event.raid.horn");
	public static final Identifier SOUND_THUNDER = new Identifier("entity.lightning_bolt.thunder");
	public static final Identifier SOUND_COMPLETE = new Identifier("ui.toast.challenge_complete");
	public static final Identifier SOUND_EXPLODE = new Identifier("entity.generic.explode");

	private ModNetworking() {
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(CONFIRM_REPLY, (server, player, handler, buf, responseSender) -> {
			UUID bountyId = buf.readUuid();
			boolean yes = buf.readBoolean();
			server.execute(() -> GuiService.handleConfirm(player, bountyId, yes));
		});
		ServerPlayNetworking.registerGlobalReceiver(LOBBY_ACTION, (server, player, handler, buf, responseSender) -> {
			int ordinal = buf.readVarInt();
			UUID target = buf.readBoolean() ? buf.readUuid() : null;
			server.execute(() -> {
				LobbyAction[] actions = LobbyAction.values();
				if (ordinal >= 0 && ordinal < actions.length) {
					GuiService.handleLobbyAction(player, actions[ordinal], target);
				}
			});
		});
	}

	/** Big centre-screen text on a black bar with a sound. Title only. */
	public static void announce(ServerPlayerEntity player, Text title, Identifier sound) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeText(title);
		buf.writeIdentifier(sound);
		ServerPlayNetworking.send(player, ANNOUNCE, buf);
	}

	public static void sendCompass(ServerPlayerEntity player, boolean hasTarget, double x, double z) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(hasTarget);
		buf.writeDouble(x);
		buf.writeDouble(z);
		ServerPlayNetworking.send(player, COMPASS, buf);
	}

	public static void sendConfirm(ServerPlayerEntity player, UUID bountyId, Text title, Text message) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeUuid(bountyId);
		buf.writeText(title);
		buf.writeText(message);
		ServerPlayNetworking.send(player, CONFIRM, buf);
	}

	public static void sendLobby(ServerPlayerEntity player, LobbySnapshot snapshot) {
		PacketByteBuf buf = PacketByteBufs.create();
		snapshot.write(buf);
		ServerPlayNetworking.send(player, LOBBY, buf);
	}
}
