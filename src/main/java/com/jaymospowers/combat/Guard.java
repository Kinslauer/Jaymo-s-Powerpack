package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.darktide.DarktidePower;
import com.jaymospowers.power.darktide.DarktideStatus;
import com.jaymospowers.power.darktide.DarktideWeapons;
import com.jaymospowers.stamina.Feathers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.64) The guard and the parry, for everyone. Andy: "I want parries to be more possible than the system we have, and
 * make combat more dynamic now that we have lockons". Our own after Mallard Guard's idea (zeli-024, All Rights Reserved,
 * NeoForge 1.21.1 only: nothing of it is copied): one button, and how long the guard has been up decides what a blow
 * meets. See {@code docs/guard.md}.
 * <ul>
 * <li>Every melee weapon and tool that does nothing on right click (swords, axes, maces, pickaxes, other mods' blades)
 * now raises a guard on it: vanilla's own blocking ({@link #WEAPON_GUARD}), so the arm comes up for everyone and axes
 * knock it aside as they do a shield. It takes {@link #GUARD_SHARE} of a blow from the front and costs feathers
 * ({@link #BLOCK_COST_PER_DAMAGE}); with none left the guard breaks ({@link #BREAK_SECONDS}, a short stagger).</li>
 * <li>Shields (and anything else that blocks) keep their full block and gain the same parry.</li>
 * <li>The first {@link #PERFECT_TICKS} of a raise are the perfect parry: nothing taken, the attacker thrown back and
 * stunned {@link #PERFECT_STUN}, a player's weapon knocked out of rhythm, feathers back, and your next blow on them
 * within {@link #PERFECT_RIPOSTE_TICKS} is a riposte ({@link #PERFECT_RIPOSTE}x). Arrows too.</li>
 * <li>Up to {@link #PARRY_TICKS} it's a parry: nothing taken, a smaller shove and stun, a lighter riposte.</li>
 * <li>Locked on (middle mouse) to the one swinging, both windows are longer ({@link #LOCK_PERFECT_BONUS},
 * {@link #LOCK_PARRY_BONUS}): watch your foe and the timing is easier.</li>
 * <li>A heavy blow (a fighter's, warned of by a red glint as it winds up: see Kingdoms' MobCombat) can't be parried.</li>
 * <li>Mashing doesn't work: a raise within {@link #EAGER_TICKS} of the last one, when that one parried nothing, is only
 * a block. A parry clears it, so a combo can be parried blow by blow.</li>
 * </ul>
 * The powers' own guards (Darktide's, a Z Fighter's bare hands) stay theirs: the weapon guard stands aside for them.
 */
public final class Guard {
	/** A blow in the first this-many ticks of a raise is a perfect parry; up to the second, a parry. */
	public static final int PERFECT_TICKS = 5, PARRY_TICKS = 12;
	/** Extra ticks on both windows against the one you're locked onto. */
	public static final int LOCK_PERFECT_BONUS = 2, LOCK_PARRY_BONUS = 4;
	/** A raise this soon after the last one's start, when that one parried nothing, has no parry window. */
	public static final int EAGER_TICKS = 15;
	/** The weapon guard's share of a frontal blow, and its reach round your front (degrees each side, as a shield's). */
	public static final float GUARD_SHARE = 0.6F, GUARD_ANGLE = 90.0F;
	/** Feathers (halves) a blocked blow costs per point of damage, at least {@link #BLOCK_COST_MIN}; shields cost none. */
	public static final float BLOCK_COST_PER_DAMAGE = 0.6F;
	public static final int BLOCK_COST_MIN = 1;
	/** A broken guard: how long the weapon can't guard (seconds), and how long you reel (ticks). */
	public static final float BREAK_SECONDS = 2.0F;
	public static final int BREAK_STAGGER = 15;
	/** Parries only meet what comes from this far round your front (degrees each side). */
	public static final double PARRY_ANGLE = 75.0;
	/** Perfect parry: attacker's stun (ticks), shove, a player attacker's weapon cooldown (ticks), feathers back, riposte. */
	public static final int PERFECT_STUN = 30, PERFECT_DISARM_TICKS = 20, PERFECT_REFUND = 4, PERFECT_RIPOSTE_TICKS = 40;
	public static final double PERFECT_SHOVE = 0.8;
	public static final float PERFECT_RIPOSTE = 1.5F;
	/** Parry: the same, lighter. */
	public static final int PARRY_STUN = 12, PARRY_RIPOSTE_TICKS = 25;
	public static final double PARRY_SHOVE = 0.45;
	public static final float PARRY_RIPOSTE = 1.25F;
	/**
	 * (0.17.64) An attacker carrying this tag while its blow lands can't be parried, only blocked or dodged (Kingdoms puts
	 * it on a fighter's heavy blows, the ones its red glint warns of).
	 */
	public static final String UNPARRYABLE_TAG = "jaymospowers.unparryable";

	/** Server → the one who parried: draw the cue ({@code perfect}: the bright one). */
	public record Parried(boolean perfect) implements CustomPacketPayload {
		public static final Type<Parried> TYPE = new Type<>(JaymosPowers.id("parried"));
		public static final StreamCodec<ByteBuf, Parried> CODEC = ByteBufCodecs.BOOL.map(Parried::new, Parried::perfect);

		@Override
		public Type<Parried> type() {
			return TYPE;
		}
	}

	/** One player's raises: when the current one started, whether it's up, whether it parried, whether it was too eager. */
	private static final class Raise {
		long start = Long.MIN_VALUE / 2;
		boolean up, parried, eager;
	}

	/** A riposte owed: on whom, until when, how much harder. */
	private record Riposte(UUID target, long until, float factor) {
	}

	private static final Map<UUID, Raise> RAISES = new HashMap<>();
	private static final Map<UUID, Riposte> RIPOSTES = new HashMap<>();

	private Guard() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Parried.TYPE, Parried.CODEC);
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(Guard::guardable, (builder, lookup, item) ->
			builder.set(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(0.0F, 1.0F,
				List.of(new BlocksAttacks.DamageReduction(GUARD_ANGLE, Optional.empty(), 0.0F, GUARD_SHARE)),
				new BlocksAttacks.ItemDamageFunction(3.0F, 0.0F, 0.5F),
				Optional.of(lookup.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
				Optional.of(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.TRIDENT_HIT)),
				Optional.of(SoundEvents.SHIELD_BREAK)))));
		UseItemCallback.EVENT.register(Guard::use);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player && met(player, source, amount)));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				boolean up = raised(player);
				Raise raise = RAISES.get(player.getUUID());
				if (up && (raise == null || !raise.up)) {
					raise(player);
				} else if (!up && raise != null) {
					raise.up = false;
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			RAISES.remove(handler.player.getUUID());
			RIPOSTES.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			RAISES.clear();
			RIPOSTES.clear();
		});
	}

	/**
	 * An item that gets the weapon guard: a weapon (or a tool, which vanilla counts as one) with nothing else on right
	 * click: no block of its own, no eating, no spear's charge, no {@code use} of its own class (tridents, bows, staves).
	 */
	private static boolean guardable(Item item) {
		DataComponentMap components = item.components();
		if (!components.has(DataComponents.WEAPON) || components.has(DataComponents.BLOCKS_ATTACKS) || components.has(DataComponents.CONSUMABLE)
			|| components.has(DataComponents.KINETIC_WEAPON) || components.has(DataComponents.EQUIPPABLE)) {
			return false;
		}
		try {
			return item.getClass().getMethod("use", Level.class, Player.class, InteractionHand.class).getDeclaringClass() == Item.class;
		} catch (NoSuchMethodException | SecurityException e) {
			return false;
		}
	}

	/** A weapon held up as a guard (ours, or a modded weapon that blocks), as opposed to a shield. */
	public static boolean weaponGuard(ItemStack stack) {
		return stack.has(DataComponents.BLOCKS_ATTACKS) && stack.has(DataComponents.WEAPON);
	}

	/**
	 * Right click with a guarding weapon in the main hand (both sides). It stands aside, so the off hand gets its turn
	 * (a torch, food, a shield, a bow), when the off hand holds something that isn't a weapon; and for a Darktide's own
	 * guard.
	 */
	private static InteractionResult use(Player player, Level level, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !weaponGuard(player.getMainHandItem()) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack off = player.getOffhandItem();
		if (!off.isEmpty() && !off.has(DataComponents.WEAPON)) {
			return InteractionResult.FAIL;
		}
		if (DarktidePower.has(player) && DarktideWeapons.canGuard(player)) {
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	/** Whether {@code player} holds anything up that blocks (a shield or a weapon's guard). */
	private static boolean raised(Player player) {
		return player.isUsingItem() && player.getUseItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	private static Raise raise(ServerPlayer player) {
		Raise raise = RAISES.computeIfAbsent(player.getUUID(), id -> new Raise());
		long now = player.level().getGameTime();
		raise.eager = now - raise.start < EAGER_TICKS && !raise.parried;
		raise.start = now;
		raise.parried = false;
		raise.up = true;
		return raise;
	}

	/** (0.17.64) A gun's ball or bullet (tagged by whoever fired it, or any of TaCZ's): blocked, never parried. */
	private static boolean shot(@Nullable Entity direct) {
		return direct instanceof Projectile && (direct.entityTags().contains(UNPARRYABLE_TAG)
			|| "tacz".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getNamespace()));
	}

	/** Damage on its way to {@code player}: true when the guard met it whole (a parry), so none of it lands. */
	private static boolean met(ServerPlayer player, DamageSource source, float amount) {
		if (!raised(player) || amount <= 0.0F || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}
		Entity direct = source.getDirectEntity();
		// (0.17.64) a gunman's hitscan shot comes as the gunman himself: a projectile all the same
		boolean projectile = direct instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE);
		boolean melee = !projectile && direct instanceof LivingEntity && direct == source.getEntity();
		if (!projectile && !melee) {
			return false;
		}
		Vec3 from = source.getSourcePosition();
		if (from == null || !inFront(player, from, PARRY_ANGLE)) {
			return false;
		}
		Raise raise = RAISES.get(player.getUUID());
		if (raise == null || !raise.up) {
			raise = raise(player);
		}
		LivingEntity attacker = source.getEntity() instanceof LivingEntity living && living != player ? living : null;
		boolean watching = attacker != null && LockOn.target(player) == attacker;
		int held = player.getTicksUsingItem();
		boolean heavy = attacker != null && attacker.entityTags().contains(UNPARRYABLE_TAG) || shot(direct);
		if (!raise.eager && !heavy) {
			if (held <= PERFECT_TICKS + (watching ? LOCK_PERFECT_BONUS : 0)) {
				parry(player, raise, melee ? attacker : null, source, true);
				return true;
			}
			if (melee && held <= PARRY_TICKS + (watching ? LOCK_PARRY_BONUS : 0)) {
				parry(player, raise, attacker, source, false);
				return true;
			}
		}
		ItemStack guard = player.getUseItem();
		if (melee && weaponGuard(guard) && player.isBlocking() && !Feathers.spend(player, Math.max(BLOCK_COST_MIN, Mth.ceil(amount * BLOCK_COST_PER_DAMAGE)))) {
			guardBreak(player, guard);
		}
		return false;
	}

	private static boolean inFront(ServerPlayer player, Vec3 from, double degrees) {
		Vec3 to = from.subtract(player.position());
		Vec3 flat = new Vec3(to.x, 0.0, to.z);
		if (flat.lengthSqr() < 1.0E-4) {
			return true;
		}
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		return flat.normalize().dot(look) >= Math.cos(Math.toRadians(degrees));
	}

	private static void parry(ServerPlayer player, Raise raise, @Nullable LivingEntity attacker, DamageSource source, boolean perfect) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		raise.parried = true;
		Vec3 from = source.getSourcePosition();
		Vec3 at = player.getEyePosition().add(from.subtract(player.getEyePosition()).normalize().scale(0.7)).add(0.0, -0.3, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, perfect ? 18 : 10, 0.15, 0.15, 0.15, 0.45);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, perfect ? 14 : 6, 0.1, 0.1, 0.1, 0.25);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, perfect ? 0.45F : 0.25F, perfect ? 2.0F : 1.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, perfect ? 1.5F : 1.2F);
		ServerPlayNetworking.send(player, new Parried(perfect));
		Shakes.send(player, perfect ? 1.2F : 0.6F, from);
		if (perfect) {
			Feathers.refund(player, PERFECT_REFUND);
		}
		if (attacker == null || !attacker.isAlive()) {
			return;
		}
		Vec3 away = attacker.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() > 1.0E-4) {
			double shove = perfect ? PERFECT_SHOVE : PARRY_SHOVE;
			away = away.normalize();
			attacker.push(away.x * shove, 0.15, away.z * shove);
		}
		attacker.needsSync = true;
		stagger(attacker, perfect ? PERFECT_STUN : PARRY_STUN);
		if (perfect && attacker instanceof Player foe && !foe.getMainHandItem().isEmpty()) {
			foe.getCooldowns().addCooldown(foe.getMainHandItem(), PERFECT_DISARM_TICKS);
		}
		RIPOSTES.put(player.getUUID(), new Riposte(attacker.getUUID(), now + (perfect ? PERFECT_RIPOSTE_TICKS : PARRY_RIPOSTE_TICKS),
			perfect ? PERFECT_RIPOSTE : PARRY_RIPOSTE));
	}

	private static void guardBreak(ServerPlayer player, ItemStack guard) {
		BlocksAttacks blocks = guard.get(DataComponents.BLOCKS_ATTACKS);
		if (blocks != null) {
			blocks.disable(player.level(), player, BREAK_SECONDS, guard);
		}
		player.stopUsingItem();
		stagger(player, BREAK_STAGGER);
		player.level().sendParticles(ParticleTypes.CRIT, player.getX(), player.getEyeY() - 0.3, player.getZ(), 8, 0.3, 0.2, 0.3, 0.2);
	}

	/** Reeling: a mob's mind stops (Darktide's stun), a player is slowed and weakened for as long. */
	private static void stagger(LivingEntity target, int ticks) {
		DarktideStatus.stun(target, ticks);
		if (target instanceof Player) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 2, false, false));
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, false));
		}
	}

	/**
	 * The attacker side of every hit (LivingEntity.hurtServer's damage): a riposte owed by {@code victim} to the player
	 * striking lands harder, once.
	 */
	public static float riposte(LivingEntity victim, DamageSource source, float damage) {
		if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player || RIPOSTES.isEmpty()) {
			return damage;
		}
		Riposte owed = RIPOSTES.get(player.getUUID());
		if (owed == null || !owed.target().equals(victim.getUUID())) {
			return damage;
		}
		RIPOSTES.remove(player.getUUID());
		ServerLevel level = player.level();
		if (level.getGameTime() > owed.until()) {
			return damage;
		}
		Vec3 at = victim.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 16, 0.3, 0.4, 0.3, 0.4);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.3, 0.4, 0.3, 0.5);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.8F);
		return damage * owed.factor();
	}
}
