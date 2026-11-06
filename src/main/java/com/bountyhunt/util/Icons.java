package com.bountyhunt.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Colour icons drawn from the mod's own bitmap font (assets/bountyhunt/font/icons.json), because the default
 * Minecraft font cannot draw colour emoji. Add an icon: put a PNG in textures/font/icon, add a provider to
 * icons.json and a constant here.
 */
public enum Icons {
	CHECK('\uE000'),
	CROSS('\uE001'),
	SWORDS('\uE002'),
	DOOR('\uE003'),
	SKULL('\uE004'),
	BACK('\uE005'),
	REROLL('\uE006'),
	FLAG('\uE007'),
	CROWN('\uE008');

	public static final Identifier FONT = new Identifier("bountyhunt", "icons");

	private final char glyph;

	Icons(char glyph) {
		this.glyph = glyph;
	}

	/** Just the icon, drawn in its own colours (white tint = no tint). */
	public MutableText text() {
		return Text.literal(String.valueOf(glyph)).setStyle(Style.EMPTY.withFont(FONT).withColor(0xFFFFFF));
	}

	/** Icon, a space, then the label. The root is empty so the label keeps the normal font. */
	public MutableText label(Text label) {
		return Text.empty().append(text()).append(Text.literal(" ")).append(label);
	}
}
