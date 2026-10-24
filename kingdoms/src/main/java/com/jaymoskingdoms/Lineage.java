package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.184) Lineage, in place of the Blood Rite. Andy: "add a lineage system that replaces blood rites, now instead
 * unlocking a heritage will allow you to swap perspectives with your offspring, and then use the heritage and new class."
 * An earned heritage you've woken ({@link Heritage#unlock}) doesn't change your own blood: it runs on in your children.
 * Sneak-use one of your own MCA children (past a toddler) with an empty hand and the heritage screen opens for your
 * lineage; pick a heritage you've woken and you live on as that child:
 * <ul>
 * <li>you open your eyes where they stood, with their look and name and their place in MCA's family tree;</li>
 * <li>the body you leave stays where you stood as a villager with your old look and name, an elder already, and takes
 * your old place in the family (your spouse's partner, your other children's parent). It grows old and dies like any
 * elder ({@link OldAge});</li>
 * <li>you take up the heritage, your class is unmade and the class screen asks for a new one, and every skill and
 * attribute point comes back to spend again (your experience stays: the blood remembers).</li>
 * </ul>
 * What you carry stays with you (an inheritance), and so do Jaymo's Powers. It needs MCA; without it nothing happens.
 */
public final class Lineage {
	/** How far the child may have moved off when the choice comes back (blocks). */
	static final double REACH = 8.0;
	/** The moment of waking: darkness this long (ticks). */
	static final int WAKE_DARK_TICKS = 60;

	/** Player → the child whose lineage screen they have up (not saved). */
	private static final Map<UUID, UUID> OPEN = new HashMap<>();

	private Lineage() {
	}

	static void register() {
		// (registered before Pickpocket and Quests: only your own child, with an earned blood waiting)
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown()
				|| !sp.getMainHandItem().isEmpty() || sp.isSpectator() || !(entity instanceof Villager child) || Heritage.of(sp) == null
				|| !waiting(sp) || !McaCompat.childOf(child, sp)) {
				return InteractionResult.PASS;
			}
			if (!child.isAlive() || Downed.isDowned(child) || Downed.isDowned(sp)) {
				return InteractionResult.PASS;
			}
			if (!McaCompat.grownEnough(child)) {
				sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.lineage.too_young", child.getDisplayName()).withStyle(ChatFormatting.GRAY));
				return InteractionResult.SUCCESS;
			}
			if (Heritage.canAsk(sp)) {
				OPEN.put(sp.getUUID(), child.getUUID());
				world.playSound(null, child.getX(), child.getY(), child.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.7F);
				Heritage.askLineage(sp, child.getDisplayName().getString());
			}
			return InteractionResult.SUCCESS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> OPEN.remove(handler.player.getUUID()));
	}

	/** Whether {@code player} has woken an earned heritage that isn't theirs: one their children could carry. */
	static boolean waiting(ServerPlayer player) {
		for (Heritage.Kind kind : Heritage.Kind.values()) {
			if (kind.earned && kind != Heritage.of(player) && Heritage.unlocked(player, kind)) {
				return true;
			}
		}
		return false;
	}

	/** {@code player} picked {@code kind} on a heritage screen after their heritage was set: if it was for their lineage, they live on. */
	static void chose(ServerPlayer player, Heritage.Kind kind) {
		UUID id = OPEN.remove(player.getUUID());
		if (id == null || !kind.earned || kind == Heritage.of(player) || !Heritage.unlocked(player, kind) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		Entity found = level.getEntity(id);
		if (!(found instanceof Villager child) || !child.isAlive() || Downed.isDowned(child) || Downed.isDowned(player) || child.distanceTo(player) > REACH
			|| !McaCompat.childOf(child, player) || !McaCompat.grownEnough(child)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.lineage.gone").withStyle(ChatFormatting.GRAY));
			return;
		}
		liveOn(player, level, child, kind);
	}

	/** {@code player} lives on as {@code child}, of heritage {@code kind}. */
	private static void liveOn(ServerPlayer player, ServerLevel level, Villager child, Heritage.Kind kind) {
		Component parentName = player.getDisplayName();
		Vec3 to = child.position();
		float yaw = child.getYRot(), pitch = child.getXRot();
		long elderSince = level.getGameTime() - CourtWizards.ELDER_DAYS * CourtWizards.DAY_TICKS;
		PathfinderMob parent = McaCompat.takeOver(player, child, elderSince);
		if (parent == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.lineage.gone").withStyle(ChatFormatting.GRAY));
			return;
		}
		// (the old self keeps the old blood)
		Heritage.Kind was = Heritage.of(player);
		if (was != null) {
			VillagerHeritage.born(parent, was, null);
		}
		level.sendParticles(ParticleTypes.SOUL, parent.getX(), parent.getY() + 1.0, parent.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		player.teleportTo(level, to.x, to.y, to.z, Set.of(), yaw, pitch, false);
		player.stopRiding();
		player.clearFire();
		player.setHealth(player.getMaxHealth());
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, WAKE_DARK_TICKS, 0, false, false, false));
		// the new blood, a new class to choose, and every point to spend again
		Heritage.set(player, kind);
		Skills.respec(player);
		Classes.clear(player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.lineage.lives_on", parentName,
			Component.translatable("heritage.jaymoskingdoms." + kind.key())).withStyle(ChatFormatting.GOLD));
		Classes.ask(player, true);
	}
}
