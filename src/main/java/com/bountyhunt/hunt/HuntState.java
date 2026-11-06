package com.bountyhunt.hunt;

/** Lifecycle of a bounty contract. */
public enum HuntState {
	AVAILABLE,
	PARTY_FORMING,
	WAITING_FOR_ENCOUNTER,
	ACTIVE,
	COMPLETED,
	FAILED,
	ABANDONED;

	/** True once the host pressed Commence: roster locked, coordinates revealed. */
	public boolean isLocked() {
		return this == WAITING_FOR_ENCOUNTER || this == ACTIVE;
	}

	public boolean isTerminal() {
		return this == COMPLETED || this == FAILED || this == ABANDONED;
	}

	public static HuntState byName(String name) {
		try {
			return valueOf(name);
		} catch (IllegalArgumentException e) {
			return AVAILABLE;
		}
	}
}
