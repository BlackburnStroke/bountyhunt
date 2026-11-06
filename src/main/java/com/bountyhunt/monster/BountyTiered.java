package com.bountyhunt.monster;

/**
 * Custom bounty monsters that carry a difficulty tier in their data tracker
 * (0 Normal, 1 Medium, 2 Hard, 3 Legendary). The tier picks the model, texture and loot table.
 */
public interface BountyTiered {
	int getTier();

	void setTier(int tier);
}
