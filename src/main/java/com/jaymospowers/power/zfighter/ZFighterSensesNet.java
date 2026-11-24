package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.17.45, Andy's NOEA picks) The packets of the senses patch (ki sense, scouter, instant teleport, impact frames, the
 * air burst), kept apart from {@link ZFighterNet} so the two patches' tracks don't touch the same file. The beam clash
 * timing game has its own pair in {@code core/BeamClashTiming}.
 */
public final class ZFighterSensesNet {
	/** {@link Reading#flags()}: the scouter overloaded (and broke), the target's ki is hidden, the number is past the scouter's cap, the wearer has no ki to read a power level with. */
	public static final int OVERLOADED = 1, HIDDEN = 2, PAST_CAP = 4, NO_LEVEL = 8;

	private ZFighterSensesNet() {
	}

	/** Client → server: the ki sense key; flips the sense on or off. */
	public record SenseToggle() implements CustomPacketPayload {
		public static final Type<SenseToggle> TYPE = new Type<>(JaymosPowers.id("zfighter_sense_toggle"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SenseToggle> CODEC = StreamCodec.unit(new SenseToggle());

		@Override
		public Type<SenseToggle> type() {
			return TYPE;
		}
	}

	/** One living thing sensed: its entity id, how strong it is next to you ({@code ZFighterKiSense.WEAKER} etc.), and damage taken. */
		public record Mark(int id, int tier, int damageTaken, int maxHealth) {
		}

	/** Server → the sensing player only, a few times a second: whether the sense is on and who is in range. */
		public record Sense(boolean on, List<Mark> marks) implements CustomPacketPayload {
			public static final Type<Sense> TYPE = new Type<>(JaymosPowers.id("zfighter_sense"));
			public static final StreamCodec<RegistryFriendlyByteBuf, Sense> CODEC = CustomPacketPayload.codec(
				(p, buf) -> {
					buf.writeBoolean(p.on);
					buf.writeVarInt(p.marks.size());
					for (Mark mark : p.marks) {
						buf.writeVarInt(mark.id());
						buf.writeByte(mark.tier());
						buf.writeVarInt(mark.damageTaken());
						buf.writeVarInt(mark.maxHealth());
					}
				},
				buf -> {
					boolean on = buf.readBoolean();
					int count = Math.min(buf.readVarInt(), 256);
					List<Mark> marks = new ArrayList<>(count);
					for (int i = 0; i < count; i++) {
						marks.add(new Mark(buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readVarInt()));
					}
					return new Sense(on, marks);
				});

			@Override
			public Type<Sense> type() {
				return TYPE;
			}
		}

	/** Client → server: the scouter wearer is looking at this living thing. */
	public record ScouterRead(int id) implements CustomPacketPayload {
		public static final Type<ScouterRead> TYPE = new Type<>(JaymosPowers.id("zfighter_scouter_read"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ScouterRead> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.id),
			buf -> new ScouterRead(buf.readVarInt()));

		@Override
		public Type<ScouterRead> type() {
			return TYPE;
		}
	}

	/** Server → client: the power level of that living thing (-1: hidden), and the {@link #OVERLOADED} etc. flags. */
	public record Reading(int id, int level, int flags) implements CustomPacketPayload {
		public static final Type<Reading> TYPE = new Type<>(JaymosPowers.id("zfighter_scouter_reading"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Reading> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.id);
				buf.writeInt(p.level);
				buf.writeByte(p.flags);
			},
			buf -> new Reading(buf.readVarInt(), buf.readInt(), buf.readByte()));

		@Override
		public Type<Reading> type() {
			return TYPE;
		}
	}

	/** What a hold-to-scan is aimed at: a living thing, a block, or the place (open sky or ground). */
	public static final int SCAN_ENTITY = 0, SCAN_BLOCK = 1, SCAN_PLACE = 2;

	/** Client → server, a few times a second while the scan key is held: what the lens is on ({@code id} or {@code pos}). */
	public record ScanAsk(int kind, int id, net.minecraft.core.BlockPos pos) implements CustomPacketPayload {
		public static final Type<ScanAsk> TYPE = new Type<>(JaymosPowers.id("zfighter_scan_ask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ScanAsk> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeByte(p.kind);
				buf.writeVarInt(p.id);
				buf.writeBlockPos(p.pos);
			},
			buf -> new ScanAsk(buf.readByte(), buf.readVarInt(), buf.readBlockPos()));

		@Override
		public Type<ScanAsk> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client, answering each {@link ScanAsk}: the lines of the reading that have come through so far, how many there
	 * will be, and whether this answer just finished it ({@code chime}).
	 */
	public record ScanLines(int kind, int id, List<net.minecraft.network.chat.Component> lines, int total, boolean chime) implements CustomPacketPayload {
		public static final Type<ScanLines> TYPE = new Type<>(JaymosPowers.id("zfighter_scan_lines"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ScanLines> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeByte(p.kind);
				buf.writeVarInt(p.id);
				buf.writeVarInt(p.total);
				buf.writeBoolean(p.chime);
				buf.writeVarInt(p.lines.size());
				for (net.minecraft.network.chat.Component line : p.lines) {
					net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buf, line);
				}
			},
			buf -> {
				int kind = buf.readByte();
				int id = buf.readVarInt();
				int total = buf.readVarInt();
				boolean chime = buf.readBoolean();
				int count = Math.min(buf.readVarInt(), 32);
				List<net.minecraft.network.chat.Component> lines = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					lines.add(net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buf));
				}
				return new ScanLines(kind, id, lines, total, chime);
			});

		@Override
		public Type<ScanLines> type() {
			return TYPE;
		}
	}

	/** Client → server: the scan key was tapped; mark (or let go) the last thing fully scanned. */
	public record ScanMark() implements CustomPacketPayload {
		public static final Type<ScanMark> TYPE = new Type<>(JaymosPowers.id("zfighter_scan_mark"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ScanMark> CODEC = StreamCodec.unit(new ScanMark());

		@Override
		public Type<ScanMark> type() {
			return TYPE;
		}
	}

	/** One marked living thing and where it is now. */
	public record Marked(int id, double x, double y, double z) {
	}

	/** Server → client, twice a second: where each marked thing is (empty: none left). */
	public record ScanMarks(List<Marked> marks) implements CustomPacketPayload {
		public static final Type<ScanMarks> TYPE = new Type<>(JaymosPowers.id("zfighter_scan_marks"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ScanMarks> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.marks.size());
				for (Marked mark : p.marks) {
					buf.writeVarInt(mark.id());
					buf.writeDouble(mark.x());
					buf.writeDouble(mark.y());
					buf.writeDouble(mark.z());
				}
			},
			buf -> {
				int count = Math.min(buf.readVarInt(), 16);
				List<Marked> marks = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					marks.add(new Marked(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
				}
				return new ScanMarks(marks);
			});

		@Override
		public Type<ScanMarks> type() {
			return TYPE;
		}
	}

	/** One place Instant Teleport can go: a friend or master sensed within the day, and how far off they were last seen. */
	public record Dest(UUID id, String name, boolean master, int distance) {
	}

	/** Server → client: B held for a second; the friends and masters to pick from. */
	public record OpenTeleport(List<Dest> dests) implements CustomPacketPayload {
		public static final Type<OpenTeleport> TYPE = new Type<>(JaymosPowers.id("zfighter_open_teleport"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenTeleport> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.dests.size());
				for (Dest dest : p.dests) {
					buf.writeUUID(dest.id());
					buf.writeUtf(dest.name(), 64);
					buf.writeBoolean(dest.master());
					buf.writeVarInt(dest.distance());
				}
			},
			buf -> {
				int count = Math.min(buf.readVarInt(), 64);
				List<Dest> dests = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					dests.add(new Dest(buf.readUUID(), buf.readUtf(64), buf.readBoolean(), buf.readVarInt()));
				}
				return new OpenTeleport(dests);
			});

		@Override
		public Type<OpenTeleport> type() {
			return TYPE;
		}
	}

	/** Client → server: appear beside this friend or master. */
	public record TeleportTo(UUID id) implements CustomPacketPayload {
		public static final Type<TeleportTo> TYPE = new Type<>(JaymosPowers.id("zfighter_teleport_to"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TeleportTo> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeUUID(p.id),
			buf -> new TeleportTo(buf.readUUID()));

		@Override
		public Type<TeleportTo> type() {
			return TYPE;
		}
	}

	/** Server → client: a big hit landed within 24 blocks; flash the screen for two frames (if the player allows it). */
	public record ImpactFrame() implements CustomPacketPayload {
		public static final Type<ImpactFrame> TYPE = new Type<>(JaymosPowers.id("zfighter_impact_frame"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ImpactFrame> CODEC = StreamCodec.unit(new ImpactFrame());

		@Override
		public Type<ImpactFrame> type() {
			return TYPE;
		}
	}

	/** Client → server: double-tap forward in flight; the short air burst (the client moves him, the server charges the ki). */
	public record AirBurst() implements CustomPacketPayload {
		public static final Type<AirBurst> TYPE = new Type<>(JaymosPowers.id("zfighter_air_burst"));
		public static final StreamCodec<RegistryFriendlyByteBuf, AirBurst> CODEC = StreamCodec.unit(new AirBurst());

		@Override
		public Type<AirBurst> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SenseToggle.TYPE, SenseToggle.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Sense.TYPE, Sense.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ScouterRead.TYPE, ScouterRead.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Reading.TYPE, Reading.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ScanAsk.TYPE, ScanAsk.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScanLines.TYPE, ScanLines.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ScanMark.TYPE, ScanMark.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScanMarks.TYPE, ScanMarks.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenTeleport.TYPE, OpenTeleport.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TeleportTo.TYPE, TeleportTo.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ImpactFrame.TYPE, ImpactFrame.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AirBurst.TYPE, AirBurst.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SenseToggle.TYPE, (payload, ctx) -> ZFighterKiSense.toggle(ctx.player()));
		ServerPlayNetworking.registerGlobalReceiver(ScouterRead.TYPE, (payload, ctx) -> ZFighterScouter.read(ctx.player(), payload.id()));
		ServerPlayNetworking.registerGlobalReceiver(ScanAsk.TYPE, (payload, ctx) -> ZFighterScan.ask(ctx.player(), payload.kind(), payload.id(), payload.pos()));
		ServerPlayNetworking.registerGlobalReceiver(ScanMark.TYPE, (payload, ctx) -> ZFighterScan.mark(ctx.player()));
		ServerPlayNetworking.registerGlobalReceiver(TeleportTo.TYPE, (payload, ctx) -> ZFighterTeleport.to(ctx.player(), payload.id()));
		ServerPlayNetworking.registerGlobalReceiver(AirBurst.TYPE, (payload, ctx) -> {
			if (Powers.has(ctx.player(), ZFighterPower.INSTANCE)) {
				ZFighterAirBurst.burst(ctx.player());
			}
		});
	}

	/** Sends {@code payload} to {@code player} (a short name for the senses classes). */
	static void send(ServerPlayer player, CustomPacketPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}
}
