package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.HammerSlam;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.darktide.DarktidePower;
import com.jaymospowers.power.darktide.DarktideStatus;
import com.jaymospowers.power.darktide.DarktideWeapons;
import com.jaymospowers.power.wind.SorasDagger;
import com.jaymospowers.stamina.Feathers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
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
 * (0.17.67) The guard and the parry, for everyone. Andy: "I want parries to be more possible than the system we have, and
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
 * (0.17.98) Balkon's warhammer carries it too, raised while its smash cools down ({@link HammerSlam}).
 * (0.17.169) And Sora's Dagger, every stage, raised while its Flurry cools down ({@link SorasDagger#use}).
 * The powers' own guards (Darktide's, a Z Fighter's bare hands) stay theirs: the weapon guard stands aside for them.
 * <p>
 * (0.18.24) Andy: "make the parry meter consistent across all weapons/ powers that block, including z fighters and
 * darktide". This is now the one parry for everything that blocks. A power's guard tells it when it goes up and down
 * ({@link #powerRaised}, {@link #powerLowered}), asks it what a blow meets ({@link #timing}) and lets it answer a parry
 * ({@link #parried}): the same windows, lock-on bonus, mashing rule and unparryable blows, the same stun, shove, riposte
 * and cue, and the same timing bar ({@link #SHOWN}). The power adds only its own flavour on top (ki, Flow, the katana's
 * slash). Every parry, any kind, fires {@link #PARRIED}.
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
	 * (0.17.67) An attacker carrying this tag while its blow lands can't be parried, only blocked or dodged (Kingdoms puts
	 * it on a fighter's heavy blows, the ones its red glint warns of).
	 */
	public static final String UNPARRYABLE_TAG = "jaymospowers.unparryable";

	/** (0.18.24) What a blow on a raised guard meets. */
	public enum Timing {
		/** Too late, too eager, or a blow that can't be parried: the guard only blocks. */
		BLOCK,
		PARRY,
		PERFECT;

		public boolean parried() {
			return this != BLOCK;
		}
	}

	/**
	 * (0.18.24) Fired on the server for every parry, whatever parried it (a weapon, a shield, a Z Fighter's arms, Darktide's
	 * guard or Gedan): for drills and quests that count parries. {@code attacker} is null for an arrow.
	 */
	@FunctionalInterface
	public interface Parry {
		void parried(ServerPlayer player, @Nullable LivingEntity attacker, boolean perfect);
	}

	public static final Event<Parry> PARRIED = EventFactory.createArrayBacked(Parry.class, listeners -> (player, attacker, perfect) -> {
		for (Parry listener : listeners) {
			listener.parried(player, attacker, perfect);
		}
	});

	/** (0.18.24) The guard's raise, for the owner's timing bar: when it went up, and whether it was too eager to parry. */
	public record Shown(long start, boolean eager) {
		public static final StreamCodec<ByteBuf, Shown> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Shown::start,
			ByteBufCodecs.BOOL, Shown::eager,
			Shown::new);
	}

	/** Set while any guard is up (a weapon's, a shield's or a power's); only its owner gets it. Not saved. */
	public static final AttachmentType<Shown> SHOWN = AttachmentRegistry.create(JaymosPowers.id("guard_shown"),
		builder -> builder.syncWith(Shown.CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Server → the one who parried: draw the cue ({@code perfect}: the bright one). */
	public record Parried(boolean perfect) implements CustomPacketPayload {
		public static final Type<Parried> TYPE = new Type<>(JaymosPowers.id("parried"));
		public static final StreamCodec<ByteBuf, Parried> CODEC = ByteBufCodecs.BOOL.map(Parried::new, Parried::perfect);

		@Override
		public Type<Parried> type() {
			return TYPE;
		}
	}

	/**
	 * One player's raises: when the current one started, whether it's up, whether it parried, whether it was too eager,
	 * and whether it's a power's guard (raised and lowered by the power, not by the item in use).
	 */
	private static final class Raise {
		long start = Long.MIN_VALUE / 2;
		boolean up, parried, eager, power;
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
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(item -> guardable(item) || HammerSlam.isWarhammer(item) || item instanceof SorasDagger, (builder, lookup, item) ->
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
				// a power's guard is raised and lowered by the power; an item raised takes over from it
				if (up && (raise == null || !raise.up || raise.power)) {
					raise(player);
				} else if (!up && raise != null && raise.up && !raise.power) {
					raise.up = false;
					player.removeAttached(SHOWN);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			handler.player.removeAttached(SHOWN);
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
		if (hand != InteractionHand.MAIN_HAND || !weaponGuard(player.getMainHandItem()) || player.isSpectator()
			|| HammerSlam.isWarhammer(player.getMainHandItem().getItem())) {
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown() && player.getMainHandItem().getItem() instanceof SorasDagger) {
			// (0.17.186) sneak + use turns Sora's Dagger in the hand, whatever's in the other ({@link SorasDagger#use})
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

	/** Whether {@code player} holds anything up that blocks (a shield or a weapon's guard; not a warhammer's smash charge). */
	public static boolean raised(Player player) {
		return player.isUsingItem() && player.getUseItem().has(DataComponents.BLOCKS_ATTACKS) && !HammerSlam.charging(player);
	}

	private static Raise raise(ServerPlayer player) {
		Raise raise = RAISES.computeIfAbsent(player.getUUID(), id -> new Raise());
		long now = player.level().getGameTime();
		raise.eager = now - raise.start < EAGER_TICKS && !raise.parried;
		raise.start = now;
		raise.parried = false;
		raise.up = true;
		raise.power = false;
		player.setAttached(SHOWN, new Shown(now, raise.eager));
		return raise;
	}

	/** (0.18.24) A power's own guard went up (a Z Fighter's crossed arms, Darktide's guard): its parry windows start now. */
	public static void powerRaised(ServerPlayer player) {
		raise(player).power = true;
	}

	/** (0.18.24) A power's own guard came down (let go, broken, the power gone). */
	public static void powerLowered(ServerPlayer player) {
		Raise raise = RAISES.get(player.getUUID());
		if (raise != null && raise.power) {
			raise.up = false;
			raise.power = false;
			player.removeAttached(SHOWN);
		}
	}

	/** (0.18.24) How long a power's guard has been up (ticks), or -1 when none is. */
	public static int powerHeld(ServerPlayer player) {
		Raise raise = RAISES.get(player.getUUID());
		return raise != null && raise.up && raise.power ? (int) (player.level().getGameTime() - raise.start) : -1;
	}

	/**
	 * (0.18.24) What a blow from {@code source} meets on {@code player}'s guard, up {@code held} ticks: the one rule for
	 * every guard. A perfect parry in the first {@link #PERFECT_TICKS} (arrows too), a parry up to {@link #PARRY_TICKS}
	 * (melee only), both longer against the one you're locked onto; never for a too-eager raise or an unparryable blow.
	 */
	public static Timing timing(ServerPlayer player, DamageSource source, int held) {
		Entity direct = source.getDirectEntity();
		boolean projectile = direct instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE);
		LivingEntity attacker = source.getEntity() instanceof LivingEntity living && living != player ? living : null;
		return timing(player, attacker, projectile, shot(direct), held);
	}

	/** {@link #timing(ServerPlayer, DamageSource, int)} for a blow known by its parts ({@code shot}: a gun's, never parried). */
	public static Timing timing(ServerPlayer player, @Nullable LivingEntity attacker, boolean projectile, boolean shot, int held) {
		Raise raise = RAISES.get(player.getUUID());
		if (raise == null || !raise.up) {
			raise = raise(player);
		}
		boolean heavy = attacker != null && attacker.entityTags().contains(UNPARRYABLE_TAG) || shot;
		if (raise.eager || heavy) {
			return Timing.BLOCK;
		}
		boolean watching = attacker != null && LockOn.target(player) == attacker;
		if (held <= PERFECT_TICKS + (watching ? LOCK_PERFECT_BONUS : 0)) {
			return Timing.PERFECT;
		}
		if (!projectile && held <= PARRY_TICKS + (watching ? LOCK_PARRY_BONUS : 0)) {
			return Timing.PARRY;
		}
		return Timing.BLOCK;
	}

	/** (0.17.67) A gun's ball or bullet (tagged by whoever fired it, or any of TaCZ's): blocked, never parried. */
	public static boolean shot(@Nullable Entity direct) {
		return direct instanceof Projectile && (direct.entityTags().contains(UNPARRYABLE_TAG)
			|| "tacz".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getNamespace()));
	}

	/** Damage on its way to {@code player}: true when the guard met it whole (a parry), so none of it lands. */
	private static boolean met(ServerPlayer player, DamageSource source, float amount) {
		if (!raised(player) || amount <= 0.0F || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}
		Entity direct = source.getDirectEntity();
		// (0.17.67) a gunman's hitscan shot comes as the gunman himself: a projectile all the same
		boolean projectile = direct instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE);
		boolean melee = !projectile && direct instanceof LivingEntity && direct == source.getEntity();
		if (!projectile && !melee) {
			return false;
		}
		Vec3 from = source.getSourcePosition();
		if (from == null || !inFront(player, from, PARRY_ANGLE)) {
			return false;
		}
		Timing timing = timing(player, source, player.getTicksUsingItem());
		if (timing.parried()) {
			LivingEntity attacker = source.getEntity() instanceof LivingEntity living && living != player ? living : null;
			parried(player, melee ? attacker : null, from, timing == Timing.PERFECT);
			return true;
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

	/**
	 * (0.18.24, public) A parry landed, by any guard: the sparks and ring, the cue on the bar, a perfect one's feathers back,
	 * and the attacker (null for an arrow) shoved, staggered, a player's weapon knocked out of rhythm on a perfect one,
	 * and your riposte owed. {@code from}: where the blow came from.
	 */
	public static void parried(ServerPlayer player, @Nullable LivingEntity attacker, @Nullable Vec3 from, boolean perfect) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (from == null) {
			from = attacker != null ? attacker.getEyePosition() : player.getEyePosition().add(player.getLookAngle());
		}
		Vec3 at = player.getEyePosition().add(from.subtract(player.getEyePosition()).normalize().scale(0.7)).add(0.0, -0.3, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, perfect ? 18 : 10, 0.15, 0.15, 0.15, 0.45);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, perfect ? 14 : 6, 0.1, 0.1, 0.1, 0.25);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, perfect ? 0.45F : 0.25F, perfect ? 2.0F : 1.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, perfect ? 1.5F : 1.2F);
		cue(player, attacker, perfect);
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

	/**
	 * (0.18.24) Only the cue of a parry: the flash on the bar and {@link #PARRIED}, and the raise counts as having parried
	 * (so the next raise isn't too eager). For a parry that answers in its own way (Darktide's Gedan).
	 */
	public static void cue(ServerPlayer player, @Nullable LivingEntity attacker, boolean perfect) {
		Raise raise = RAISES.get(player.getUUID());
		if (raise != null) {
			raise.parried = true;
		}
		ServerPlayNetworking.send(player, new Parried(perfect));
		PARRIED.invoker().parried(player, attacker, perfect);
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
