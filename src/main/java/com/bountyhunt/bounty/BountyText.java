package com.bountyhunt.bounty;

import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/** Shared display names so every announcement and tooltip says e.g. "Normal Zombie", never just "Normal". */
public final class BountyText {
	private BountyText() {
	}

	public static Text entityName(Bounty b) {
		return Registries.ENTITY_TYPE.get(b.monsterType).getName();
	}

	/** Coloured difficulty followed by the monster name, e.g. "Hard Zombie". */
	public static MutableText display(Bounty b) {
		return Text.empty().append(b.difficulty.displayName()).append(Text.literal(" ")).append(entityName(b));
	}

	public static String direction(double dx, double dz) {
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? "East" : "West";
		}
		return dz >= 0 ? "South" : "North";
	}
}
