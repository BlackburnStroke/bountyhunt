package com.bountyhunt.gui;

import com.bountyhunt.bounty.Bounty;
import com.bountyhunt.bounty.BountyDifficulty;
import com.bountyhunt.bounty.BountyText;
import com.bountyhunt.config.BountyConfig;
import com.bountyhunt.data.BountyWorldData;
import com.bountyhunt.hunt.HuntInstance;
import com.bountyhunt.hunt.HuntState;
import com.bountyhunt.item.ModItems;
import com.bountyhunt.util.PlayerNames;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Item builders for the board menu. All names/lore are non-italic. */
public final class GuiItems {
	private GuiItems() {
	}

	public static MutableText plain(String text, Formatting color) {
		return Text.literal(text).formatted(color);
	}

	public static Text kv(String label, Text value) {
		return Text.literal(label + ": ").formatted(Formatting.GRAY).append(value);
	}

	public static ItemStack named(Item item, Text name, List<Text> lore) {
		ItemStack stack = new ItemStack(item);
		stack.setCustomName(name.copy().styled(s -> s.withItalic(false)));
		if (lore != null && !lore.isEmpty()) {
			NbtList list = new NbtList();
			for (Text line : lore) {
				list.add(NbtString.of(Text.Serializer.toJson(line.copy().styled(s -> s.withItalic(false)))));
			}
			stack.getOrCreateSubNbt("display").put("Lore", list);
		}
		return stack;
	}

	private static String num(double d) {
		return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
	}

	public static String stateLabel(HuntState s) {
		return switch (s) {
			case AVAILABLE -> "Available";
			case PARTY_FORMING -> "Party forming";
			case WAITING_FOR_ENCOUNTER -> "Commenced - travelling to the target";
			case ACTIVE -> "The monster is out there";
			case COMPLETED -> "Completed";
			case FAILED -> "Failed";
			case ABANDONED -> "Abandoned";
		};
	}

	/** Distance text relative to a reference position, e.g. "~1560 blocks South". */
	public static String distanceText(Bounty b, BlockPos ref) {
		double dx = b.target.getX() - ref.getX();
		double dz = b.target.getZ() - ref.getZ();
		int r = Math.max(1, BountyConfig.get().distanceRounding);
		long dist = Math.round(Math.sqrt(dx * dx + dz * dz) / r) * r;
		return "~" + dist + " blocks " + BountyText.direction(dx, dz);
	}

	/** A bounty as a difficulty-textured scroll; monster details are in the tooltip shown on hover. */
	public static ItemStack scroll(MinecraftServer server, BountyWorldData data, Bounty b, BlockPos ref, ServerPlayerEntity viewer) {
		BountyDifficulty d = b.difficulty;
		List<Text> lore = new ArrayList<>();
		lore.add(kv("Difficulty", d.displayName()));
		lore.add(kv("Monster", BountyText.entityName(b)));
		lore.add(kv("Reward", plain(Math.round(d.statMultiplier() * 100) + "%", Formatting.YELLOW)));
		lore.add(kv("Health / Armor / Damage", plain("x" + num(d.statMultiplier()), Formatting.WHITE)));
		lore.add(kv("Size", plain("x" + num(d.sizeMultiplier()), Formatting.WHITE)));

		HuntInstance hunt = data.hunts().get(b.id);
		boolean member = hunt != null && hunt.party().contains(viewer.getUuid());
		if (member && b.state.isLocked()) {
			lore.add(kv("Target", plain(b.target.getX() + ", " + b.target.getY() + ", " + b.target.getZ(), Formatting.AQUA)));
		} else {
			lore.add(kv("Distance", plain(distanceText(b, ref), Formatting.WHITE)));
			lore.add(plain("Exact location hidden until commenced", Formatting.DARK_GRAY));
		}
		if (b.owner != null) {
			lore.add(kv("Personal bounty of", plain(PlayerNames.of(server, b.owner), Formatting.WHITE)));
		}
		lore.add(kv("Status", plain(stateLabel(b.state), Formatting.WHITE)));
		if (hunt != null) {
			lore.add(kv("Host", plain(PlayerNames.of(server, hunt.party().currentHost()), Formatting.WHITE)));
			lore.add(kv("Party", plain(hunt.party().size() + " / " + BountyConfig.get().maxPartySize, Formatting.WHITE)));
		}

		lore.add(Text.empty());
		HuntInstance mine = data.findHuntOf(viewer.getUuid());
		if (member) {
			lore.add(plain("Click to open your party lobby", Formatting.GREEN));
		} else if (mine != null) {
			lore.add(plain("You are already in another party", Formatting.GRAY));
		} else if (b.state == HuntState.AVAILABLE) {
			lore.add(plain("Click to create a party", Formatting.YELLOW));
		} else if (b.state == HuntState.PARTY_FORMING) {
			lore.add(plain("Click to join this party", Formatting.YELLOW));
		} else {
			lore.add(plain("Hunt in progress", Formatting.RED));
		}
		return named(ModItems.scroll(d), BountyText.display(b), lore);
	}
}
