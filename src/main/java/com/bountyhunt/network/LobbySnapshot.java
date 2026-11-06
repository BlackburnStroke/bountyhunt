package com.bountyhunt.network;

import com.bountyhunt.bounty.BountyDifficulty;
import com.bountyhunt.hunt.HuntState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Everything the lobby screen needs, sent by the server. */
public class LobbySnapshot {
	public static final int ALIVE = 0;
	public static final int DOWNED = 1;
	public static final int ELIMINATED = 2;

	public record Member(UUID id, String name, boolean host, int status) {
	}

	/** True when this packet should open the screen (otherwise it only updates an open one). */
	public boolean open;
	/** False when the viewer is no longer in a hunt: the screen closes. */
	public boolean inHunt;
	public Identifier monster = new Identifier("zombie");
	public BountyDifficulty difficulty = BountyDifficulty.NORMAL;
	public HuntState state = HuntState.AVAILABLE;
	public Text title = Text.empty();
	public int reward;
	public float size = 1.0f;
	public boolean hasCoords;
	public BlockPos coords = BlockPos.ORIGIN;
	public boolean viewerIsHost;
	public boolean canKick;
	public boolean hasBoard;
	public int maxMembers = 16;
	public final List<Member> members = new ArrayList<>();

	public void write(PacketByteBuf buf) {
		buf.writeBoolean(open);
		buf.writeBoolean(inHunt);
		if (!inHunt) {
			return;
		}
		buf.writeIdentifier(monster);
		buf.writeEnumConstant(difficulty);
		buf.writeEnumConstant(state);
		buf.writeText(title);
		buf.writeVarInt(reward);
		buf.writeFloat(size);
		buf.writeBoolean(hasCoords);
		buf.writeBlockPos(coords);
		buf.writeBoolean(viewerIsHost);
		buf.writeBoolean(canKick);
		buf.writeBoolean(hasBoard);
		buf.writeVarInt(maxMembers);
		buf.writeVarInt(members.size());
		for (Member m : members) {
			buf.writeUuid(m.id());
			buf.writeString(m.name());
			buf.writeBoolean(m.host());
			buf.writeVarInt(m.status());
		}
	}

	public static LobbySnapshot read(PacketByteBuf buf) {
		LobbySnapshot s = new LobbySnapshot();
		s.open = buf.readBoolean();
		s.inHunt = buf.readBoolean();
		if (!s.inHunt) {
			return s;
		}
		s.monster = buf.readIdentifier();
		s.difficulty = buf.readEnumConstant(BountyDifficulty.class);
		s.state = buf.readEnumConstant(HuntState.class);
		s.title = buf.readText();
		s.reward = buf.readVarInt();
		s.size = buf.readFloat();
		s.hasCoords = buf.readBoolean();
		s.coords = buf.readBlockPos();
		s.viewerIsHost = buf.readBoolean();
		s.canKick = buf.readBoolean();
		s.hasBoard = buf.readBoolean();
		s.maxMembers = buf.readVarInt();
		int n = buf.readVarInt();
		for (int i = 0; i < n; i++) {
			s.members.add(new Member(buf.readUuid(), buf.readString(), buf.readBoolean(), buf.readVarInt()));
		}
		return s;
	}
}
