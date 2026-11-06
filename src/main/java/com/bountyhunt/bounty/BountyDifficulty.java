package com.bountyhunt.bounty;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

public enum BountyDifficulty {
	//          label        colour                   stat/reward  size  personal roll weight (%)
	NORMAL("Normal", Formatting.GREEN, 1.0, 1.0f, 50),
	MEDIUM("Medium", Formatting.DARK_PURPLE, 1.5, 1.5f, 30),
	HARD("Hard", Formatting.RED, 2.5, 2.0f, 15),
	LEGENDARY("Legendary", Formatting.GOLD, 5.0, 2.5f, 5);

	private final String label;
	private final Formatting color;
	private final double multiplier;
	private final float sizeMultiplier;
	private final int personalWeight;

	BountyDifficulty(String label, Formatting color, double multiplier, float sizeMultiplier, int personalWeight) {
		this.label = label;
		this.color = color;
		this.multiplier = multiplier;
		this.sizeMultiplier = sizeMultiplier;
		this.personalWeight = personalWeight;
	}

	/** Health, armor, damage and reward multiplier. */
	public double statMultiplier() {
		return multiplier;
	}

	public float sizeMultiplier() {
		return sizeMultiplier;
	}

	public String label() {
		return label;
	}

	public MutableText displayName() {
		return Text.literal(label).formatted(color);
	}

	public static BountyDifficulty rollPersonal(Random random) {
		int roll = random.nextInt(100);
		int acc = 0;
		for (BountyDifficulty d : values()) {
			acc += d.personalWeight;
			if (roll < acc) {
				return d;
			}
		}
		return NORMAL;
	}

	public static BountyDifficulty byName(String name) {
		try {
			return valueOf(name);
		} catch (IllegalArgumentException e) {
			return NORMAL;
		}
	}
}
