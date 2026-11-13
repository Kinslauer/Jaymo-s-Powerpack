package com.jaymospowers.divine;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.web.WebNet;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.wind.aether.WindAether;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.stamina.Dodge;
import com.jaymospowers.stamina.Feathers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * What every Divine-born carries (Kingdoms' heritage {@code divine}, read off its tag: {@link ZFighterLineage#divine}),
 * Z Fighter or not. Andy: "like z fighters but without the raging raw ki, it is controlled and elegant, they gain a web
 * like sense that allows them to dodge incoming attacks and counter, as long as they have feathers." All of it runs on
 * {@link Feathers}:
 * <ul>
 * <li><b>The sense.</b> A blow or a shot that would land is felt first: it misses, {@link #SENSE_COST} feathers go, and
 * the body drifts a step aside. Nothing is pressed. It stops when the feathers do.</li>
 * <li><b>The answer.</b> For {@link #COUNTER_TICKS} after a blow slipped that way, the next melee hit on whoever threw it
 * lands {@link #COUNTER_BONUS} times as hard (more with level and practice: {@link #counterBonus}) and gives {@link #COUNTER_REFUND} feathers back.</li>
 * <li><b>Alight</b> (the heritage key, through Kingdoms: {@link #alight}). Lifted a little off the ground and held still,
 * a sigil turns under them, motes of light wind down round them and the row fills, one every {@link #ALIGHT_EVERY} ticks (faster under an open
 * sky or in the Aether). Being hit, sneaking or the key again ends it; the sense is shut while it lasts.</li>
 * <li><b>Hover.</b> Jump pressed again in the air and held: the fall slows to a drift, for a feather every
 * {@link #HOVER_EVERY} ticks, and no fall is counted.</li>
 * <li><b>Trained fists</b> (Andy, 2026-11-12, on their "Z Fighter scaling": "they can train their fists to deal more
 * damage"). Every bare-handed blow a Divine-born lands, on a foe or a practice dummy, is practice for the fists
 * ({@link #FIST_PRACTICE}, at most one every {@link #FIST_GAP} ticks); each rank ({@link #FIST_RANK_AT}) makes their
 * bare hands hit {@link #FIST_PER_RANK} harder ({@link #fists}).</li>
 * </ul>
 * Motion is the client's ({@code DivineSenseClient}); the server spends, times and shows.
 */
public final class DivineSense {
	// ===== the knobs ================================================================================================
	/** Feathers (half-feathers) a blow slipped by the sense costs, and the least ticks between two. */
	public static final int SENSE_COST = 4, SENSE_GAP = 10;
	/** The drift aside (blocks/tick) and its lift. */
	public static final double SENSE_GLIDE = 0.5, SENSE_LIFT = 0.12;
	/** How long the answer stays open (ticks), what it multiplies the blow by, and the feathers it gives back. */
	public static final int COUNTER_TICKS = 40;
	public static final float COUNTER_BONUS = 1.5F;
	public static final int COUNTER_REFUND = 4;
	/**
	 * The answer grows (Andy: "they deal more damage with the counters as they increase in level and they can train it for
	 * higher increases"): this much more per experience level, up to {@link #LEVEL_CAP} in all, and this much more per rank
	 * of practice. Practice is earned by using it: {@link #SLIP_PRACTICE} for a blow slipped, {@link #COUNTER_PRACTICE} for an
	 * answer landed, ranks at {@link #RANK_AT}.
	 */
	public static final float PER_LEVEL = 0.01F, LEVEL_CAP = 0.5F, PER_RANK = 0.15F;
	public static final int SLIP_PRACTICE = 1, COUNTER_PRACTICE = 3;
	public static final int[] RANK_AT = {0, 40, 120, 280, 560, 1000};
	/** Trained fists: practice per bare-handed blow landed, the least wait between two that count (ticks), the ranks, and the extra a rank adds (times the blow). */
	public static final int FIST_PRACTICE = 1, FIST_GAP = 8;
	public static final int[] FIST_RANK_AT = {0, 60, 200, 450, 850, 1400};
	public static final float FIST_PER_RANK = 0.12F;
	/** Alight: ticks per half-feather (on the ground under a roof, and under the sky or in the Aether), the longest it lasts, and the wait after. */
	public static final int ALIGHT_EVERY = 4, ALIGHT_EVERY_SKY = 2, ALIGHT_MAX = 20 * 12, ALIGHT_COOLDOWN = 20 * 20;
	/** Alight ends if they are carried this far from where it began. */
	public static final double ALIGHT_LEASH = 4.0;
	/** Hover: ticks per half-feather, and the fall it slows to (blocks/tick; read by the client). */
	public static final int HOVER_EVERY = 8;
	public static final double HOVER_FALL = -0.07;
	/** The light all of it is drawn in (MagicFx): a pale gold. */
	public static final int LIGHT = 0xFFF2C8;
	// ================================================================================================================

	/** On a Divine-born player, synced to them alone: their client needs to know (heritage tags aren't sent). */
	public static final AttachmentType<Boolean> DIVINE = AttachmentRegistry.create(JaymosPowers.id("divine_born"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));
	/** On one of the just who stayed at the Judge: they keep their own heritage and carry all of this and the ring. Saved, kept through death, synced to everyone (the ring). */
	public static final AttachmentType<Boolean> STAYED = AttachmentRegistry.create(JaymosPowers.id("divine_stayed"),
		builder -> builder.persistent(com.mojang.serialization.Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** Practice at the sense and the answer. Saved, kept through death. */
	public static final AttachmentType<Integer> PRACTICE = AttachmentRegistry.create(JaymosPowers.id("divine_practice"),
		builder -> builder.persistent(com.mojang.serialization.Codec.INT).copyOnDeath());
	/** Practice of the fists (bare-handed blows landed). Saved, kept through death. */
	public static final AttachmentType<Integer> FISTS = AttachmentRegistry.create(JaymosPowers.id("divine_fists"),
		builder -> builder.persistent(com.mojang.serialization.Codec.INT).copyOnDeath());
	/** When each Divine-born's last bare-handed blow counted as practice (game time). Not saved. */
	private static final java.util.Map<java.util.UUID, Long> LAST_FIST = new java.util.HashMap<>();

	/** While Alight lasts, synced to them alone (their client holds them in the air). */
	public static final AttachmentType<Boolean> ALIGHT = AttachmentRegistry.create(JaymosPowers.id("divine_alight"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/** Client → server: {@link #HOVER_ON} or {@link #HOVER_OFF}. */
	public record Act(byte what) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(JaymosPowers.id("divine_act"));
		public static final StreamCodec<ByteBuf, Act> CODEC = ByteBufCodecs.BYTE.map(Act::new, Act::what);

		@Override
		public Type<Act> type() {
			return TYPE;
		}
	}

	public static final byte HOVER_OFF = 0, HOVER_ON = 1;

	private record Counter(UUID foe, long until) {
	}

	private record Alighting(long start, Vec3 at) {
	}

	private static final Map<UUID, Long> SENSED = new HashMap<>();
	private static final Map<UUID, Counter> COUNTERS = new HashMap<>();
	private static final Map<UUID, Alighting> ALIGHTING = new HashMap<>();
	private static final Map<UUID, Long> ALIGHT_READY = new HashMap<>();
	private static final Set<UUID> HOVERING = new HashSet<>();

	private DivineSense() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Act.TYPE, (payload, ctx) -> act(ctx.player(), payload.what()));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(DivineSense::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0.0F && ALIGHTING.containsKey(player.getUUID())) {
				endAlight(player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(DivineSense::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SENSED.clear();
			COUNTERS.clear();
			ALIGHTING.clear();
			ALIGHT_READY.clear();
			HOVERING.clear();
		});
	}

	private static void forget(UUID id) {
		SENSED.remove(id);
		COUNTERS.remove(id);
		ALIGHTING.remove(id);
		ALIGHT_READY.remove(id);
		HOVERING.remove(id);
	}

	/** Divine-born by blood, or one of the just who stayed at the Judge. */
	public static boolean is(Entity entity) {
		return ZFighterLineage.divine(entity) || entity instanceof Player player && stayed(player);
	}

	/** Whether {@code player} stayed at the Judge (Kingdoms asks by name: its Divine-born buffs are theirs too). */
	public static boolean stayed(Player player) {
		return player.hasAttached(STAYED);
	}

	/** The Judge wrote it down: {@code player} stays. */
	public static void stay(ServerPlayer player) {
		player.setAttached(STAYED, true);
	}

	/** The rank of {@code player}'s practice, 0 to 5. */
	public static int rank(Player player) {
		int points = player.getAttachedOrElse(PRACTICE, 0), rank = 0;
		while (rank + 1 < RANK_AT.length && points >= RANK_AT[rank + 1]) {
			rank++;
		}
		return rank;
	}

	/** What the answer multiplies a blow by for {@code player}: the base, their level and their practice. */
	public static float counterBonus(Player player) {
		return COUNTER_BONUS + Math.min(LEVEL_CAP, player.experienceLevel * PER_LEVEL) + rank(player) * PER_RANK;
	}

	private static void practise(ServerPlayer player, int points) {
		int before = rank(player);
		player.setAttached(PRACTICE, Math.min(RANK_AT[RANK_AT.length - 1], player.getAttachedOrElse(PRACTICE, 0) + points));
		int after = rank(player);
		if (after > before) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.divine.rank." + after).withStyle(ChatFormatting.GOLD));
			ServerLevel level = player.level();
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.9F);
			MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), 2.5F, LIGHT);
		}
	}

	// ---- the sense ---------------------------------------------------------------------------------------------------

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || amount <= 0.0F || !is(player) || !player.isAlive() || player.isCreative() || player.isSpectator()) {
			return true;
		}
		// a web slinger's own sense goes first, a dodge already misses, and Alight leaves them open
		if (Powers.has(player, WebPower.INSTANCE) || Dodge.evading(player) || ALIGHTING.containsKey(player.getUUID()) || player.isSleeping()) {
			return true;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_EXPLOSION)) {
			return true;
		}
		Entity direct = source.getDirectEntity();
		if (!(direct instanceof Projectile) && !(direct instanceof LivingEntity) || direct == player) {
			return true;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = SENSED.get(player.getUUID());
		if (last != null && now - last < SENSE_GAP || !Feathers.spend(player, SENSE_COST)) {
			return true;
		}
		SENSED.put(player.getUUID(), now);
		practise(player, SLIP_PRACTICE);
		Entity foe = source.getEntity() != null ? source.getEntity() : direct;
		if (foe instanceof LivingEntity && foe != player) {
			COUNTERS.put(player.getUUID(), new Counter(foe.getUUID(), now + COUNTER_TICKS));
		}
		// a quiet step square to where it came from: no flip, no roll
		Vec3 away = player.position().subtract(direct.position());
		Vec3 flat = new Vec3(away.x, 0.0, away.z);
		flat = flat.lengthSqr() < 1.0E-4 ? player.getViewVector(1.0F).horizontal() : flat.normalize();
		Vec3 side = new Vec3(-flat.z, 0.0, flat.x).scale(player.getRandom().nextBoolean() ? 1.0 : -1.0);
		ServerPlayNetworking.send(player, new WebNet.Nudge(side.scale(SENSE_GLIDE).add(flat.scale(SENSE_GLIDE * 0.25)).add(0.0, SENSE_LIFT, 0.0)));
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.5F);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.5F, 1.7F);
		level.sendParticles(MagicFx.mote(LIGHT, 0.8F), player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.25, 0.5, 0.25, 0.01);
		return false;
	}

	/**
	 * The answer ({@code DivineCounterMixin}, on every blow): {@code damage}, or more of it when a Divine-born strikes,
	 * by hand, the one whose blow they have just slipped.
	 */
	public static float counter(LivingEntity target, DamageSource source, float damage) {
		if (COUNTERS.isEmpty() || !(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player) {
			return damage;
		}
		Counter counter = COUNTERS.get(player.getUUID());
		if (counter == null || !counter.foe.equals(target.getUUID())) {
			return damage;
		}
		COUNTERS.remove(player.getUUID());
		ServerLevel level = player.level();
		if (level.getGameTime() > counter.until || damage <= 0.0F) {
			return damage;
		}
		Feathers.refund(player, COUNTER_REFUND);
		level.playSound(null, target.getX(), target.getEyeY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.6F);
		MagicFx.blast(level, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 0.7F, LIGHT);
		practise(player, COUNTER_PRACTICE);
		return damage * counterBonus(player);
	}

	/** The rank of {@code player}'s trained fists, 0 to 5. */
	public static int fistRank(Player player) {
		int points = player.getAttachedOrElse(FISTS, 0), rank = 0;
		while (rank + 1 < FIST_RANK_AT.length && points >= FIST_RANK_AT[rank + 1]) {
			rank++;
		}
		return rank;
	}

	/**
	 * Trained fists ({@code DivineCounterMixin}, on every blow): a Divine-born's bare-handed blow is practice, and lands harder
	 * by their rank.
	 */
	public static float fists(LivingEntity target, DamageSource source, float damage) {
		if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player || damage <= 0.0F
			|| !player.getMainHandItem().isEmpty() || !is(player) || target == player) {
			return damage;
		}
		long now = player.level().getGameTime();
		Long last = LAST_FIST.get(player.getUUID());
		if (last == null || now - last >= FIST_GAP) {
			LAST_FIST.put(player.getUUID(), now);
			int before = fistRank(player);
			player.setAttached(FISTS, Math.min(FIST_RANK_AT[FIST_RANK_AT.length - 1], player.getAttachedOrElse(FISTS, 0) + FIST_PRACTICE));
			int after = fistRank(player);
			if (after > before) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.divine.fists." + after).withStyle(ChatFormatting.GOLD));
				player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 1.2F);
			}
		}
		return damage * (1.0F + fistRank(player) * FIST_PER_RANK);
	}

	// ---- Alight ------------------------------------------------------------------------------------------------------

	/**
	 * The heritage key (Kingdoms calls this by name): begins Alight, or ends it. False if {@code player} isn't Divine-born
	 * (Kingdoms then falls back on its own move).
	 */
	public static boolean alight(ServerPlayer player) {
		if (!is(player) || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (ALIGHTING.containsKey(player.getUUID())) {
			endAlight(player);
			return true;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long ready = ALIGHT_READY.get(player.getUUID());
		if (ready != null && now < ready && ZFighterLineage.divine(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.divine.alight_wait", (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return true;
		}
		// one who stayed keeps their own heritage's key: it is only Alight when there is something to refill
		boolean own = !ZFighterLineage.divine(player);
		if (own && (ready != null && now < ready || full(player))) {
			return false;
		}
		if (full(player) || player.isPassenger() || player.isInWater()) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4F, 0.8F);
			return true;
		}
		ALIGHTING.put(player.getUUID(), new Alighting(now, player.position()));
		HOVERING.remove(player.getUUID());
		player.setAttached(ALIGHT, true);
		MagicFx.sigil(level, player.position().add(0.0, 0.05, 0.0), 1.4F, LIGHT, 30);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.8F);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.PLAYERS, 0.6F, 1.3F);
		return true;
	}

	private static boolean full(ServerPlayer player) {
		Feathers.View view = Feathers.view(player);
		return view.feathers() >= view.cap() && view.strain() == 0 && !view.exhausted();
	}

	private static void endAlight(ServerPlayer player) {
		if (ALIGHTING.remove(player.getUUID()) == null) {
			return;
		}
		player.removeAttached(ALIGHT);
		ServerLevel level = player.level();
		ALIGHT_READY.put(player.getUUID(), level.getGameTime() + ALIGHT_COOLDOWN);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6F, 1.2F);
		MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), 2.0F, LIGHT);
	}

	private static void tickAlight(ServerPlayer player, Alighting alighting, long now) {
		ServerLevel level = player.level();
		long age = now - alighting.start;
		if (!player.isAlive() || age > ALIGHT_MAX || full(player) || player.isShiftKeyDown() && age > 5
			|| player.position().distanceToSqr(alighting.at) > ALIGHT_LEASH * ALIGHT_LEASH) {
			endAlight(player);
			return;
		}
		boolean sky = level.dimension() == WindAether.AETHER || level.canSeeSky(player.blockPosition().above());
		if (age % (sky ? ALIGHT_EVERY_SKY : ALIGHT_EVERY) == 0) {
			Feathers.refund(player, 1);
		}
		// two feathers of light winding down round them, a turn every second
		double turn = age * (Math.PI / 10.0), drop = 2.4 - age % 20 / 20.0 * 2.2;
		for (int arm = 0; arm < 2; arm++) {
			double angle = turn + arm * Math.PI;
			level.sendParticles(MagicFx.mote(LIGHT, 1.0F), player.getX() + Math.cos(angle) * 0.8, player.getY() + drop, player.getZ() + Math.sin(angle) * 0.8, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (age > 0 && age % 20 == 0) {
			// the sigil is sent a second at a time, so it goes when they come down
			MagicFx.sigil(level, alighting.at.add(0.0, 0.05, 0.0), 1.4F, LIGHT, 30);
		}
		if (age % 10 == 0) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F,
				Math.min(2.0F, 1.0F + age / 10 * 0.08F));
		}
	}

	// ---- hover -------------------------------------------------------------------------------------------------------

	private static void act(ServerPlayer player, byte what) {
		switch (what) {
			case HOVER_ON -> {
				if (is(player) && !player.onGround() && !ALIGHTING.containsKey(player.getUUID()) && Feathers.canSpend(player, 1)) {
					HOVERING.add(player.getUUID());
				}
			}
			case HOVER_OFF -> HOVERING.remove(player.getUUID());
			default -> {
			}
		}
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % 20 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				boolean divine = is(player);
				if (divine != player.hasAttached(DIVINE)) {
					if (divine) {
						player.setAttached(DIVINE, true);
					} else {
						player.removeAttached(DIVINE);
						endAlight(player);
						HOVERING.remove(player.getUUID());
					}
				}
			}
		}
		if (!ALIGHTING.isEmpty()) {
			for (Map.Entry<UUID, Alighting> entry : Map.copyOf(ALIGHTING).entrySet()) {
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player == null) {
					ALIGHTING.remove(entry.getKey());
				} else {
					tickAlight(player, entry.getValue(), player.level().getGameTime());
				}
			}
		}
		for (Iterator<UUID> it = HOVERING.iterator(); it.hasNext();) {
			ServerPlayer player = server.getPlayerList().getPlayer(it.next());
			if (player == null || !player.isAlive() || player.onGround() || player.isInWater() || player.getAbilities().flying
				|| player.tickCount % HOVER_EVERY == 0 && !Feathers.spend(player, 1)) {
				it.remove();
				continue;
			}
			player.resetFallDistance();
			if (player.tickCount % 4 == 0) {
				player.level().sendParticles(MagicFx.mote(LIGHT, 0.6F), player.getX(), player.getY() - 0.1, player.getZ(), 1, 0.2, 0.0, 0.2, 0.0);
			}
		}
	}
}
