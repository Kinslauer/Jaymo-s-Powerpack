package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.Soundscape;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.94, Andy's NOEA pick "Technique creator") A fighter makes techniques of their own on the training screen's
 * Create page: a name, a shape, a colour and a strength (1-5), paid for in TP. Three quick ones can be made for G
 * ({@link #QUICK_FIRST}..) and three charged ones for sneak+V ({@link #CHARGED_FIRST}..). They sit beside the learned
 * techniques under indexes of their own (100 up), so they go on their key, on the wheel and on the HUD like any other.
 * A made technique can be forgotten to free its place (its TP is not given back).
 */
public final class ZFighterCreator {
	/** Indexes of made techniques: three quick (G), then three charged (V). */
	public static final int QUICK_FIRST = 100, CHARGED_FIRST = 103, EACH = 3;
	/** The longest name. */
	public static final int NAME_MAX = 20;
	/** TP per point of strength, and the strongest it can be made. */
	public static final int TP_PER_POWER = 1000, POWER_MAX = 5;
	/** The quick shapes: one bolt, a fan of bolts, seeking bolts, a burst all round. */
	public static final int BOLT = 0, SPREAD = 1, HOMING = 2, BURST = 3;
	/** The charged shapes: a wave, a boring lance, a needle, a great ball thrown. */
	public static final int BEAM = 0, LANCE = 1, NEEDLE = 2, BALL = 3;
	public static final int SHAPES = 4;
	public static final String[] QUICK_SHAPE_KEYS = {"bolt", "spread", "homing", "burst"};
	public static final String[] CHARGED_SHAPE_KEYS = {"beam", "lance", "needle", "ball"};
	/** The colours to pick from. */
	public static final int[] COLORS = {0xFFD84A, 0x5AC8FF, 0xFF4A4A, 0x7CFF6A, 0xC070FF, 0xFFFFFF, 0xFF8A2A, 0xFF70C8};
	/** Ticks of gathering to a full charge, for every made charged technique. */
	public static final int FULL_TICKS = 50;

	// ---- quick (G): ki, damage, cooldown, by strength ----
	static final float QUICK_KI = 6.0F, QUICK_KI_PER = 4.0F;
	static final float BOLT_DAMAGE = 4.0F, BOLT_DAMAGE_PER = 2.5F;
	static final float SPREAD_DAMAGE = 3.0F, SPREAD_DAMAGE_PER = 1.2F;
	static final float HOMING_DAMAGE = 2.5F, HOMING_DAMAGE_PER = 1.2F;
	static final float BURST_DAMAGE = 4.0F, BURST_DAMAGE_PER = 2.0F;
	static final double BURST_RADIUS = 3.0, BURST_RADIUS_PER = 0.6;
	static final int QUICK_COOLDOWN = 20, QUICK_COOLDOWN_PER = 10;
	// ---- charged (V) ----
	static final float CHARGED_MIN_KI = 15.0F, CHARGED_MIN_KI_PER = 10.0F;
	static final float CHARGED_MAX_KI = 40.0F, CHARGED_MAX_KI_PER = 25.0F;
	static final float CHARGED_DAMAGE_MIN = 6.0F, CHARGED_DAMAGE_MIN_PER = 4.0F;
	static final float CHARGED_DAMAGE_MAX = 14.0F, CHARGED_DAMAGE_MAX_PER = 10.0F;
	static final int CHARGED_COOLDOWN = 80, CHARGED_COOLDOWN_PER = 30;

	/** One made technique. */
	public record Made(int index, String name, int shape, int color, int power) {
		public static final Codec<Made> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("index").forGetter(Made::index),
				Codec.STRING.fieldOf("name").forGetter(Made::name),
				Codec.INT.fieldOf("shape").forGetter(Made::shape),
				Codec.INT.fieldOf("color").forGetter(Made::color),
				Codec.INT.fieldOf("power").forGetter(Made::power))
			.apply(i, Made::new));
		public static final StreamCodec<ByteBuf, Made> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Made::index,
			ByteBufCodecs.STRING_UTF8, Made::name,
			ByteBufCodecs.VAR_INT, Made::shape,
			ByteBufCodecs.INT, Made::color,
			ByteBufCodecs.VAR_INT, Made::power,
			Made::new);

		public boolean charged() {
			return index >= CHARGED_FIRST;
		}

		public String shapeKey() {
			return "screen.jaymospowers.zfighter_creator.shape." + (charged() ? CHARGED_SHAPE_KEYS : QUICK_SHAPE_KEYS)[Mth.clamp(shape, 0, SHAPES - 1)];
		}
	}

	/** Their made techniques. Saved, kept through death, synced to them. */
	public static final AttachmentType<List<Made>> MADE = AttachmentRegistry.create(JaymosPowers.id("zfighter_made_techniques"),
		builder -> builder.persistent(Made.CODEC.listOf()).copyOnDeath()
			.syncWith(Made.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	/** Client → server: make one. */
	public record Make(boolean charged, String name, int shape, int color, int power) implements CustomPacketPayload {
		public static final Type<Make> TYPE = new Type<>(JaymosPowers.id("zfighter_make_technique"));
		public static final StreamCodec<ByteBuf, Make> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Make::charged,
			ByteBufCodecs.stringUtf8(NAME_MAX * 4), Make::name,
			ByteBufCodecs.VAR_INT, Make::shape,
			ByteBufCodecs.INT, Make::color,
			ByteBufCodecs.VAR_INT, Make::power,
			Make::new);

		@Override
		public Type<Make> type() {
			return TYPE;
		}
	}

	/** Client → server: forget one. */
	public record Forget(int index) implements CustomPacketPayload {
		public static final Type<Forget> TYPE = new Type<>(JaymosPowers.id("zfighter_forget_technique"));
		public static final StreamCodec<ByteBuf, Forget> CODEC = ByteBufCodecs.VAR_INT.map(Forget::new, Forget::index);

		@Override
		public Type<Forget> type() {
			return TYPE;
		}
	}

	private ZFighterCreator() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Make.TYPE, Make.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Forget.TYPE, Forget.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Make.TYPE, (payload, ctx) -> make(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(Forget.TYPE, (payload, ctx) -> forget(ctx.player(), payload.index()));
	}

	// ---------------------------------------------------------------- who has what

	/** Whether {@code index} is a made technique's place. */
	public static boolean custom(int index) {
		return index >= QUICK_FIRST && index < CHARGED_FIRST + EACH;
	}

	/** Whether {@code index} is a made technique's place on V. */
	public static boolean chargedPlace(int index) {
		return index >= CHARGED_FIRST && index < CHARGED_FIRST + EACH;
	}

	public static List<Made> of(Player player) {
		return player.getAttachedOrElse(MADE, List.of());
	}

	/** The technique they made at {@code index}, or null. */
	public static @Nullable Made made(Player player, int index) {
		if (!custom(index)) {
			return null;
		}
		for (Made made : of(player)) {
			if (made.index() == index) {
				return made;
			}
		}
		return null;
	}

	/** Its name, in its colour. */
	public static Component name(Made made) {
		return Component.literal(made.name()).withColor(0xFF000000 | made.color());
	}

	/** What making one at strength {@code power} costs. */
	public static int cost(int power) {
		return Mth.clamp(power, 1, POWER_MAX) * TP_PER_POWER;
	}

	/** The first free place for a quick or charged one, or -1 if all three are taken. */
	public static int free(Player player, boolean charged) {
		int first = charged ? CHARGED_FIRST : QUICK_FIRST;
		for (int index = first; index < first + EACH; index++) {
			if (made(player, index) == null) {
				return index;
			}
		}
		return -1;
	}

	/** A cleaned-up name: printable, trimmed, at most {@link #NAME_MAX}. */
	public static String clean(String name) {
		StringBuilder out = new StringBuilder();
		name.codePoints().filter(c -> c >= 0x20 && c != 0x7F && c != 0xA7).forEach(out::appendCodePoint);
		String trimmed = out.toString().trim();
		return trimmed.length() > NAME_MAX ? trimmed.substring(0, NAME_MAX).trim() : trimmed;
	}

	/** Why it can't be made now (a lang key), or null. Both sides. */
	public static @Nullable String blocker(Player player, boolean charged, String name, int power) {
		if (clean(name).isEmpty()) {
			return "screen.jaymospowers.zfighter_creator.need_name";
		}
		if (free(player, charged) < 0) {
			return "screen.jaymospowers.zfighter_creator.full";
		}
		if (!player.isCreative() && ZFighterTraining.of(player).tp() < cost(power)) {
			return "screen.jaymospowers.zfighter.need_tp";
		}
		return null;
	}

	private static void make(ServerPlayer player, Make make) {
		if (!com.jaymospowers.core.Powers.has(player, ZFighterPower.INSTANCE)) {
			return;
		}
		int power = Mth.clamp(make.power(), 1, POWER_MAX);
		String blocker = blocker(player, make.charged(), make.name(), power);
		if (blocker != null) {
			player.sendSystemMessage(Component.translatable(blocker).withStyle(ChatFormatting.GRAY));
			return;
		}
		int index = free(player, make.charged());
		Made made = new Made(index, clean(make.name()), Mth.clamp(make.shape(), 0, SHAPES - 1), make.color() & 0xFFFFFF, power);
		List<Made> next = new ArrayList<>(of(player));
		next.add(made);
		player.setAttached(MADE, List.copyOf(next));
		ZFighterTraining.Data data = ZFighterTraining.of(player);
		if (!player.isCreative()) {
			data = data.withTp(data.tp() - cost(power));
		}
		player.setAttached(ZFighterTraining.DATA, made.charged() ? data.withCharged(index) : data.withQuick(index));
		ZFighterSlots.add(player, index);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_creator.made", name(made),
			Component.keybind(made.charged() ? "key.jaymospowers.ability_3" : "key.jaymospowers.ability_2")).withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.4F);
	}

	private static void forget(ServerPlayer player, int index) {
		Made made = made(player, index);
		if (made == null) {
			return;
		}
		List<Made> next = new ArrayList<>(of(player));
		next.remove(made);
		if (next.isEmpty()) {
			player.removeAttached(MADE);
		} else {
			player.setAttached(MADE, List.copyOf(next));
		}
		ZFighterTraining.Data data = ZFighterTraining.of(player);
		if (data.quick() == index) {
			player.setAttached(ZFighterTraining.DATA, data.withQuick(ZFighterTraining.KI_BLAST));
		} else if (data.charged() == index) {
			player.setAttached(ZFighterTraining.DATA, data.withCharged(ZFighterTraining.KI_WAVE));
		}
		// off the wheel too (the slots drop what isn't owned when read; save it so)
		player.setAttached(ZFighterSlots.DATA, List.copyOf(ZFighterSlots.of(player)));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_creator.forgot", name(made)).withStyle(ChatFormatting.GRAY));
	}

	// ---------------------------------------------------------------- G: the quick ones

	/** G with a made quick technique on it: does it. Its cooldown, or 0 if it didn't go (no ki). */
	static int quick(ServerPlayer player, ZFighterPower.Live live, int index) {
		Made made = made(player, index);
		if (made == null || made.charged()) {
			return 0;
		}
		int p = made.power();
		if (!ZFighterPower.spend(player, live, QUICK_KI + QUICK_KI_PER * p)) {
			return 0;
		}
		ServerLevel level = player.level();
		Vec3 look = LockOn.aim(player);
		float kiPower = ZFighterPower.kiPower(player, live);
		int color = made.color();
		Vec3 palm = ZFighterKi.palm(player, look);
		switch (made.shape()) {
			case SPREAD -> {
				int bolts = 2 + p;
				for (int i = 0; i < bolts; i++) {
					double turn = (i - (bolts - 1) * 0.5) * 0.14;
					Vec3 dir = look.yRot((float)turn).normalize();
					ZFighterKi.shoot(player, palm, dir.scale(ZFighterKi.BLAST_SPEED), (SPREAD_DAMAGE + SPREAD_DAMAGE_PER * p) * kiPower, color, false,
						ZFighterKi.BLAST_LIFE);
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F, 1.5F);
			}
			case HOMING -> {
				var random = level.getRandom();
				for (int i = 0; i < 2 + p; i++) {
					Vec3 dir = look.add((random.nextDouble() - 0.5) * 0.7, random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.7).normalize();
					ZFighterKi.shoot(player, palm, dir.scale(ZFighterKi.VOLLEY_SPEED), (HOMING_DAMAGE + HOMING_DAMAGE_PER * p) * kiPower, color, true,
						ZFighterKi.VOLLEY_LIFE);
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F, 1.3F);
			}
			case BURST -> burst(player, made, kiPower);
			default -> {
				ZFighterKi.shoot(player, palm, look.scale(ZFighterKi.BLAST_SPEED * 1.2), (BOLT_DAMAGE + BOLT_DAMAGE_PER * p) * kiPower, color, false,
					ZFighterKi.BLAST_LIFE);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.8F, 1.4F);
			}
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(0.1F * p);
		}
		return QUICK_COOLDOWN + QUICK_COOLDOWN_PER * p;
	}

	/** A burst of ki all round them: everyone close is hurt and thrown back. */
	private static void burst(ServerPlayer player, Made made, float kiPower) {
		ServerLevel level = player.level();
		int p = made.power();
		double radius = BURST_RADIUS + BURST_RADIUS_PER * p;
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius), e -> ZFighterArts.valid(player, e))) {
			Vec3 to = near.getBoundingBox().getCenter().subtract(at);
			if (to.length() > radius) {
				continue;
			}
			near.hurtServer(level, ZFighterKi.source(player), (BURST_DAMAGE + BURST_DAMAGE_PER * p) * kiPower);
			ZFighterKi.push(near, to, 0.8 + 0.2 * p, 0.35);
		}
		level.sendParticles(new DustParticleOptions(made.color(), 2.0F), true, false, at.x, at.y, at.z, 40 + 10 * p, radius * 0.4, 0.4, radius * 0.4, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6F, 1.5F);
		Shakes.send(player, 0.3F + 0.1F * p, at);
	}

	// ---------------------------------------------------------------- V: the charged ones

	static float min(Player player, int index) {
		Made made = made(player, index);
		return CHARGED_MIN_KI + CHARGED_MIN_KI_PER * (made == null ? 1 : made.power());
	}

	static float max(Player player, int index) {
		Made made = made(player, index);
		return CHARGED_MAX_KI + CHARGED_MAX_KI_PER * (made == null ? 1 : made.power());
	}

	static int cooldown(Player player, int index) {
		Made made = made(player, index);
		return CHARGED_COOLDOWN + CHARGED_COOLDOWN_PER * (made == null ? 1 : made.power());
	}

	/** Why it can't be used (it was forgotten), or null. */
	static @Nullable String blocker(Player player, int index) {
		return made(player, index) == null ? "screen.jaymospowers.zfighter_creator.gone" : null;
	}

	/** While V is held: the ki gathers at the hands in its colour. */
	static void gather(ServerPlayer player, ZFighterPower.Live live, int index, float charge) {
		Made made = made(player, index);
		if (made == null) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1.0F);
		boolean ball = made.shape() == BALL;
		Vec3 hands = ball ? player.getEyePosition().add(0.0, 1.0 + charge * made.power() * 0.3, 0.0)
			: player.getEyePosition().add(look.scale(0.7)).add(0.0, -0.45, 0.0);
		double size = ball ? 0.4 + charge * (0.5 + 0.35 * made.power()) : made.shape() == NEEDLE ? 0.1 + 0.15 * charge : 0.25 + 0.6 * charge;
		ZFighterKi.orb(level, -(player.getId() * 4 + 3), hands, size, made.color());
		var random = level.getRandom();
		if (random.nextInt(2) == 0) {
			double ox = (random.nextDouble() - 0.5) * 1.6, oy = (random.nextDouble() - 0.5) * 1.6, oz = (random.nextDouble() - 0.5) * 1.6;
			level.sendParticles(new DustParticleOptions(made.color(), 0.8F + charge), true, false, hands.x + ox, hands.y + oy, hands.z + oz, 0, -ox, -oy, -oz, 0.12);
		}
		if (charge >= 1.0F && level.getGameTime() % 3 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, hands.x, hands.y, hands.z, 2, 0.2, 0.2, 0.2, 0.2);
		}
		if (Soundscape.due(player, "ki.wave", 8, 13)) {
			Soundscape.play(level, player.position(), SoundSource.PLAYERS, ZFighterKi.KI_HUM.pitched(0.8F + charge), ZFighterKi.KI_SURGE);
		}
	}

	/** V let go with the ki paid: it goes. */
	static void fire(ServerPlayer player, ZFighterPower.Live live, int index, float charge) {
		Made made = made(player, index);
		if (made == null) {
			return;
		}
		ServerLevel level = player.level();
		int p = made.power();
		Vec3 look = LockOn.aim(player);
		float kiPower = ZFighterPower.kiPower(player, live);
		float damage = Mth.lerp(charge, CHARGED_DAMAGE_MIN + CHARGED_DAMAGE_MIN_PER * p, CHARGED_DAMAGE_MAX + CHARGED_DAMAGE_MAX_PER * p) * kiPower;
		int color = made.color();
		if (made.shape() == BALL) {
			double size = 0.4 + charge * (0.5 + 0.35 * p);
			Vec3 from = player.getEyePosition().add(0.0, 1.0 + charge * p * 0.3, 0.0);
			Vec3 aim = player.getEyePosition().add(look.scale(24.0)).subtract(from).normalize();
			ZFighterKi.throwBall(player, from, aim.scale(1.1), damage, color, size, 2.0 + size * 2.0, 1.0F + charge * p * 0.5F,
				ZFighterArts.griefing(level) && p >= 3, 120);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
		} else {
			int style = switch (made.shape()) {
				case LANCE -> ZFighterKi.SPIRAL;
				case NEEDLE -> ZFighterKi.NEEDLE;
				default -> ZFighterKi.WAVE;
			};
			double radius = (made.shape() == NEEDLE ? 0.25 : made.shape() == LANCE ? 0.6 : 0.8) + charge * 0.15 * p;
			double speed = made.shape() == NEEDLE ? ZFighterKi.NEEDLE_SPEED : made.shape() == LANCE ? ZFighterKi.LANCE_SPEED : ZFighterKi.WAVE_SPEED;
			float blast = made.shape() == NEEDLE ? 0.0F : charge * (0.6F + 0.3F * p);
			Level.ExplosionInteraction interaction = p >= 4 && charge >= 1.0F ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
			ZFighterWaveBeam.fire(player, ZFighterKi.waveHands(player, look), look, new ZFighterWaveBeam.Spec(ZFighterKi.WAVE_RANGE, speed, radius, damage,
				(0.6 + charge) * (made.shape() == NEEDLE ? 0.3 : 1.0), 0, made.shape() == LANCE, blast, interaction, false, color, style,
				ZFighterWaveBeam.strength(charge), false, made.shape() == NEEDLE ? 1.9F : made.shape() == LANCE ? 1.4F : 1.0F));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F, 1.2F + 0.1F * p);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
		}
		Shakes.send(player, 0.3F + 0.15F * p * charge, player.position());
		if (!player.isCreative()) {
			player.causeFoodExhaustion(0.2F * p * (1.0F + charge));
		}
	}
}
