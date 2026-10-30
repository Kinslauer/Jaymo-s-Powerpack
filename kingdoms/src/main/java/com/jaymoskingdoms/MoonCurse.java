package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.415) The moon's curse: a life story road ("Moon-cursed"; {@link #curse} is its entry) and a plain affliction. A cursed
 * player is themselves on every other night, but under a blood moon ({@link Moons.Moon#BLOOD}, {@link Moons.Moon#SUPER_BLOOD}),
 * in the Overworld, they turn feral: Strength and Speed come on them, the screen goes red ({@code MoonCurseClient}), and they
 * can't choose their quarry. Every {@link #LUNGE_EVERY} ticks they spin round to the nearest living thing within
 * {@link #REACH} blocks (a friend, a villager, a horse) and strike it, full swing, with a lunge. It is not the werewolf's
 * curse ({@link Lycans}): no beast shape, nothing at the full moon.
 * <p>
 * Cures: a deliberate healing (the Mend and Mending spells, Restoration, a healer's work: {@link PowersCompat#cureCurses}
 * and {@link PowersCompat#restore}; never the passive Renewal aura or a cleric's rounds), or drinking a Potion of Cleansing (Jaymo's Powers' effect
 * {@code jaymospowers:cleansing}). Operators: {@code /kingdom mooncurse <curse|cure>} on yourself.
 */
public final class MoonCurse {
	// ---- tuning ----
	/** Feral: Strength and Speed (amplifiers), and how long each refresh lasts (ticks). */
	static final int STRENGTH = 1, SPEED = 1, BUFF_TICKS = 50;
	/** How often the curse is looked at (ticks). */
	static final int CHECK_EVERY = 5;
	/** How often they lunge at the nearest thing (ticks), how far they find one (blocks), and how hard they throw themselves (blocks/tick). */
	static final int LUNGE_EVERY = 50;
	static final double REACH = 4.5, LUNGE_PUSH = 0.45;
	/** The Potion of Cleansing's effect (Jaymo's Powers). */
	private static final Identifier CLEANSING = Identifier.fromNamespaceAndPath("jaymospowers", "cleansing");

	/** On the cursed: saved, kept through death; synced to its owner (the red screen reads it). */
	public static final AttachmentType<Boolean> CURSED = AttachmentRegistry.create(JaymosKingdoms.id("moon_cursed"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/** Not saved: who is feral now (so the change is announced once), and when each lunges next. */
	private static final Map<UUID, Boolean> WAS_FERAL = new HashMap<>();
	private static final Map<UUID, Long> NEXT_LUNGE = new HashMap<>();

	private MoonCurse() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MoonCurse::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WAS_FERAL.clear();
			NEXT_LUNGE.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("mooncurse").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("curse").executes(c -> {
					curse(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("cure").executes(c -> cure(c.getSource().getPlayerOrException()) ? 1 : 0)))));
	}

	// ---- queries (both sides) ------------------------------------------------------------------------------------------

	public static boolean cursed(Player player) {
		return Boolean.TRUE.equals(player.getAttached(CURSED));
	}

	/**
	 * Whether {@code player} is feral right now: cursed, in the Overworld, under a blood moon. (The client reads the moon it was
	 * told of, and only once it has faded in.)
	 */
	public static boolean feral(Player player) {
		if (!cursed(player) || player.level().dimension() != Level.OVERWORLD || player.isSpectator() || !player.isAlive()) {
			return false;
		}
		if (player.level() instanceof ServerLevel level) {
			return Moons.blood(level.getServer()) && !Downed.isDowned(player);
		}
		return Moons.clientFade > 0.0F && (Moons.clientMoon == Moons.Moon.BLOOD || Moons.clientMoon == Moons.Moon.SUPER_BLOOD);
	}

	// ---- entries -------------------------------------------------------------------------------------------------------

	/** The life story's entry: {@code player} carries the moon's curse from now on. */
	public static void curse(ServerPlayer player) {
		if (cursed(player)) {
			return;
		}
		player.setAttached(CURSED, true);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.moon_curse.cursed").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
	}

	/** A healer's hands or a Potion of Cleansing: the curse lets go. True if there was one. */
	public static boolean cure(ServerPlayer player) {
		if (!cursed(player)) {
			return false;
		}
		player.removeAttached(CURSED);
		WAS_FERAL.remove(player.getUUID());
		NEXT_LUNGE.remove(player.getUUID());
		removeOurs(player, MobEffects.STRENGTH, STRENGTH);
		removeOurs(player, MobEffects.SPEED, SPEED);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.moon_curse.cured").withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** Takes off only what the curse put on: a hidden, ambient effect of its amplifier and no longer than one refresh. */
	private static void removeOurs(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
		MobEffectInstance instance = player.getEffect(effect);
		if (instance != null && instance.isAmbient() && !instance.isVisible() && instance.getAmplifier() == amplifier && instance.getDuration() <= BUFF_TICKS) {
			player.removeEffect(effect);
		}
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % CHECK_EVERY != 0) {
			return;
		}
		Holder<MobEffect> cleansing = BuiltInRegistries.MOB_EFFECT.get(CLEANSING).map(h -> (Holder<MobEffect>)h).orElse(null);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!cursed(player)) {
				continue;
			}
			if (cleansing != null && player.hasEffect(cleansing)) {
				cure(player);
				continue;
			}
			boolean feral = feral(player);
			Boolean was = WAS_FERAL.put(player.getUUID(), feral);
			if (!feral) {
				continue;
			}
			if (was == null || !was) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.moon_curse.feral").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
				player.level().playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2F, 0.6F);
				NEXT_LUNGE.put(player.getUUID(), now + LUNGE_EVERY / 2);
			}
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, BUFF_TICKS, STRENGTH, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, BUFF_TICKS, SPEED, true, false, false));
			if (now >= NEXT_LUNGE.getOrDefault(player.getUUID(), 0L)) {
				NEXT_LUNGE.put(player.getUUID(), now + LUNGE_EVERY);
				lunge(player);
			}
		}
	}

	/** They turn on the nearest living thing in reach and strike it; the swing is the screen's own. */
	private static void lunge(ServerPlayer player) {
		if (player.isSleeping()) {
			return;
		}
		LivingEntity prey = nearest(player);
		if (prey == null) {
			return;
		}
		player.lookAt(EntityAnchorArgument.Anchor.EYES, prey.getEyePosition());
		Vec3 toward = prey.position().subtract(player.position());
		Vec3 push = new Vec3(toward.x, 0.0, toward.z).normalize().scale(LUNGE_PUSH);
		player.setDeltaMovement(player.getDeltaMovement().add(push.x, 0.15, push.z));
		player.syncVelocity = true;
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		player.attack(prey);
	}

	private static @Nullable LivingEntity nearest(ServerPlayer player) {
		List<LivingEntity> near = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REACH),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !e.isInvulnerable() && player.canAttack(e)
				&& e != player.getVehicle() && !player.hasIndirectPassenger(e) && !e.isPassengerOfSameVehicle(player)
				&& e.distanceToSqr(player) <= REACH * REACH && player.hasLineOfSight(e));
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity e : near) {
			double d = e.distanceToSqr(player);
			if (d < bestDistance) {
				best = e;
				bestDistance = d;
			}
		}
		return best;
	}
}
