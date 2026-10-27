package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Z Fighter's own packet: the training screen's buttons (stats, forms, Limit Break, techniques) and sneak+X. */
public final class ZFighterNet {
	public static final int BUY_STAT = 0;
	public static final int BUY_FORM = 1;
	public static final int BUY_LIMIT = 2;
	public static final int CYCLE_LIMIT = 3;
	public static final int BUY_TECHNIQUE = 4;
	public static final int EQUIP_TECHNIQUE = 5;
	/** (0.17.46) The aura style picked on the training screen: {@code index} is the style. */
	public static final int AURA_STYLE = 12;
	/** (0.17.46) The training screen's wheel button: puts technique {@code index} on the technique wheel or takes it off. */
	public static final int WHEEL_SLOT = 13;
	/** (0.17.61) The Story screen: start saga {@code index / 3} at difficulty {@code index % 3}; give up the saga; give up job {@code index}. */
	public static final int SAGA_START = 20;
	public static final int SAGA_ABANDON = 21;
	public static final int JOB_ABANDON = 22;
	// (6 was the lineage pick: lineages now come from the heritage)

	private ZFighterNet() {
	}

	/** Client → server: {@code action} ({@link #BUY_STAT} etc.) on {@code index} (a stat, a form). */
	public record Train(int action, int index) implements CustomPacketPayload {
		public static final Type<Train> TYPE = new Type<>(JaymosPowers.id("zfighter_train"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Train> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.action);
				buf.writeVarInt(p.index);
			},
			buf -> new Train(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Train> type() {
			return TYPE;
		}
	}

	/** Client → server: hold Z and scroll; take off into, or change to, flight pose {@code pose} (1-5), or land (0). */
	public record Fly(int pose) implements CustomPacketPayload {
		public static final Type<Fly> TYPE = new Type<>(JaymosPowers.id("zfighter_fly"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Fly> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.pose),
			buf -> new Fly(buf.readVarInt()));

		@Override
		public Type<Fly> type() {
			return TYPE;
		}
	}

	/** Client → server: left mouse held bare-handed (the hold combo) or let go. */
	public record Combo(boolean down) implements CustomPacketPayload {
		public static final Type<Combo> TYPE = new Type<>(JaymosPowers.id("zfighter_hold_combo"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Combo> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.down),
			buf -> new Combo(buf.readBoolean()));

		@Override
		public Type<Combo> type() {
			return TYPE;
		}
	}

	/** Client → server: right click held bare-handed (the guard) or let go. */
	public record Guard(boolean up) implements CustomPacketPayload {
		public static final Type<Guard> TYPE = new Type<>(JaymosPowers.id("zfighter_guard"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Guard> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.up),
			buf -> new Guard(buf.readBoolean()));

		@Override
		public Type<Guard> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: a beam to draw (a Ki Wave and its kin, the Great Ape's): from, to, how thick, its colour, its
	 * style ({@code ZFighterKi.WAVE} etc.) and how long it lasts in ticks.
	 */
	public record BeamFx(double fx, double fy, double fz, double tx, double ty, double tz, float radius, int color, int style, int ticks)
		implements CustomPacketPayload {
		public static final Type<BeamFx> TYPE = new Type<>(JaymosPowers.id("zfighter_beam"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BeamFx> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeDouble(p.fx);
				buf.writeDouble(p.fy);
				buf.writeDouble(p.fz);
				buf.writeDouble(p.tx);
				buf.writeDouble(p.ty);
				buf.writeDouble(p.tz);
				buf.writeFloat(p.radius);
				buf.writeInt(p.color);
				buf.writeVarInt(p.style);
				buf.writeVarInt(p.ticks);
			},
			buf -> new BeamFx(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
				buf.readFloat(), buf.readInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<BeamFx> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client, every tick a fired beam travels ({@code ZFighterWaveBeam}): its id (the firer's entity id), from
	 * the hands to its head, how thick, its colour and style; {@code fade} 0 while it travels, else it has stopped and
	 * fades over that many ticks.
	 */
	public record BeamHead(int id, double fx, double fy, double fz, double hx, double hy, double hz, float radius, int color, int style, int fade)
		implements CustomPacketPayload {
		public static final Type<BeamHead> TYPE = new Type<>(JaymosPowers.id("zfighter_beam_head"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BeamHead> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.id);
				buf.writeDouble(p.fx);
				buf.writeDouble(p.fy);
				buf.writeDouble(p.fz);
				buf.writeDouble(p.hx);
				buf.writeDouble(p.hy);
				buf.writeDouble(p.hz);
				buf.writeFloat(p.radius);
				buf.writeInt(p.color);
				buf.writeVarInt(p.style);
				buf.writeVarInt(p.fade);
			},
			buf -> new BeamHead(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
				buf.readDouble(), buf.readFloat(), buf.readInt(), buf.readVarInt(), Math.min(buf.readVarInt(), 100)));

		@Override
		public Type<BeamHead> type() {
			return TYPE;
		}
	}

	/** Server → client: a Spirit Bomb (by its thrower's entity id) is here and this big; drawn while it keeps coming. */
	public record SpiritFx(int id, double x, double y, double z, float size, float glow) implements CustomPacketPayload {
		public static final Type<SpiritFx> TYPE = new Type<>(JaymosPowers.id("zfighter_spirit"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SpiritFx> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.id);
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
				buf.writeFloat(p.size);
				buf.writeFloat(p.glow);
			},
			buf -> new SpiritFx(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat()));

		@Override
		public Type<SpiritFx> type() {
			return TYPE;
		}
	}

	/** Server → client: the Eternal Dragon waits; open the wish screen. */
	public record OpenWish() implements CustomPacketPayload {
		public static final Type<OpenWish> TYPE = new Type<>(JaymosPowers.id("zfighter_open_wish"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenWish> CODEC = StreamCodec.unit(new OpenWish());

		@Override
		public Type<OpenWish> type() {
			return TYPE;
		}
	}

	/** Client → server: the wish picked on the wish screen ({@code ZFighterWishes.POWER} etc.). */
	public record Wish(int wish) implements CustomPacketPayload {
		public static final Type<Wish> TYPE = new Type<>(JaymosPowers.id("zfighter_wish"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Wish> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.wish),
			buf -> new Wish(buf.readVarInt()));

		@Override
		public Type<Wish> type() {
			return TYPE;
		}
	}

	/** One ki orb this tick: who it is (to follow it between ticks), where, how big across (blocks) and its colour. */
	public record Orb(int id, double x, double y, double z, float size, int color) {
		/** Set above the RGB in {@code color}: a ball of water, not ki. */
		public static final int WATER = 0x01000000;
		/** Set above the RGB in {@code color}: a Ki Disc, drawn as a flat disc spinning level. */
		public static final int DISC = 0x02000000;
	}

	/** Server → client, every tick: the ki orbs near you (blasts in flight, ki gathering in the hands), drawn as plain orbs. */
	public record KiOrbs(java.util.List<Orb> orbs) implements CustomPacketPayload {
		public static final Type<KiOrbs> TYPE = new Type<>(JaymosPowers.id("zfighter_ki_orbs"));
		public static final StreamCodec<RegistryFriendlyByteBuf, KiOrbs> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.orbs.size());
				for (Orb orb : p.orbs) {
					buf.writeVarInt(orb.id());
					buf.writeDouble(orb.x());
					buf.writeDouble(orb.y());
					buf.writeDouble(orb.z());
					buf.writeFloat(orb.size());
					buf.writeInt(orb.color());
				}
			},
			buf -> {
				int count = Math.min(buf.readVarInt(), 512);
				java.util.List<Orb> orbs = new java.util.ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					orbs.add(new Orb(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readInt()));
				}
				return new KiOrbs(orbs);
			});

		@Override
		public Type<KiOrbs> type() {
			return TYPE;
		}
	}

	/**
	 * (0.17.54) Server → client: a Great Ape's foot came down at {@code (x, y, z)} (its {@code left} or right), its
	 * shockwave running out to {@code reach}; {@code step} is how far it walks a step. The client keeps the ape's legs in
	 * step with it and draws the wave's ring ({@code ZFighterApeSteps}).
	 */
	public record ApeStep(int entity, boolean left, double x, double y, double z, float reach, float step) implements CustomPacketPayload {
		public static final Type<ApeStep> TYPE = new Type<>(JaymosPowers.id("zfighter_ape_step"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ApeStep> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entity);
				buf.writeBoolean(p.left);
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
				buf.writeFloat(p.reach);
				buf.writeFloat(p.step);
			},
			buf -> new ApeStep(buf.readVarInt(), buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
				buf.readFloat()));

		@Override
		public Type<ApeStep> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(KiOrbs.TYPE, KiOrbs.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ApeStep.TYPE, ApeStep.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenWish.TYPE, OpenWish.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Wish.TYPE, Wish.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Wish.TYPE, (payload, ctx) -> ZFighterWishes.wish(ctx.player(), payload.wish()));
		PayloadTypeRegistry.clientboundPlay().register(BeamFx.TYPE, BeamFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(BeamHead.TYPE, BeamHead.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SpiritFx.TYPE, SpiritFx.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Guard.TYPE, Guard.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Guard.TYPE, (payload, ctx) -> {
			if (Powers.has(ctx.player(), ZFighterPower.INSTANCE)) {
				ZFighterGuard.set(ctx.player(), payload.up());
			}
		});
		PayloadTypeRegistry.serverboundPlay().register(ApeSmash.Key.TYPE, ApeSmash.Key.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ApeSmash.Key.TYPE, (payload, ctx) -> ApeSmash.key(ctx.player(), payload.down()));
		PayloadTypeRegistry.serverboundPlay().register(Combo.TYPE, Combo.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Combo.TYPE, (payload, ctx) -> {
			if (Powers.has(ctx.player(), ZFighterPower.INSTANCE) || !payload.down()) {
				ZFighterCombo.hold(ctx.player(), payload.down());
			}
		});
		PayloadTypeRegistry.serverboundPlay().register(Train.TYPE, Train.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Fly.TYPE, Fly.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Fly.TYPE, (payload, ctx) -> {
			if (Powers.has(ctx.player(), ZFighterPower.INSTANCE)) {
				ZFighterPower.fly(ctx.player(), payload.pose());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Train.TYPE, (payload, ctx) -> train(ctx.player(), payload));
	}

	private static void train(ServerPlayer player, Train train) {
		if (!Powers.has(player, ZFighterPower.INSTANCE)) {
			return;
		}
		switch (train.action()) {
			case BUY_STAT -> ZFighterTraining.buyStat(player, train.index());
			case BUY_FORM -> ZFighterTraining.buyForm(player, train.index());
			case BUY_LIMIT -> ZFighterTraining.buyLimit(player);
			case CYCLE_LIMIT -> ZFighterPower.cycleLimit(player);
			case BUY_TECHNIQUE -> ZFighterTraining.buyTechnique(player, train.index());
			case EQUIP_TECHNIQUE -> ZFighterTraining.equipTechnique(player, train.index());
			case AURA_STYLE -> ZFighterAura.pick(player, train.index());
			case WHEEL_SLOT -> ZFighterSlots.toggle(player, train.index());
			case SAGA_START -> ZFighterSagas.start(player, train.index() / 3, train.index() % 3);
			case SAGA_ABANDON -> ZFighterSagas.abandon(player);
			case JOB_ABANDON -> ZFighterSideQuests.abandon(player, train.index());
			default -> {
			}
		}
	}
}
