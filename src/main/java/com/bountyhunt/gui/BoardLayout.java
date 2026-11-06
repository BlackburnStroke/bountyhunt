package com.bountyhunt.gui;

/** Shared slot layout so server handler and client screen agree. Only these slots exist; everything else is truly blank. */
public final class BoardLayout {
	public static final int WIDTH = 204;
	public static final int HEIGHT = 126;
	public static final int PUBLIC_COUNT = 27; // 3 rows x 9
	public static final int IDX_PERSONAL = 27; // 27, 28, 29
	public static final int TOTAL = 30;

	public static final int GRID_X = 21; // centres the 9-column grid in the panel
	private static final int GRID_Y = 20;
	private static final int STEP = 18;
	public static final int LABEL_Y = 82;
	private static final int PERSONAL_Y = 98;

	// Real buttons on the personal row (not item slots)
	public static final int BTN_Y = 97;
	public static final int BTN_H = 18;
	public static final int REROLL_X = GRID_X + 4 * STEP; // after the 3 personal slots and one blank column
	public static final int REROLL_W = 44;
	public static final int LOBBY_X = REROLL_X + REROLL_W + 4;
	public static final int LOBBY_W = 42;

	// Button ids sent through the vanilla screen-handler button channel
	public static final int BUTTON_REROLL = 0;
	public static final int BUTTON_LOBBY = 1;

	private BoardLayout() {
	}

	public static int x(int idx) {
		if (idx < PUBLIC_COUNT) {
			return GRID_X + (idx % 9) * STEP;
		}
		return GRID_X + (idx - IDX_PERSONAL) * STEP; // columns 0-2; column 3 stays blank
	}

	public static int y(int idx) {
		return idx < PUBLIC_COUNT ? GRID_Y + (idx / 9) * STEP : PERSONAL_Y;
	}
}
