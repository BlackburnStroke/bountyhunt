package com.bountyhunt.client;

import com.bountyhunt.util.Icons;
import com.bountyhunt.hunt.HuntState;
import com.bountyhunt.monster.BountyScaled;
import com.bountyhunt.monster.BountyZombieEntity;
import com.bountyhunt.monster.MonsterRegistry;
import com.bountyhunt.network.LobbyAction;
import com.bountyhunt.network.LobbySnapshot;
import com.bountyhunt.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Party lobby: the target monster's model on the left, the roster on the right, controls at the bottom left. */
public class LobbyScreen extends Screen {
	private static final int ROW_H = 22;

	private LobbySnapshot snap;
	private LivingEntity model;
	private Identifier modelType;
	private int scroll;
	private ButtonWidget commenceBtn;
	private ButtonWidget leaveBtn;
	private ButtonWidget endBtn;
	private ButtonWidget backBtn;
	private final List<Object[]> kickRects = new ArrayList<>(); // {x1, y1, x2, y2, UUID}

	public LobbyScreen(LobbySnapshot snap) {
		super(Text.literal("Hunt Lobby"));
		this.snap = snap;
	}

	/** Entry point for every lobby packet. */
	public static void handle(MinecraftClient client, LobbySnapshot s) {
		if (!s.inHunt) {
			if (client.currentScreen instanceof LobbyScreen) {
				client.setScreen(null);
			}
			return;
		}
		if (client.currentScreen instanceof LobbyScreen screen) {
			screen.update(s);
		} else if (s.open) {
			client.setScreen(new LobbyScreen(s));
		}
	}

	private void update(LobbySnapshot s) {
		this.snap = s;
		if (commenceBtn != null) {
			updateButtons();
		}
	}

	private void send(LobbyAction action, UUID target) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(action.ordinal());
		buf.writeBoolean(target != null);
		if (target != null) {
			buf.writeUuid(target);
		}
		ClientPlayNetworking.send(ModNetworking.LOBBY_ACTION, buf);
	}

	@Override
	public void removed() {
		send(LobbyAction.CLOSED, null);
	}

	// ---- Layout -------------------------------------------------------------------------------

	private int leftW() {
		return width / 2 - 30;
	}

	private int rightX() {
		return width / 2 + 10;
	}

	private int rightW() {
		return width / 2 - 30;
	}

	@Override
	protected void init() {
		int bw = (leftW() - 6) / 2;
		int x0 = 20;
		int x1 = x0 + bw + 6;
		int y1 = height - 30;
		int y0 = y1 - 24;
		commenceBtn = addDrawableChild(ButtonWidget.builder(Icons.SWORDS.label(Text.literal("Commence").formatted(Formatting.GREEN)),
				b -> send(LobbyAction.COMMENCE, null)).dimensions(x0, y0, bw, 20).build());
		leaveBtn = addDrawableChild(ButtonWidget.builder(Icons.DOOR.label(Text.literal("Leave Party").formatted(Formatting.YELLOW)),
				b -> send(LobbyAction.LEAVE, null)).dimensions(x1, y0, bw, 20).build());
		endBtn = addDrawableChild(ButtonWidget.builder(Icons.SKULL.label(Text.literal("Disband").formatted(Formatting.RED)), b ->
				send(snap.state.isLocked() ? LobbyAction.ABANDON : LobbyAction.DISBAND, null)).dimensions(x0, y1, bw, 20).build());
		backBtn = addDrawableChild(ButtonWidget.builder(Icons.BACK.label(Text.literal("Back to Board")),
				b -> send(LobbyAction.BACK, null)).dimensions(x1, y1, bw, 20).build());
		updateButtons();
	}

	private void updateButtons() {
		boolean forming = snap.state == HuntState.PARTY_FORMING;
		commenceBtn.active = snap.viewerIsHost && forming;
		commenceBtn.setMessage(forming
				? Icons.SWORDS.label(Text.literal("Commence").formatted(snap.viewerIsHost ? Formatting.GREEN : Formatting.GRAY))
				: Icons.SWORDS.label(Text.literal("In progress").formatted(Formatting.GRAY)));
		endBtn.active = snap.viewerIsHost;
		endBtn.setMessage(Icons.SKULL.label(Text.literal(snap.state.isLocked() ? "Abandon" : "Disband").formatted(snap.viewerIsHost ? Formatting.RED : Formatting.GRAY)));
		backBtn.visible = snap.hasBoard;
	}

	// ---- Rendering ----------------------------------------------------------------------------

	private void ensureModel() {
		if (model != null && snap.monster.equals(modelType)) {
			return;
		}
		modelType = snap.monster;
		model = null;
		if (client == null || client.world == null) {
			return;
		}
		Entity e = Registries.ENTITY_TYPE.get(MonsterRegistry.spawnType(modelType)).create(client.world);
		if (e instanceof BountyZombieEntity zombie) {
			zombie.setTier(snap.difficulty.ordinal()); // same model the real monster will have
		}
		if (e instanceof LivingEntity living) {
			model = living;
			if ((Object) living instanceof BountyScaled scaled) {
				scaled.bountyhunt$setScale(snap.size); // same scale the real monster will have
			}
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		renderBackground(ctx);
		ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Hunt Lobby").formatted(Formatting.GOLD, Formatting.BOLD), width / 2, 12, 0xFFFFFF);

		int lx = 20;
		int ly = 32;
		int lw = leftW();
		int lh = height - 32 - 64;
		ctx.fill(lx, ly, lx + lw, ly + lh, 0x88000000);
		ctx.drawCenteredTextWithShadow(textRenderer, snap.title, lx + lw / 2, ly + 8, 0xFFFFFF);

		ensureModel();
		if (model != null) {
			EntityType<?> type = model.getType();
			int size = (int) Math.max(8, Math.min(90, lh * 0.5f / (type.getHeight() * 2.5f)));
			int cx = lx + lw / 2;
			int feet = ly + lh - 34;
			InventoryScreen.drawEntity(ctx, cx, feet, size, (float) (cx - mouseX), (float) (feet - 50 - mouseY), model);
		}
		ctx.drawCenteredTextWithShadow(textRenderer,
				Text.literal("Reward " + snap.reward + "%").formatted(Formatting.YELLOW)
						.append(Text.literal("   Size x" + snap.size).formatted(Formatting.GRAY)),
				lx + lw / 2, ly + lh - 24, 0xFFFFFF);
		String status = snap.hasCoords
				? "Target: " + snap.coords.getX() + ", " + snap.coords.getY() + ", " + snap.coords.getZ()
				: "Location hidden until commenced";
		ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status).formatted(snap.hasCoords ? Formatting.AQUA : Formatting.GRAY),
				lx + lw / 2, ly + lh - 12, 0xFFFFFF);

		drawRoster(ctx);
		super.render(ctx, mouseX, mouseY, delta);
	}

	private void drawRoster(DrawContext ctx) {
		kickRects.clear();
		int px = rightX();
		int py = 32;
		int pw = rightW();
		int ph = height - 32 - 20;
		ctx.fill(px, py, px + pw, py + ph, 0x88000000);
		ctx.drawTextWithShadow(textRenderer,
				Text.literal("Party  " + snap.members.size() + " / " + snap.maxMembers).formatted(Formatting.GOLD), px + 8, py + 7, 0xFFFFFF);

		int top = py + 22;
		int bottom = py + ph - 4;
		clampScroll(top, bottom);
		UUID self = client != null && client.player != null ? client.player.getUuid() : null;
		ctx.enableScissor(px, top, px + pw, bottom);
		int y = top - scroll;
		for (LobbySnapshot.Member m : snap.members) {
			if (y + ROW_H >= top && y <= bottom) {
				Identifier skin = skinOf(m.id());
				PlayerSkinDrawer.draw(ctx, skin, px + 6, y + 2, 18);
				Formatting nameColor = m.status() == LobbySnapshot.ELIMINATED ? Formatting.DARK_GRAY
						: m.status() == LobbySnapshot.DOWNED ? Formatting.RED : Formatting.WHITE;
				ctx.drawTextWithShadow(textRenderer, Text.literal(m.name()).formatted(nameColor), px + 30, y + 3, 0xFFFFFF);
				MutableText tag = Text.empty();
				if (m.host()) {
					tag.append(Icons.CROWN.label(Text.literal("Host").formatted(Formatting.GOLD)));
				}
				if (m.status() != LobbySnapshot.ALIVE) {
					if (m.host()) {
						tag.append(Text.literal("  "));
					}
					tag.append(Text.literal(m.status() == LobbySnapshot.DOWNED ? "Downed" : "Eliminated").formatted(m.status() == LobbySnapshot.DOWNED ? Formatting.RED : Formatting.GRAY));
				}
				if (!tag.getSiblings().isEmpty()) {
					ctx.drawTextWithShadow(textRenderer, tag, px + 30, y + 12, 0xFFFFFF);
				}
				if (snap.canKick && !m.id().equals(self)) {
					Text kick = Icons.CROSS.label(Text.literal("Kick").formatted(Formatting.RED));
					int kw = textRenderer.getWidth(kick);
					int kx = px + pw - kw - 8;
					ctx.drawTextWithShadow(textRenderer, kick, kx, y + 7, 0xFFFFFF);
					kickRects.add(new Object[]{kx - 2, Math.max(y, top), px + pw - 4, Math.min(y + ROW_H, bottom), m.id()});
				}
			}
			y += ROW_H;
		}
		ctx.disableScissor();
	}

	private Identifier skinOf(UUID id) {
		if (client != null && client.getNetworkHandler() != null) {
			PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(id);
			if (entry != null) {
				return entry.getSkinTexture();
			}
		}
		return DefaultSkinHelper.getTexture(id);
	}

	private void clampScroll(int top, int bottom) {
		int max = Math.max(0, snap.members.size() * ROW_H - (bottom - top));
		scroll = Math.max(0, Math.min(scroll, max));
	}

	// ---- Input --------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		for (Object[] r : kickRects) {
			if (mouseX >= (int) r[0] && mouseX < (int) r[2] && mouseY >= (int) r[1] && mouseY < (int) r[3]) {
				send(LobbyAction.KICK, (UUID) r[4]);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		scroll -= (int) (amount * ROW_H);
		return true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
