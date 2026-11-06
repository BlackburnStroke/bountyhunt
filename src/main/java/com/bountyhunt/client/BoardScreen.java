package com.bountyhunt.client;

import com.bountyhunt.gui.BoardLayout;
import com.bountyhunt.gui.BoardScreenHandler;
import com.bountyhunt.util.Icons;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Custom board panel. Only real slots get a slot background, so the gap between the public grid and the
 * personal row is genuinely blank. Reroll and Lobby are real buttons with an icon and a label.
 */
public class BoardScreen extends HandledScreen<BoardScreenHandler> {
	private ButtonWidget rerollBtn;
	private ButtonWidget lobbyBtn;
	private int lastReroll = -1;
	private int lastParty = -1;

	public BoardScreen(BoardScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = BoardLayout.WIDTH;
		this.backgroundHeight = BoardLayout.HEIGHT;
	}

	@Override
	protected void init() {
		super.init();
		this.titleX = BoardLayout.GRID_X;
		this.titleY = 6;
		rerollBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> press(BoardLayout.BUTTON_REROLL))
				.dimensions(x + BoardLayout.REROLL_X, y + BoardLayout.BTN_Y, BoardLayout.REROLL_W, BoardLayout.BTN_H).build());
		lobbyBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> press(BoardLayout.BUTTON_LOBBY))
				.dimensions(x + BoardLayout.LOBBY_X, y + BoardLayout.BTN_Y, BoardLayout.LOBBY_W, BoardLayout.BTN_H).build());
		lastReroll = -1;
		lastParty = -1;
		updateButtons();
	}

	private void press(int id) {
		if (client != null && client.interactionManager != null) {
			client.interactionManager.clickButton(handler.syncId, id);
		}
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		updateButtons();
	}

	private void updateButtons() {
		boolean can = handler.canReroll();
		boolean party = handler.inParty();
		int stateKey = handler.rerollsLeft() * 2 + (can ? 1 : 0);
		if (stateKey != lastReroll) {
			lastReroll = stateKey;
			rerollBtn.active = can;
			rerollBtn.setMessage(Icons.REROLL.label(Text.literal("Reroll").formatted(can ? Formatting.LIGHT_PURPLE : Formatting.GRAY)));
			rerollBtn.setTooltip(Tooltip.of(Text.literal("Reroll all 3 personal bounties").formatted(Formatting.LIGHT_PURPLE)
					.append(Text.literal("\nRerolls left today: " + handler.rerollsLeft()).formatted(Formatting.GRAY))
					.append(Text.literal("\nNot allowed while one is in progress").formatted(Formatting.DARK_GRAY))));
		}
		int partyKey = party ? 1 : 0;
		if (partyKey != lastParty) {
			lastParty = partyKey;
			lobbyBtn.active = party;
			lobbyBtn.setMessage(Icons.FLAG.label(Text.literal("Lobby").formatted(party ? Formatting.GREEN : Formatting.GRAY)));
			lobbyBtn.setTooltip(Tooltip.of(Text.literal(party ? "Open your party lobby" : "Create or join a party first")
					.formatted(party ? Formatting.GREEN : Formatting.GRAY)));
		}
	}

	@Override
	protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
		int x0 = this.x;
		int y0 = this.y;
		int x1 = x0 + backgroundWidth;
		int y1 = y0 + backgroundHeight;
		ctx.fill(x0, y0, x1, y1, 0xFF000000);
		ctx.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFFFFFFF);
		ctx.fill(x0 + 2, y0 + 2, x1 - 1, y1 - 1, 0xFF555555);
		ctx.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0xFFC6C6C6);

		for (Slot slot : handler.slots) {
			int sx = x0 + slot.x;
			int sy = y0 + slot.y;
			ctx.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF373737);
			ctx.fill(sx, sy, sx + 17, sy + 17, 0xFFFFFFFF);
			ctx.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
		}
		// Thin divider in the blank gap above the personal row.
		ctx.fill(x0 + BoardLayout.GRID_X, y0 + BoardLayout.LABEL_Y - 5, x1 - BoardLayout.GRID_X, y0 + BoardLayout.LABEL_Y - 4, 0xFF8B8B8B);
	}

	@Override
	protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
		ctx.drawText(textRenderer, title, titleX, titleY, 0x404040, false);
		ctx.drawText(textRenderer, Text.literal("Personal Bonus Bounties"), BoardLayout.GRID_X, BoardLayout.LABEL_Y, 0x7A4A00, false);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		renderBackground(ctx);
		super.render(ctx, mouseX, mouseY, delta);
		drawMouseoverTooltip(ctx, mouseX, mouseY);
	}
}
