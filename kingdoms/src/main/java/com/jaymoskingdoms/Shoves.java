package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shove and trip (fifth edition's shove, Andy's pick C7). Instead of hitting, press the shove key (default Insert, in the
 * Controls screen under Jaymo's Kingdoms; it clashes with nothing the packs use):
 * <ul>
 * <li>with an <b>empty hand</b> or a <b>shield</b> in either hand, you shove the foe in front of you back a block: off a
 * ledge, into a fire;</li>
 * <li>with a <b>hooked weapon</b> in hand ({@link #HOOKED}: axes, halberds, glaives, scythes, hooks), you trip them: they go
 * down prone, and prone foes give the upper hand ({@link UpperHand}).</li>
 * </ul>
 * It is a contest: the bigger the foe and the better their Might save ({@link Saves.Kind#GRAB}) the likelier they stand
 * firm; your Might helps. Bosses can't be shoved or tripped. A foe that's shoved or tripped turns on you if it's a mob. One
 * shove every {@link #COOLDOWN} ticks; a held or downed player can't.
 */
public final class Shoves {
	/** Weapons with a hook, a beard or a blade that catches a leg: tripping weapons. */
	public static final TagKey<Item> HOOKED = TagKey.create(Registries.ITEM, JaymosKingdoms.id("hooked"));

	/** How far a shove reaches (blocks), and the wait between shoves (ticks). */
	static final double REACH = 2.8;
	static final int COOLDOWN = 24;
	/** The push (blocks per tick, flat; the hop up): about a block of sliding on the ground. */
	static final double PUSH = 0.62, HOP = 0.1;
	/** How long a tripped foe lies (ticks). */
	static final int TRIP_TICKS = 50;
	/** The base chance, what the foe's Might save takes off (share of its chance), what your Might adds, and what size takes off per unit of foe area above a person's. */
	static final float BASE = 0.8F, SAVE_TAKES = 0.6F, MIGHT_ADDS = 0.2F, SIZE_TAKES = 0.12F;
	static final float FLOOR = 0.15F, CEILING = 0.95F;
	/** A person's body area (width times height), for the size part. */
	static final float PERSON = 1.1F;
	/** A shove costs this much food. */
	static final float TIRING = 0.35F;

	/** Client to server: the shove key was pressed. */
	public record Press() implements CustomPacketPayload {
		public static final Type<Press> TYPE = new Type<>(JaymosKingdoms.id("shove"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Press> CODEC = StreamCodec.unit(new Press());

		@Override
		public Type<Press> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Long> LAST = new HashMap<>();

	private Shoves() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Press.TYPE, Press.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Press.TYPE, (payload, context) -> context.server().execute(() -> press(context.player())));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST.clear());
	}

	private static boolean shield(ItemStack stack) {
		return stack.has(DataComponents.BLOCKS_ATTACKS) || stack.getItem() instanceof net.minecraft.world.item.ShieldItem;
	}

	private static void press(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator() || HeldFast.locked(player) || Nets.netted(player)
			|| PowersCompat.helpless(player) || player.isPassenger()) {
			return;
		}
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < COOLDOWN) {
			return;
		}
		ItemStack main = player.getMainHandItem();
		boolean trip = main.is(HOOKED);
		boolean shove = !trip && (main.isEmpty() || shield(main) || shield(player.getOffhandItem()));
		if (!trip && !shove) {
			return;
		}
		LAST.put(player.getUUID(), now);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		player.causeFoodExhaustion(TIRING);
		LivingEntity foe = target(player);
		if (foe == null) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.6F, 1.2F);
			return;
		}
		if (!wins(player, foe, level)) {
			level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.8F, 1.3F);
			return;
		}
		if (trip) {
			if (!PowersCompat.trip(foe, player, TRIP_TICKS)) {
				// something that can't be put on its back is staggered instead
				foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2), player);
			}
			level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 0.8F);
		} else {
			Vec3 away = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() < 1.0E-4) {
				away = player.getLookAngle().multiply(1.0, 0.0, 1.0);
			}
			away = away.normalize();
			Vec3 v = foe.getDeltaMovement();
			foe.setDeltaMovement(away.x * PUSH, Math.max(v.y, HOP), away.z * PUSH);
			foe.syncVelocity = true;
			level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 1.2F);
		}
		level.sendParticles(ParticleTypes.POOF, foe.getX(), foe.getY(0.5), foe.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
		if (foe instanceof Mob mob && mob.isAlive()) {
			mob.setTarget(player);
		}
	}

	/** The one in front of {@code player} within reach. */
	private static @Nullable LivingEntity target(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(REACH));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, new AABB(eye, end).inflate(1.0),
			e -> e instanceof LivingEntity l && l.isAlive() && !l.isSpectator() && l != player && !l.isPassengerOfSameVehicle(player), 0.3F);
		return hit != null && hit.getEntity() instanceof LivingEntity l && !(l instanceof Mob m && Senses.boss(m)) ? l : null;
	}

	/** The contest: {@code foe}'s size and Might save against {@code player}'s Might. */
	private static boolean wins(ServerPlayer player, LivingEntity foe, ServerLevel level) {
		float size = Math.max(0.0F, foe.getBbWidth() * foe.getBbHeight() - PERSON);
		float chance = BASE - SAVE_TAKES * Saves.chance(foe, Saves.Kind.GRAB) - SIZE_TAKES * size + MIGHT_ADDS * Saves.share(player, Stats.Stat.MIGHT);
		return level.getRandom().nextFloat() < Math.max(FLOOR, Math.min(CEILING, chance));
	}
}
