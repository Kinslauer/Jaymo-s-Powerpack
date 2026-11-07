package com.jaymoskingdoms;

import com.jaymoskingdoms.Patrons.Mark;
import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * (0.7.513) The cleric's Leaving Rite (docs/Patrons-B-Plan.md section 5). Andy: clerics "will aid you in turning away if you
 * choose to". On MCA's Interact page ({@link InteractActions#RITE}) a town cleric offers it to one who is turning to another
 * patron or is under the old patron's trials. It halves the trials and dulls the omens, for a price in the town's coin. It
 * never takes a scar.
 */
public final class Rites {
	static final int RITE_COST = 8, RITE_COST_PER_SCAR = 4;
	static final float RITE_FROM_TRUTH = 2.0F, RITE_TO_TRUTH = 0.5F;
	static final double RITE_REACH = 6.0;

	/** Server to player: open the rite screen. */
	public record OpenRite(int entityId, String from, String to, int daysLeft, int daysAfter, Component price, boolean canPay) implements CustomPacketPayload {
		public static final Type<OpenRite> TYPE = new Type<>(JaymosKingdoms.id("patron_rite"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenRite> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, OpenRite::entityId,
			ByteBufCodecs.STRING_UTF8, OpenRite::from,
			ByteBufCodecs.STRING_UTF8, OpenRite::to,
			ByteBufCodecs.VAR_INT, OpenRite::daysLeft,
			ByteBufCodecs.VAR_INT, OpenRite::daysAfter,
			ComponentSerialization.STREAM_CODEC, OpenRite::price,
			ByteBufCodecs.BOOL, OpenRite::canPay,
			OpenRite::new);

		@Override
		public Type<OpenRite> type() {
			return TYPE;
		}
	}

	/** Player to server: pay for the rite on offer. */
	public record TakeRite(int entityId) implements CustomPacketPayload {
		public static final Type<TakeRite> TYPE = new Type<>(JaymosKingdoms.id("patron_rite_take"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TakeRite> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, TakeRite::entityId, TakeRite::new);

		@Override
		public Type<TakeRite> type() {
			return TYPE;
		}
	}

	/** The rite each player was last shown (from, to), so a payment is for what they saw. Not saved. */
	private static final Map<UUID, String> SHOWN = new HashMap<>();

	private Rites() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(OpenRite.TYPE, OpenRite.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TakeRite.TYPE, TakeRite.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TakeRite.TYPE, (payload, context) -> take(context.player(), payload.entityId()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SHOWN.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHOWN.clear());
	}

	/** The price in milli: a base and a sum for each scar, double to leave The Truth and half to come to it. At least one unit. */
	static long price(ServerPlayer sp, Turning.Rite rite) {
		double emeralds = RITE_COST + RITE_COST_PER_SCAR * Turning.allScars(sp);
		if (rite.from() == Patron.TRUTH) {
			emeralds *= RITE_FROM_TRUTH;
		}
		if (rite.to() == Patron.TRUTH) {
			emeralds *= RITE_TO_TRUTH;
		}
		return Math.max(1L, Math.round(emeralds)) * Money.UNIT;
	}

	private static String key(Turning.Rite rite) {
		return rite.from().key + ">" + rite.to().key;
	}

	/** {@code priest} offers the rite to {@code sp}: the screen opens. */
	static void offer(ServerPlayer sp, Villager priest) {
		if (!(sp.level() instanceof ServerLevel level)) {
			return;
		}
		Turning.Rite rite = Turning.rite(sp, Patrons.today(level.getServer()));
		if (rite == null) {
			return;
		}
		Faction f = Quests.settlement(level, priest);
		long milli = price(sp, rite);
		SHOWN.put(sp.getUUID(), key(rite));
		ServerPlayNetworking.send(sp, new OpenRite(priest.getId(), rite.from().key, rite.to().key, rite.daysLeft(), rite.daysAfter(), Money.text(f, milli), Money.canPay(sp, f, milli)));
	}

	/** {@code sp} pressed Pay: everything is checked again, then paid. */
	static void take(ServerPlayer sp, int entityId) {
		if (!(sp.level() instanceof ServerLevel level)) {
			return;
		}
		Entity e = level.getEntity(entityId);
		if (!(e instanceof Villager priest) || !Accusations.priest(priest) || !priest.isAlive() || Downed.isDowned(priest) || sp.distanceTo(priest) > RITE_REACH) {
			return;
		}
		long today = Patrons.today(level.getServer());
		Turning.Rite rite = Turning.rite(sp, today);
		if (rite == null || !key(rite).equals(SHOWN.get(sp.getUUID()))) {
			return;
		}
		Faction f = Quests.settlement(level, priest);
		long milli = price(sp, rite);
		if (!Money.canPay(sp, f, milli) || !Money.charge(sp, f, milli)) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rite.poor"));
			level.playSound(null, priest.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
			return;
		}
		SHOWN.remove(sp.getUUID());
		List<Mark> marks = new ArrayList<>(Patrons.state(sp).marks());
		if (rite.streak()) {
			marks.add(new Mark(Turning.RITE, rite.to().key, today));
		} else {
			// every live full trial of this patron is eased, so a longer second one cannot stay at full strength
			for (int i = 0; i < marks.size(); i++) {
				Mark m = marks.get(i);
				if (Turning.TRIAL.equals(m.kind()) && m.patron().equals(rite.from().key) && m.day() > today) {
					marks.set(i, new Mark(Turning.TRIAL_EASED_KIND, rite.from().key, today + rite.daysAfter()));
				}
			}
		}
		Patrons.setMarks(sp, marks);
		String line = (rite.to() == Patron.TRUTH ? "speech.jaymoskingdoms.rite.home." : "speech.jaymoskingdoms.rite.done.") + sp.getRandom().nextInt(2);
		Speech.sayTo(level, priest, sp, Component.translatable(line), Speech.GOLD);
		level.playSound(null, priest.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.8F, 1.2F);
		level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.0, sp.getZ(), 16, 0.5, 0.8, 0.5, 0.05);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rite.done", Patrons.coloured(rite.from())));
		if (rite.to() == Patron.TRUTH && Patrons.of(sp) == Patron.TRUTH) {
			Patrons.deed(sp, Patron.TRUTH, Patrons.DEED_SMALL, "rite");
		}
	}
}
