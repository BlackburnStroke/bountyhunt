package com.bountyhunt.gui;

import com.bountyhunt.util.Icons;
import com.bountyhunt.bounty.Bounty;
import com.bountyhunt.bounty.BountyManager;
import com.bountyhunt.config.BountyConfig;
import com.bountyhunt.data.BountyWorldData;
import com.bountyhunt.data.PlayerBountyData;
import com.bountyhunt.hunt.HuntInstance;
import com.bountyhunt.hunt.HuntState;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Board menu: 27 public slots, a blank gap, then 3 personal slots, one reroll slot and one lobby slot.
 * The same class runs on the client (display only) and the server (owns the contents and handles clicks).
 */
public class BoardScreenHandler extends ScreenHandler {
	private final Inventory inventory;
	private final ServerPlayerEntity serverPlayer; // null on the client
	private final BlockPos boardPos;
	private final Map<Integer, UUID> slotBounty = new HashMap<>();
	// Synced to the client so the Reroll / Lobby buttons know whether they are usable.
	private final Property rerollsLeftProp = Property.create();
	private final Property canRerollProp = Property.create();
	private final Property inPartyProp = Property.create();

	/** Client side. */
	public BoardScreenHandler(int syncId) {
		this(syncId, new SimpleInventory(BoardLayout.TOTAL), null, BlockPos.ORIGIN);
	}

	/** Server side. */
	public BoardScreenHandler(int syncId, ServerPlayerEntity player, BlockPos boardPos) {
		this(syncId, new SimpleInventory(BoardLayout.TOTAL), player, boardPos);
		refresh();
	}

	private BoardScreenHandler(int syncId, Inventory inventory, ServerPlayerEntity player, BlockPos boardPos) {
		super(ModScreens.BOARD, syncId);
		this.inventory = inventory;
		this.serverPlayer = player;
		this.boardPos = boardPos;
		for (int i = 0; i < BoardLayout.TOTAL; i++) {
			addSlot(new Slot(inventory, i, BoardLayout.x(i), BoardLayout.y(i)) {
				@Override
				public boolean canInsert(ItemStack stack) {
					return false;
				}

				@Override
				public boolean canTakeItems(PlayerEntity playerEntity) {
					return false;
				}
			});
		}
		addProperty(rerollsLeftProp);
		addProperty(canRerollProp);
		addProperty(inPartyProp);
	}

	public int rerollsLeft() {
		return rerollsLeftProp.get();
	}

	public boolean canReroll() {
		return canRerollProp.get() == 1;
	}

	public boolean inParty() {
		return inPartyProp.get() == 1;
	}

	public static void open(ServerPlayerEntity player, BlockPos pos) {
		player.openHandledScreen(new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity p, PacketByteBuf buf) {
			}

			@Override
			public Text getDisplayName() {
				return Text.literal("Bounty Board");
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
				return new BoardScreenHandler(syncId, (ServerPlayerEntity) p, pos);
			}
		});
	}

	public void refresh() {
		if (serverPlayer == null) {
			return;
		}
		MinecraftServer server = serverPlayer.getServer();
		BountyWorldData data = BountyWorldData.get(server);
		PlayerBountyData personal = BountyManager.ensurePersonal(server, serverPlayer.getUuid());
		inventory.clear();
		slotBounty.clear();

		// Open personal parties of other players first, then the main board, hardest first.
		List<Bounty> shown = new ArrayList<>();
		for (PlayerBountyData other : data.players()) {
			if (!other.player.equals(serverPlayer.getUuid())) {
				for (Bounty b : other.bounties) {
					if (b.state == HuntState.PARTY_FORMING) {
						shown.add(b);
					}
				}
			}
		}
		List<Bounty> main = new ArrayList<>(data.main().values());
		main.sort(Comparator.comparingInt((Bounty b) -> -b.difficulty.ordinal()).thenComparingLong(b -> b.createdTick));
		shown.addAll(main);
		for (int i = 0; i < Math.min(BoardLayout.PUBLIC_COUNT, shown.size()); i++) {
			put(i, data, shown.get(i));
		}
		for (int i = 0; i < 3 && i < personal.bounties.size(); i++) {
			put(BoardLayout.IDX_PERSONAL + i, data, personal.bounties.get(i));
		}

		int left = Math.max(0, BountyConfig.get().maxRerollsPerDay - personal.rerollsUsed);
		boolean idle = true;
		for (Bounty b : personal.bounties) {
			if (b.state != HuntState.AVAILABLE) {
				idle = false;
			}
		}
		rerollsLeftProp.set(left);
		canRerollProp.set(left > 0 && idle ? 1 : 0);
		inPartyProp.set(data.findHuntOf(serverPlayer.getUuid()) != null ? 1 : 0);
	}

	private void put(int slot, BountyWorldData data, Bounty b) {
		inventory.setStack(slot, GuiItems.scroll(serverPlayer.getServer(), data, b, boardPos, serverPlayer));
		slotBounty.put(slot, b.id);
	}

	@Override
	public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
		if (serverPlayer == null) {
			return; // client: the server decides and resyncs
		}
		if (slotIndex >= 0 && slotIndex < BoardLayout.TOTAL
				&& (actionType == SlotActionType.PICKUP || actionType == SlotActionType.QUICK_MOVE)) {
			onClick(slotIndex);
		}
		syncState();
	}

	private void onClick(int slot) {
		BountyWorldData data = BountyWorldData.get(serverPlayer.getServer());
		UUID id = slotBounty.get(slot);
		if (id != null) {
			clickBounty(data, id);
		}
	}

	/** The Reroll and Lobby buttons are real client buttons; they arrive through the vanilla button channel. */
	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (serverPlayer == null) {
			return false;
		}
		MinecraftServer server = serverPlayer.getServer();
		if (id == BoardLayout.BUTTON_REROLL) {
			String error = BountyManager.reroll(server, serverPlayer.getUuid());
			if (error != null) {
				serverPlayer.sendMessage(GuiItems.plain(error, Formatting.RED), true);
			}
			refresh();
			return true;
		}
		if (id == BoardLayout.BUTTON_LOBBY) {
			String error = GuiService.openLobby(serverPlayer);
			if (error != null) {
				serverPlayer.sendMessage(GuiItems.plain(error, Formatting.RED), true);
			}
			return true;
		}
		return false;
	}

	private void clickBounty(BountyWorldData data, UUID id) {
		Bounty b = data.findBounty(id);
		if (b == null) {
			refresh();
			return;
		}
		HuntInstance mine = data.findHuntOf(serverPlayer.getUuid());
		if (mine != null) {
			if (mine.bounty().id.equals(id)) {
				GuiService.openLobby(serverPlayer);
			} else {
				serverPlayer.sendMessage(GuiItems.plain("You are already in a party for another bounty.", Formatting.RED), true);
			}
		} else if (b.state == HuntState.AVAILABLE) {
			GuiService.askConfirm(serverPlayer, b, false);
		} else if (b.state == HuntState.PARTY_FORMING) {
			GuiService.askConfirm(serverPlayer, b, true);
		} else {
			serverPlayer.sendMessage(GuiItems.plain("That hunt is already underway.", Formatting.RED), true);
		}
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}
}
