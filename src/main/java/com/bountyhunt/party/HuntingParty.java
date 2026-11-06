package com.bountyhunt.party;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Party roster plus the two host identities:
 * original host (who commenced) and current host (who holds leadership right now).
 */
public class HuntingParty {
	private UUID originalHost;
	private UUID currentHost;
	/** True when the current host took leadership by killing the previous host. They cannot kick. */
	private boolean hostByBetrayal;
	/** True when the original host lost leadership only because they disconnected. */
	private boolean originalHostAway;
	private final Set<UUID> members = new LinkedHashSet<>();

	public HuntingParty(UUID host) {
		this.originalHost = host;
		this.currentHost = host;
		this.members.add(host);
	}

	public UUID originalHost() {
		return originalHost;
	}

	public UUID currentHost() {
		return currentHost;
	}

	public boolean isHost(UUID id) {
		return currentHost.equals(id);
	}

	public boolean hostByBetrayal() {
		return hostByBetrayal;
	}

	public boolean originalHostAway() {
		return originalHostAway;
	}

	public Set<UUID> members() {
		return Collections.unmodifiableSet(members);
	}

	public boolean contains(UUID id) {
		return members.contains(id);
	}

	public int size() {
		return members.size();
	}

	public boolean add(UUID id) {
		return members.add(id);
	}

	public boolean remove(UUID id) {
		return members.remove(id);
	}

	/** Called when Commence is pressed: whoever holds leadership then is the "original host". */
	public void markCommenced() {
		this.originalHost = currentHost;
		this.originalHostAway = false;
	}

	public void setCurrentHost(UUID id, boolean byBetrayal) {
		this.currentHost = id;
		this.hostByBetrayal = byBetrayal;
	}

	public void transferBecauseDisconnected(UUID newHost) {
		boolean wasOriginal = currentHost.equals(originalHost);
		this.currentHost = newHost;
		this.hostByBetrayal = false;
		this.originalHostAway = wasOriginal;
	}

	public void restoreOriginalHost() {
		this.currentHost = originalHost;
		this.hostByBetrayal = false;
		this.originalHostAway = false;
	}

	public NbtCompound toNbt() {
		NbtCompound n = new NbtCompound();
		n.putUuid("Original", originalHost);
		n.putUuid("Current", currentHost);
		n.putBoolean("Betrayal", hostByBetrayal);
		n.putBoolean("OriginalAway", originalHostAway);
		NbtList list = new NbtList();
		for (UUID m : members) {
			NbtCompound c = new NbtCompound();
			c.putUuid("Id", m);
			list.add(c);
		}
		n.put("Members", list);
		return n;
	}

	public static HuntingParty fromNbt(NbtCompound n) {
		HuntingParty p = new HuntingParty(n.getUuid("Original"));
		p.currentHost = n.getUuid("Current");
		p.hostByBetrayal = n.getBoolean("Betrayal");
		p.originalHostAway = n.getBoolean("OriginalAway");
		p.members.clear();
		for (NbtElement e : n.getList("Members", NbtElement.COMPOUND_TYPE)) {
			p.members.add(((NbtCompound) e).getUuid("Id"));
		}
		return p;
	}
}
