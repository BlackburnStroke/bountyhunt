package com.bountyhunt.gui;

import com.bountyhunt.bounty.Bounty;
import com.bountyhunt.bounty.BountyText;
import com.bountyhunt.config.BountyConfig;
import com.bountyhunt.data.BountyWorldData;
import com.bountyhunt.hunt.HuntInstance;
import com.bountyhunt.hunt.HuntManager;
import com.bountyhunt.hunt.HuntState;
import com.bountyhunt.network.LobbyAction;
import com.bountyhunt.network.LobbySnapshot;
import com.bountyhunt.network.ModNetworking;
import com.bountyhunt.party.HuntingParty;
import com.bountyhunt.party.PartyManager;
import com.bountyhunt.util.PlayerNames;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-side glue for the board menu, the party confirmation prompt and the lobby screen. */
public final class GuiService {
	private static final Map<UUID, BlockPos> LAST_BOARD = new HashMap<>();
	private static final Map<UUID, UUID> PENDING_CONFIRM = new HashMap<>();
	private static final Set<UUID> LOBBY_VIEWERS = new HashSet<>();

	private GuiService() {
	}

	// ---- Board --------------------------------------------------------------------------------

	public static void openBoard(ServerPlayerEntity player, BlockPos pos) {
		LAST_BOARD.put(player.getUuid(), pos);
		BoardScreenHandler.open(player, pos);
	}

	public static void refreshBoards(MinecraftServer server) {
		for (ServerPlayerEntity p : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
			if (p.currentScreenHandler instanceof BoardScreenHandler board) {
				board.refresh();
			}
		}
	}

	// ---- "Create a party?" prompt (a vanilla confirm screen on the client) ---------------------

	public static void askConfirm(ServerPlayerEntity player, Bounty b, boolean joining) {
		PENDING_CONFIRM.put(player.getUuid(), b.id);
		BlockPos ref = LAST_BOARD.getOrDefault(player.getUuid(), player.getBlockPos());
		MutableText message = Text.empty().append(BountyText.display(b)).append(Text.literal("\n"))
				.append(Text.literal("Reward " + Math.round(b.difficulty.statMultiplier() * 100) + "%").formatted(Formatting.YELLOW))
				.append(Text.literal("   Size x" + b.difficulty.sizeMultiplier()).formatted(Formatting.GRAY))
				.append(Text.literal("\n" + GuiItems.distanceText(b, ref)).formatted(Formatting.AQUA));
		if (joining) {
			HuntInstance hunt = BountyWorldData.get(player.getServer()).hunts().get(b.id);
			if (hunt != null) {
				message.append(Text.literal("\nHost: " + PlayerNames.of(player.getServer(), hunt.party().currentHost())
						+ " (" + hunt.party().size() + " in party)").formatted(Formatting.WHITE));
			}
		}
		Text title = Text.literal(joining ? "Join this party?" : "Create a party for this bounty?");
		ModNetworking.sendConfirm(player, b.id, title, message);
	}

	public static void handleConfirm(ServerPlayerEntity player, UUID bountyId, boolean yes) {
		UUID pending = PENDING_CONFIRM.remove(player.getUuid());
		if (!bountyId.equals(pending)) {
			return;
		}
		BlockPos board = LAST_BOARD.get(player.getUuid());
		if (!yes) {
			if (board != null) {
				openBoard(player, board); // nothing happens, keep browsing
			}
			return;
		}
		String error = PartyManager.accept(player, bountyId);
		if (error != null) {
			player.sendMessage(GuiItems.plain(error, Formatting.RED), true);
			if (board != null) {
				openBoard(player, board);
			}
			return;
		}
		openLobby(player);
	}

	// ---- Lobby --------------------------------------------------------------------------------

	/** Returns an error message or null. */
	public static String openLobby(ServerPlayerEntity player) {
		if (BountyWorldData.get(player.getServer()).findHuntOf(player.getUuid()) == null) {
			return "You are not in a bounty party.";
		}
		LOBBY_VIEWERS.add(player.getUuid());
		sendLobby(player, true);
		return null;
	}

	private static void sendLobby(ServerPlayerEntity player, boolean open) {
		LobbySnapshot s = snapshot(player.getServer(), player);
		s.open = open;
		ModNetworking.sendLobby(player, s);
	}

	private static LobbySnapshot snapshot(MinecraftServer server, ServerPlayerEntity viewer) {
		LobbySnapshot s = new LobbySnapshot();
		HuntInstance hunt = BountyWorldData.get(server).findHuntOf(viewer.getUuid());
		if (hunt == null) {
			return s; // inHunt = false
		}
		Bounty b = hunt.bounty();
		HuntingParty party = hunt.party();
		s.inHunt = true;
		s.monster = b.monsterType;
		s.difficulty = b.difficulty;
		s.state = b.state;
		s.title = BountyText.display(b);
		s.reward = (int) Math.round(b.difficulty.statMultiplier() * 100);
		s.size = b.difficulty.sizeMultiplier();
		s.hasCoords = b.state.isLocked();
		s.coords = b.target;
		s.viewerIsHost = party.isHost(viewer.getUuid());
		s.canKick = s.viewerIsHost && !party.hostByBetrayal() && b.state == HuntState.PARTY_FORMING;
		s.hasBoard = LAST_BOARD.containsKey(viewer.getUuid());
		s.maxMembers = BountyConfig.get().maxPartySize;
		for (UUID id : party.members()) {
			int status = hunt.isEliminated(id) ? LobbySnapshot.ELIMINATED : hunt.isDowned(id) ? LobbySnapshot.DOWNED : LobbySnapshot.ALIVE;
			s.members.add(new LobbySnapshot.Member(id, PlayerNames.of(server, id), party.isHost(id), status));
		}
		return s;
	}

	/** Called about every half second: keeps open lobby screens live, closes them if the viewer left the hunt. */
	public static void refreshLobbies(MinecraftServer server) {
		for (UUID id : new ArrayList<>(LOBBY_VIEWERS)) {
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
			if (p == null) {
				LOBBY_VIEWERS.remove(id);
				continue;
			}
			LobbySnapshot s = snapshot(server, p);
			ModNetworking.sendLobby(p, s);
			if (!s.inHunt) {
				LOBBY_VIEWERS.remove(id);
			}
		}
	}

	public static void forget(UUID player) {
		LOBBY_VIEWERS.remove(player);
		PENDING_CONFIRM.remove(player);
	}

	public static void handleLobbyAction(ServerPlayerEntity player, LobbyAction action, UUID target) {
		UUID id = player.getUuid();
		String error = null;
		boolean leaveScreen = false;
		switch (action) {
			case COMMENCE -> error = HuntManager.commence(player);
			case LEAVE -> {
				error = PartyManager.leave(player);
				leaveScreen = error == null;
			}
			case DISBAND -> {
				error = PartyManager.disband(player);
				leaveScreen = error == null;
			}
			case ABANDON -> {
				error = HuntManager.abandon(player);
				leaveScreen = error == null;
			}
			case KICK -> error = target == null ? "No member selected." : PartyManager.kick(player, target);
			case BACK -> leaveScreen = true;
			case CLOSED -> {
				LOBBY_VIEWERS.remove(id);
				return;
			}
		}
		if (error != null) {
			player.sendMessage(GuiItems.plain(error, Formatting.RED), true);
		}
		if (leaveScreen) {
			LOBBY_VIEWERS.remove(id);
			BlockPos board = LAST_BOARD.get(id);
			if (board != null) {
				openBoard(player, board);
			} else {
				sendLobby(player, false); // inHunt may be false now, which closes the screen
			}
			return;
		}
		if (LOBBY_VIEWERS.contains(id)) {
			sendLobby(player, false);
		}
	}
}
