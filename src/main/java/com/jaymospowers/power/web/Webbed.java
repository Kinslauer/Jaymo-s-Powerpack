package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.power.ember.EmberKindle;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

/**
 * Webbed up (the medieval picks: de-wrap, de-layers, de-struggle). The layered hold after Derelict's Cobwebbed (mim1q,
 * MIT: a strength that steps down a level at a time, and no walking while it holds), in our own code. Every web hold of
 * ours wraps a layer on (Web's shots, the web bomb, webbed fists' third hit, cocoons and hanging targets, through
 * {@link WebTraps#ensnare}), so do the Aether's webs and Jaymo's Kingdoms' spiders (by this effect's id), up to
 * {@link #MOST}: thin, thick, dense.
 * <ul>
 * <li><b>Layers:</b> the top one holds as long as the web that put it on; once it tears loose, each under it holds
 * {@link #UNDER_TICKS} more of its own. Kept as the effect's own stack (the game's hidden effects): the amplifier is
 * the layers less one.</li>
 * <li><b>Held:</b> no walking at all, only a hop ({@link #HOP} of a jump, no sprint), and milk doesn't take it off
 * ({@code WebbedMilkMixin}). An {@code addEffect} of it from anywhere (a command, the Aether, Kingdoms) is a
 * {@link #wrap} of that many layers ({@code WebbedLivingEntityMixin}).</li>
 * <li><b>Free:</b> struggling ({@link #STRUGGLE_PRESSES} fresh presses of the walking keys, the client's
 * {@code WebbedView}) tears a layer off; a blade's hit cuts one, and so does a friend's use with a blade
 * ({@link #CUT_COOLDOWN}); fire burns the lot. (Andy) An Emberborn burns any web that touches them: they can't be
 * webbed, and a cobweb they walk into burns away ({@code WebbedWebBlockMixin}).</li>
 * </ul>
 * {@link #LAYERS} tells every client how many layers someone wears, for the look ({@code WebbedLook}) and the
 * strands across your own view ({@code WebbedView}).
 */
public final class Webbed {
	/** At most this many layers: thin, thick, dense. */
	public static final int MOST = 3;
	/** Once the layer over it tears loose, each layer under holds this long (ticks): a player's, anything else's. */
	public static final int UNDER_TICKS = 60, UNDER_TICKS_MOB = 40;
	/** A hop is this much less than a jump (0.5 = half as high). */
	public static final double HOP = 0.5;
	/** Struggling: this many fresh presses tear a layer off; a press this long (ticks) after the last starts the count again. */
	public static final int STRUGGLE_PRESSES = 6;
	public static final int STRUGGLE_FORGET = 40;
	/** A friend cutting someone out with a blade takes off one layer at most every this many ticks. */
	public static final int CUT_COOLDOWN = 10;
	/** The cobweb grey of its icon and its name. */
	public static final int COLOR = 0xdcdce4;

	/** The effect ({@code jaymospowers:webbed}); null before {@link #register}. */
	public static @Nullable Holder<MobEffect> EFFECT;

	/** How many layers someone wears (1 to {@link #MOST}), for everyone's client; absent when free. */
	public static final AttachmentType<Integer> LAYERS = AttachmentRegistry.create(JaymosPowers.id("webbed_layers"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Client → server: a webbed player tore at the web (a fresh press of a walking key). */
	public record Struggle() implements CustomPacketPayload {
		public static final Type<Struggle> TYPE = new Type<>(JaymosPowers.id("webbed_struggle"));
		public static final StreamCodec<ByteBuf, Struggle> CODEC = StreamCodec.unit(new Struggle());

		@Override
		public Type<Struggle> type() {
			return TYPE;
		}
	}

	/** Everyone webbed this server knows of, and how many layers they wore last tick. */
	private static final Map<LivingEntity, Integer> TRACKED = new HashMap<>();
	/** Struggles so far: presses, and the tick of the last. */
	private static final Map<UUID, long[]> STRUGGLES = new HashMap<>();
	/** When each cutter may cut again. */
	private static final Map<UUID, Long> CUT_READY = new HashMap<>();
	private static long clock;
	private static boolean registered;

	private Webbed() {
	}

	/** The effect, its packet and its events (once, from whichever of the web power and the Aether comes first). */
	public static synchronized Holder<MobEffect> register() {
		if (registered) {
			return EFFECT;
		}
		registered = true;
		EFFECT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id("webbed")), new Effect());
		PayloadTypeRegistry.serverboundPlay().register(Struggle.TYPE, Struggle.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Struggle.TYPE, (payload, ctx) -> struggle(ctx.player()));
		ServerTickEvents.END_SERVER_TICK.register(Webbed::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TRACKED.clear();
			STRUGGLES.clear();
			CUT_READY.clear();
		});
		// a blade's hit cuts a layer
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!blocked && layers(entity) > 0 && source.getDirectEntity() instanceof LivingEntity by && by == source.getEntity()
				&& Dismember.blade(by.getMainHandItem()) && entity.level() instanceof ServerLevel level) {
				peel(level, entity);
			}
		});
		// a friend's use with a blade cuts one too
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !(entity instanceof LivingEntity held) || held == player
				|| shown(held) <= 0 || !Dismember.blade(player.getMainHandItem())) {
				return InteractionResult.PASS;
			}
			if (world instanceof ServerLevel level) {
				Long ready = CUT_READY.get(player.getUUID());
				if (ready == null || clock >= ready) {
					CUT_READY.put(player.getUUID(), clock + CUT_COOLDOWN);
					player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.SHEARS_SNIP, SoundSource.PLAYERS, 0.9F, 1.1F);
					peel(level, held);
				}
			}
			return InteractionResult.SUCCESS;
		});
		return EFFECT;
	}

	// ---- reading ----------------------------------------------------------------------------------------------------

	/** How many layers {@code body} wears now (server side: its effect). */
	public static int layers(LivingEntity body) {
		if (EFFECT == null) {
			return 0;
		}
		MobEffectInstance effect = body.getEffect(EFFECT);
		return effect == null ? 0 : Math.min(MOST, effect.getAmplifier() + 1);
	}

	/** How many layers {@code body} is seen to wear (either side: {@link #LAYERS}). */
	public static int shown(LivingEntity body) {
		Integer n = body.getAttached(LAYERS);
		return n == null ? 0 : n;
	}

	/** Whether {@code body} is held by web now (either side: the effect itself for one's own player and the server). */
	public static boolean held(LivingEntity body) {
		return EFFECT != null && body.hasEffect(EFFECT);
	}

	/** (Andy) Of Emberborn blood: a player or villager Kingdoms tags so, or a player Powers knows is. */
	public static boolean emberborn(LivingEntity body) {
		return body.entityTags().contains(EmberKindle.TAG) || body instanceof Player player && EmberKindle.ember(player);
	}

	// ---- wrapping ---------------------------------------------------------------------------------------------------

	/** One more layer on {@code target}, the top one holding {@code ticks}; false if it couldn't be webbed. */
	public static boolean wrap(LivingEntity target, int ticks) {
		return wrap(target, ticks, 1);
	}

	/**
	 * {@code add} more layers on {@code target} (up to {@link #MOST}), the top one holding at least {@code ticks}. An
	 * Emberborn, or anything alight, burns it off at once. False if nothing was wrapped.
	 */
	public static boolean wrap(LivingEntity target, int ticks, int add) {
		if (EFFECT == null || !(target.level() instanceof ServerLevel level) || !target.isAlive() || target.isSpectator() || ticks <= 0) {
			return false;
		}
		if (target.isOnFire() || emberborn(target)) {
			burnFx(level, target);
			return false;
		}
		int have = layers(target);
		int n = Math.min(MOST, have + Math.max(1, add));
		MobEffectInstance now = target.getEffect(EFFECT);
		int top = n == have && now != null ? Math.max(ticks, now.getDuration()) : ticks;
		set(target, n, top);
		return true;
	}

	/** {@code body} wears {@code n} layers, the top one for {@code top} ticks and each under it {@link #UNDER_TICKS} more. */
	private static void set(LivingEntity body, int n, int top) {
		if (EFFECT == null) {
			return;
		}
		if (n <= 0) {
			body.removeEffect(EFFECT);
			return;
		}
		int under = body instanceof Player ? UNDER_TICKS : UNDER_TICKS_MOB;
		MobEffectInstance stack = null;
		for (int layer = 1; layer <= n; layer++) {
			// (the game's hidden effects: each runs down with the one over it, so the ones under last longer)
			stack = new MobEffectInstance(EFFECT, top + (n - layer) * under, layer - 1, false, false, true, stack);
		}
		body.forceAddEffect(stack, null);
		body.setAttached(LAYERS, n);
		TRACKED.put(body, n);
	}

	/** One layer torn or cut off {@code body} (the next holds a fresh while), with the tearing look; false if none was on. */
	public static boolean peel(ServerLevel level, LivingEntity body) {
		int n = layers(body);
		if (n <= 0) {
			return false;
		}
		set(body, n - 1, body instanceof Player ? UNDER_TICKS : UNDER_TICKS_MOB);
		if (n - 1 <= 0) {
			body.removeAttached(LAYERS);
			TRACKED.remove(body);
		}
		peelFx(level, body, n - 1 <= 0);
		return true;
	}

	/** Fire (or an Emberborn's heat) takes every layer off at once. */
	public static void burn(ServerLevel level, LivingEntity body) {
		if (EFFECT != null && body.removeEffect(EFFECT)) {
			body.removeAttached(LAYERS);
			TRACKED.remove(body);
		}
		burnFx(level, body);
	}

	/** (Andy) A web an Emberborn touched burns away: the cobweb or web block at {@code pos}. */
	public static void burnBlock(ServerLevel level, BlockPos pos) {
		level.destroyBlock(pos, false);
		level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.01);
		level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.01);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.6F);
	}

	/** Milk was drunk: everything else went, the web stays on ({@code WebbedMilkMixin}). */
	public static void keep(LivingEntity body, @Nullable MobEffectInstance web) {
		if (web != null && body.isAlive()) {
			body.forceAddEffect(web, null);
		}
	}

	private static void struggle(ServerPlayer player) {
		if (layers(player) <= 0 || !player.isAlive()) {
			STRUGGLES.remove(player.getUUID());
			return;
		}
		long[] s = STRUGGLES.computeIfAbsent(player.getUUID(), k -> new long[2]);
		if (clock - s[1] > STRUGGLE_FORGET) {
			s[0] = 0;
		}
		if (s[1] == clock) {
			// (one a tick)
			return;
		}
		s[1] = clock;
		s[0]++;
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.5F, 0.7F + 0.1F * s[0]);
		if (s[0] >= STRUGGLE_PRESSES) {
			s[0] = 0;
			peel(level, player);
		}
	}

	private static void tick(MinecraftServer server) {
		clock++;
		if (TRACKED.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<LivingEntity, Integer>> it = TRACKED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<LivingEntity, Integer> entry = it.next();
			LivingEntity body = entry.getKey();
			boolean here = !body.isRemoved() && body.isAlive();
			int n = here ? layers(body) : 0;
			int was = entry.getValue();
			if (n != was) {
				if (n <= 0) {
					body.removeAttached(LAYERS);
				} else {
					body.setAttached(LAYERS, n);
				}
				if (n < was && here && body.level() instanceof ServerLevel level) {
					// a layer wore through on its own
					peelFx(level, body, n <= 0);
				}
				entry.setValue(n);
			}
			if (n <= 0) {
				it.remove();
				STRUGGLES.remove(body.getUUID());
			}
		}
		if (!CUT_READY.isEmpty()) {
			CUT_READY.values().removeIf(until -> until <= clock);
		}
	}

	// ---- looks ------------------------------------------------------------------------------------------------------

	/** A layer tears loose: tufts fly, and a soft rip (the last one, louder). */
	private static void peelFx(ServerLevel level, LivingEntity body, boolean free) {
		level.sendParticles(ParticleTypes.ITEM_COBWEB, body.getX(), body.getY() + body.getBbHeight() * 0.5, body.getZ(), free ? 24 : 14,
			body.getBbWidth() * 0.4, body.getBbHeight() * 0.35, body.getBbWidth() * 0.4, 0.02);
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, free ? 1.0F : 0.7F, free ? 1.0F : 1.3F);
	}

	private static void burnFx(ServerLevel level, LivingEntity body) {
		level.sendParticles(ParticleTypes.FLAME, body.getX(), body.getY() + body.getBbHeight() * 0.5, body.getZ(), 12,
			body.getBbWidth() * 0.4, body.getBbHeight() * 0.35, body.getBbWidth() * 0.4, 0.01);
		level.sendParticles(ParticleTypes.SMOKE, body.getX(), body.getY() + body.getBbHeight() * 0.6, body.getZ(), 8,
			body.getBbWidth() * 0.35, body.getBbHeight() * 0.3, body.getBbWidth() * 0.35, 0.01);
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.5F);
	}

	/** The effect: a hop for a jump; fire or an Emberborn's heat burns it off; and it's counted for {@link #LAYERS}. */
	private static final class Effect extends MobEffect {
		Effect() {
			super(MobEffectCategory.HARMFUL, COLOR, ParticleTypes.ITEM_COBWEB);
			this.addAttributeModifier(Attributes.JUMP_STRENGTH, JaymosPowers.id("effect.webbed.jump"), -HOP, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
			return true;
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity body, int amplifier) {
			if (body.isOnFire() || emberborn(body)) {
				// (false takes the whole stack off)
				burnFx(level, body);
				body.removeAttached(LAYERS);
				TRACKED.remove(body);
				return false;
			}
			// (one loaded from a save, or webbed before the server knew it)
			TRACKED.putIfAbsent(body, 0);
			return true;
		}
	}
}
