package com.bountyhunt.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** Big centre-screen text on a translucent black bar that fades in and out. */
public final class AnnouncementOverlay {
	private static final long DURATION_MS = 4500;
	private static final long FADE_MS = 500;

	private static Text title;
	private static long startMs;

	private AnnouncementOverlay() {
	}

	public static void show(Text newTitle) {
		title = newTitle;
		startMs = Util.getMeasuringTimeMs();
	}

	public static void clear() {
		title = null;
	}

	public static void render(DrawContext ctx, float tickDelta) {
		if (title == null) {
			return;
		}
		long elapsed = Util.getMeasuringTimeMs() - startMs;
		if (elapsed >= DURATION_MS) {
			title = null;
			return;
		}
		float alpha = 1.0f;
		if (elapsed < FADE_MS) {
			alpha = elapsed / (float) FADE_MS;
		} else if (elapsed > DURATION_MS - FADE_MS) {
			alpha = (DURATION_MS - elapsed) / (float) FADE_MS;
		}
		int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
		if (a < 8) {
			return;
		}

		MinecraftClient mc = MinecraftClient.getInstance();
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		int barH = 44;
		int top = (int) (h * 0.28f) - barH / 2;

		ctx.fill(0, top, w, top + barH, ((int) (a * 0.78f) << 24));
		MatrixStack m = ctx.getMatrices();
		m.push();
		m.translate(w / 2.0f, top + 8, 0);
		m.scale(3.0f, 3.0f, 1.0f);
		ctx.drawCenteredTextWithShadow(mc.textRenderer, title, 0, 0, (a << 24) | 0xFFFFFF);
		m.pop();
	}
}
