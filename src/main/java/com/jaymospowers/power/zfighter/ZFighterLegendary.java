package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.48, Andy's NOEA picks) The legendary line. One Warrior-born in {@link #ODDS} is born with the mutation (rolled
 * once, the first time a Z Fighter of that blood is seen, then saved for good). For him the three bought forms are not
 * Super, Super 2 and Super 3 but {@link ZFighterForms#LEGEND_1}-{@link ZFighterForms#LEGEND_3}: the same road (the
 * breakthrough, TP, hardship and mastery open them in the same order, {@link #chain} maps them back) but a different
 * creature at the end of it. Huge ({@link #SCALE}), green, rage-driven and poor at control: every so often the rage
 * surges and lashes out at everything near him, friend or not ({@link #surge}); the more he masters a form the longer
 * between surges. Heavier on ki and food, and stronger than the Super it replaces. Primal Super (the tamed ape) is not
 * for him: his line ends at Legend 3, and the god form can be lifted from it. The Great Ape ends the form as ever.
 */
public final class ZFighterLegendary {
	/** One Warrior-born in this many is born with it. */
	static final int ODDS = 40;
	/** Scale added to his size by line step 1-3 (a share of normal height): about 1.35, 1.6 and 1.9 times. */
	static final double[] SCALE = {0.0, 0.35, 0.6, 0.9};
	/** The rage surges every {@link #SURGE_MIN} to {@link #SURGE_MIN} + {@link #SURGE_SPREAD} ticks, up to this much longer with full mastery. */
	static final int SURGE_MIN = 240, SURGE_SPREAD = 200;
	static final float CONTROL_GAIN = 1.5F;
	/** A surge reaches this far (plus this per step) and hurts this much (plus this per step); it throws everything back. */
	static final double SURGE_RADIUS = 3.5, SURGE_RADIUS_STEP = 1.5;
	static final float SURGE_DAMAGE = 2.0F, SURGE_DAMAGE_STEP = 2.0F;
	static final double SURGE_PUSH = 1.1;
	static final float SURGE_SHAKE = 1.2F;
	/** The ki a surge feeds back into him. */
	static final float SURGE_KI = 6.0F;

	private static final Identifier SCALE_ID = JaymosPowers.id("zfighter_legend_scale");

	/** 1: no mutation, 2: born with it. Absent until the first roll. Saved, kept through death, synced to him (his screens' names). */
	public static final AttachmentType<Integer> MUTATION = AttachmentRegistry.create(JaymosPowers.id("zfighter_legendary"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/** When each legendary-born's rage next surges. */
	private static final Map<UUID, Long> SURGES = new HashMap<>();

	private ZFighterLegendary() {
	}

	static void init() {
	}

	static void clear() {
		SURGES.clear();
	}

	static void forget(ServerPlayer player) {
		SURGES.remove(player.getUUID());
	}

	/** Whether {@code form} is one of the legendary line. */
	static boolean legend(int form) {
		return form >= ZFighterForms.LEGEND_1 && form <= ZFighterForms.LEGEND_3;
	}

	/** Born with the mutation, and of the Warrior-born blood it lives in. */
	public static boolean mutant(Player player) {
		Integer mutation = player.getAttached(MUTATION);
		return mutation != null && mutation == 2 && ZFighterLineage.of(player) == ZFighterLineage.WARRIOR;
	}

	/** The legendary line's form for step 1-3 of the road ({@code step} itself for anyone else, and for anything else). */
	static int line(Player player, int step) {
		return step >= 1 && step <= ZFighterTraining.FORMS && mutant(player) ? ZFighterForms.LEGEND_1 + step - 1 : step;
	}

	/** The step 1-3 a legendary form stands for (the form itself for any other): where its mastery and unlocking are kept. */
	static int chain(int form) {
		return legend(form) ? form - ZFighterForms.LEGEND_1 + 1 : form;
	}

	/** Decides, once, whether this Warrior-born has the mutation (debug: {@link #set}). */
	private static void roll(ServerPlayer player) {
		if (player.getAttached(MUTATION) != null || ZFighterLineage.of(player) != ZFighterLineage.WARRIOR) {
			return;
		}
		boolean born = player.getRandom().nextInt(ODDS) == 0;
		player.setAttached(MUTATION, born ? 2 : 1);
		if (born) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_legendary_born").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
		}
	}

	/** Debug: gives or takes the mutation. */
	static void set(ServerPlayer player, boolean born) {
		player.setAttached(MUTATION, born ? 2 : 1);
		ZFighterPower.refresh(player, ZFighterPower.live(player));
	}

	/** Part of {@link ZFighterPower#refresh}: his bulk in a legendary form. */
	static void attributes(ServerPlayer player, ZFighterPower.Live live) {
		double scale = legend(live.form) ? SCALE[chain(live.form)] : 0.0;
		ZFighterPower.set(player, Attributes.SCALE, SCALE_ID, scale, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	/** C: up the legendary line (from base, or the step he is on), at the price of ki unless the step is half mastered. */
	static void stepUp(ServerPlayer player, ZFighterPower.Live live) {
		int step = chain(live.form);
		int next = step + 1;
		if (live.form == 0 && !ZFighterPower.ready(player, ZFighterState.SUPER_SLOT)) {
			return;
		}
		if (next > ZFighterTraining.formsOpen(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_higher").withStyle(ChatFormatting.GRAY));
			return;
		}
		int form = line(player, next);
		boolean free = player.isCreative() || ZFighterTraining.of(player).mastery(next) >= ZFighterTraining.FREE_ENTRY_MASTERY;
		int cost = ZFighterPower.FORM_ENTER_KI[form];
		if (!free && live.ki < cost) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_ki").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!free) {
			live.ki -= cost;
		}
		SURGES.remove(player.getUUID());
		ZFighterPower.transform(player, live, form);
	}

	/** Every tick, per Z Fighter: the mutation's roll, and the rage surging in a legendary form. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		if (player.tickCount % 40 == 0) {
			roll(player);
		}
		if (!legend(live.form)) {
			SURGES.remove(player.getUUID());
			return;
		}
		long now = player.level().getGameTime();
		Long next = SURGES.get(player.getUUID());
		if (next == null) {
			SURGES.put(player.getUUID(), now + interval(player, live));
			return;
		}
		if (now >= next) {
			SURGES.put(player.getUUID(), now + interval(player, live));
			surge(player, live);
		}
	}

	/** Ticks to the next surge: longer the more of this step he has mastered. */
	private static long interval(ServerPlayer player, ZFighterPower.Live live) {
		float control = ZFighterTraining.mastery(player, live.form);
		return Math.round((SURGE_MIN + player.getRandom().nextInt(SURGE_SPREAD)) * (1.0F + CONTROL_GAIN * control));
	}

	/** The rage gets away from him: a shockwave that hurts and throws back everything alive round him, friend or foe. */
	private static void surge(ServerPlayer player, ZFighterPower.Live live) {
		ServerLevel level = player.level();
		int step = chain(live.form);
		double radius = SURGE_RADIUS + SURGE_RADIUS_STEP * step;
		float damage = SURGE_DAMAGE + SURGE_DAMAGE_STEP * step;
		Vec3 c = player.position();
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
			e -> e != player && e.isAlive() && !(e instanceof ArmorStand) && !(e instanceof Player other && (other.isCreative() || other.isSpectator())))) {
			if (near.distanceToSqr(player) > radius * radius) {
				continue;
			}
			near.hurtServer(level, ZFighterKi.source(player), damage);
			ZFighterKi.push(near, near.position().subtract(c), SURGE_PUSH, 0.4);
		}
		live.ki = Math.min(ZFighterPower.maxKi(player), live.ki + SURGE_KI);
		int color = ZFighterForms.color(live.form);
		level.sendParticles(new DustParticleOptions(color, 2.2F), true, false, c.x, c.y + player.getBbHeight() * 0.5, c.z, 50, radius * 0.3, player.getBbHeight() * 0.4,
			radius * 0.3, 0.3);
		level.sendParticles(ParticleTypes.EXPLOSION, true, false, c.x, c.y + 0.5, c.z, 3, radius * 0.25, 0.3, radius * 0.25, 0.0);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, false, c.x, c.y + 0.2, c.z, 8, radius * 0.3, 0.1, radius * 0.3, 0.04);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.6F, 0.6F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 0.7F);
		com.jaymospowers.core.Shakes.heavy(player, c, 20.0, SURGE_SHAKE);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_legendary_surge").withStyle(ChatFormatting.GREEN));
	}
}
