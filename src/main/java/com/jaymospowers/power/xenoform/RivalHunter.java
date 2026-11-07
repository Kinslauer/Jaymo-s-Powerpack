package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterNpcs;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.125, Xeno-Band patch 8) The band hunter: an outcast (an MCA villager, Kingdoms' "band_hunter") with the world's
 * other band, the rival band, dark red on his wrist. While the rival band is out of the world (nobody wears it and none is
 * loose, {@link XenoBands#rivalFree}), some night a Xeno-Band wearer in the overworld may draw him: he comes in from the
 * dark and walks them down. Near enough, he changes ({@link RivalAlien}: one of his four darkened aliens) and fights;
 * he goes unseen and untouchable inside it. When it falls (or its time runs out) he's back, dazed: beaten. Tap him then
 * (use) and you tear the band off his wrist, and he runs. Kill him outright and it falls with him. Gone from the world
 * any other way, the band goes with him (back out of the world: another hunter may come).
 * <p>
 * His wrist counts as one loose rival band ({@link XenoBands#RIVAL_COUNT}). Powers drives him; Kingdoms only dresses
 * him and gives him his part (it leaves his steps to us, as it does the patrol's).
 */
public final class RivalHunter {
	// ---- tuning ----
	/** His tag; and the tag he has once the band's torn off him and he runs. */
	public static final String TAG = "jaymospowers_band_hunter", FLED_TAG = "jaymospowers_band_hunter_fled";
	/** How often (ticks) the overworld is checked for a hunter's coming, the chance then (per check), and the least gap between hunters. */
	public static final int COME_EVERY = 600;
	public static final float COME_CHANCE = 0.12F;
	public static final long COME_GAP = 72000L;
	/** How far off he comes in (blocks). */
	public static final double ARRIVE_MIN = 30.0, ARRIVE_MAX = 40.0;
	/** How often (ticks) he's steered; how far he looks for a wearer, and how near before he changes (and needs to see them). */
	public static final int STEP_EVERY = 5;
	public static final double HUNT_RANGE = 96.0, CHANGE_RANGE = 12.0, WALK_SPEED = 0.8;
	/** After he's back in his body: dazed this long (ticks; the tap tears it off), and again this long before he can change. */
	public static final int BEATEN_TICKS = 300, CHANGE_COOLDOWN = 900;
	/** Fled: runs this long (ticks), then he's gone. */
	public static final int FLEE_TICKS = 240;
	/** How many names he may go by ({@code npc.jaymospowers.band_hunter.<n>}). */
	static final int NAMES = 4;

	/** When the next hunter may come (overworld game time). */
	public static final AttachmentType<Long> NEXT = AttachmentRegistry.create(JaymosPowers.id("rival_hunter_next"),
		builder -> builder.persistent(Codec.LONG));

	/** Each hunter's fight, by his UUID (not saved: after a reload he simply hunts on). */
	private static final class Hunt {
		int alien = -1;
		long ready, beatenUntil, fledAt = -1;
	}

	private static final Map<UUID, Hunt> HUNTS = new HashMap<>();

	private RivalHunter() {
	}

	static void init() {
		RivalAlien.init();
		ServerTickEvents.END_LEVEL_TICK.register(RivalHunter::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HUNTS.clear());
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !entity.entityTags().contains(TAG) || !beaten(entity)) {
				return InteractionResult.PASS;
			}
			tearOff(sp, (PathfinderMob)entity);
			return InteractionResult.SUCCESS;
		});
		// killed outright: his band falls with him
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.entityTags().contains(TAG) && entity.level() instanceof ServerLevel level) {
				entity.removeTag(TAG);
				entity.spawnAtLocation(level, XenoBands.item(XenoBandItem.RIVAL_ALIENS, true, null));
				HUNTS.remove(entity.getUUID());
			}
		});
		// gone any other way, with the band still on him: it's out of the world again
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity.entityTags().contains(TAG) && entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) {
				XenoBands.destroyed(level.getServer(), true);
				HUNTS.remove(entity.getUUID());
			}
		});
	}

	static boolean is(Entity e) {
		return e instanceof PathfinderMob && e.entityTags().contains(TAG);
	}

	/** Whether {@code e} is a hunter lying dazed, his band there for the taking. */
	public static boolean beaten(Entity e) {
		Hunt hunt = HUNTS.get(e.getUUID());
		return hunt != null && hunt.alien < 0 && e.level().getGameTime() < hunt.beatenUntil;
	}

	// ---------------------------------------------------------------- coming

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (level.dimension() == Level.OVERWORLD && now % COME_EVERY == 0) {
			come(level, now);
		}
		if (now % STEP_EVERY != 0 || level.players().isEmpty()) {
			return;
		}
		List<PathfinderMob> hunters = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(HUNT_RANGE),
				e -> e.isAlive() && (e.entityTags().contains(TAG) || e.entityTags().contains(FLED_TAG)))) {
				if (!hunters.contains(mob)) {
					hunters.add(mob);
				}
			}
		}
		for (PathfinderMob hunter : hunters) {
			if (hunter.entityTags().contains(FLED_TAG)) {
				flee(level, hunter, now);
			} else {
				step(level, hunter, now);
			}
		}
	}

	/** Some night, while the rival band is out of the world: a hunter comes for one of the wearers. */
	private static void come(ServerLevel level, long now) {
		MinecraftServer server = level.getServer();
		if (!level.isDarkOutside() || !XenoBands.rivalFree(server) || !ZFighterNpcs.villagers()) {
			return;
		}
		Long next = level.getAttached(NEXT);
		if (next != null && now < next) {
			return;
		}
		List<ServerPlayer> wearers = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && !player.isCreative() && XenoformPower.has(player) && !XenoformState.rival(player)) {
				wearers.add(player);
			}
		}
		if (wearers.isEmpty() || level.getRandom().nextFloat() >= COME_CHANCE) {
			return;
		}
		ServerPlayer prey = wearers.get(level.getRandom().nextInt(wearers.size()));
		if (arrive(level, prey) != null) {
			level.setAttached(NEXT, now + COME_GAP);
		}
	}

	/** A hunter, in from the dark {@link #ARRIVE_MIN}-{@link #ARRIVE_MAX} blocks from {@code prey}; null if there was nowhere to stand. */
	public static @Nullable LivingEntity arrive(ServerLevel level, ServerPlayer prey) {
		for (int tries = 0; tries < 10; tries++) {
			float yaw = level.getRandom().nextFloat() * Mth.TWO_PI;
			double d = ARRIVE_MIN + level.getRandom().nextDouble() * (ARRIVE_MAX - ARRIVE_MIN);
			int x = Mth.floor(prey.getX() - Mth.sin(yaw) * d);
			int z = Mth.floor(prey.getZ() + Mth.cos(yaw) * d);
			if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos at = new BlockPos(x, y, z);
			if (!level.getBlockState(at.below()).isSolid() || !level.getFluidState(at.below()).isEmpty() || Math.abs(y - prey.getY()) > 12.0) {
				continue;
			}
			LivingEntity hunter = ZFighterNpcs.villager(level, Vec3.atBottomCenterOf(at), yaw * Mth.RAD_TO_DEG, TAG, "npc.jaymospowers.band_hunter." + level.getRandom().nextInt(NAMES), false);
			if (hunter == null) {
				return null;
			}
			XenoBands.loosed(level.getServer(), true);
			HUNTS.put(hunter.getUUID(), new Hunt());
			prey.sendSystemMessage(Component.translatable("message.jaymospowers.xenoform.rival_near").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			level.playSound(null, prey.getX(), prey.getY(), prey.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.5F);
			return hunter;
		}
		return null;
	}

	// ---------------------------------------------------------------- the hunt

	private static void step(ServerLevel level, PathfinderMob hunter, long now) {
		Hunt hunt = HUNTS.computeIfAbsent(hunter.getUUID(), u -> new Hunt());
		if (hunt.alien >= 0) {
			if (level.getEntity(hunt.alien) instanceof RivalAlien alien && alien.isAlive() && !alien.isRemoved()) {
				// unseen inside it, carried along with it
				hunter.teleportTo(alien.getX(), alien.getY(), alien.getZ());
				hunter.getNavigation().stop();
				return;
			}
			back(level, hunter, hunt, now);
			return;
		}
		if (hunter.isInvisible()) {
			// a reload while he was changed: just himself again
			show(hunter, true);
		}
		if (now < hunt.beatenUntil) {
			hunter.getNavigation().stop();
			if (now % 20 == 0) {
				level.sendParticles(ParticleTypes.CRIT, hunter.getX(), hunter.getEyeY() + 0.4, hunter.getZ(), 4, 0.25, 0.1, 0.25, 0.0);
			}
			return;
		}
		ServerPlayer prey = prey(level, hunter);
		if (prey == null) {
			hunter.getNavigation().stop();
			return;
		}
		hunter.getLookControl().setLookAt(prey, 30.0F, 30.0F);
		double d = hunter.distanceTo(prey);
		if (d <= CHANGE_RANGE && now >= hunt.ready && hunter.hasLineOfSight(prey)) {
			change(level, hunter, hunt, prey);
			return;
		}
		if (d > 3.0) {
			hunter.getNavigation().moveTo(prey, WALK_SPEED);
		} else {
			hunter.getNavigation().stop();
		}
	}

	/** The nearest Xeno-Band wearer he can hunt. */
	private static @Nullable ServerPlayer prey(ServerLevel level, PathfinderMob hunter) {
		ServerPlayer best = null;
		double bestD = HUNT_RANGE * HUNT_RANGE;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative() || !player.isAlive() || !XenoformPower.has(player) || XenoformState.rival(player)) {
				continue;
			}
			double d = player.distanceToSqr(hunter);
			if (d < bestD) {
				best = player;
				bestD = d;
			}
		}
		return best;
	}

	/** He slaps the rival band and becomes one of its darkened aliens. */
	private static void change(ServerLevel level, PathfinderMob hunter, Hunt hunt, ServerPlayer prey) {
		String id = XenoBandItem.RIVAL_ALIENS.get(level.getRandom().nextInt(XenoBandItem.RIVAL_ALIENS.size()));
		XenoForm form = XenoForms.get(id);
		RivalAlien alien = RivalAlien.TYPE.create(level, EntitySpawnReason.EVENT);
		if (form == null || alien == null) {
			hunt.ready = level.getGameTime() + CHANGE_COOLDOWN;
			return;
		}
		alien.snapTo(hunter.getX(), hunter.getY(), hunter.getZ(), hunter.getYRot(), 0.0F);
		alien.become(form, hunter.getUUID());
		alien.setCustomName(form.name().copy().withStyle(ChatFormatting.DARK_RED));
		alien.setTarget(prey);
		level.addFreshEntity(alien);
		hunt.alien = alien.getId();
		hunter.getNavigation().stop();
		show(hunter, false);
		flash(level, hunter.position().add(0.0, 1.0, 0.0));
		level.playSound(null, hunter.getX(), hunter.getY(), hunter.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.0F, 0.6F);
		prey.sendSystemMessage(Component.translatable("message.jaymospowers.xenoform.rival_changes", form.name()).withStyle(ChatFormatting.DARK_RED));
	}

	/** His alien fell (or its time ran out): he's himself again, dazed. */
	private static void back(ServerLevel level, PathfinderMob hunter, Hunt hunt, long now) {
		hunt.alien = -1;
		hunt.beatenUntil = now + BEATEN_TICKS;
		hunt.ready = now + BEATEN_TICKS + CHANGE_COOLDOWN;
		show(hunter, true);
		hunter.getNavigation().stop();
		flash(level, hunter.position().add(0.0, 1.0, 0.0));
		level.playSound(null, hunter.getX(), hunter.getY(), hunter.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.0F, 0.6F);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(hunter) < 24.0 * 24.0) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.rival_beaten").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	private static void show(PathfinderMob hunter, boolean seen) {
		hunter.setInvisible(!seen);
		hunter.setPermanentlyInvulnerable(!seen);
	}

	private static void flash(ServerLevel level, Vec3 at) {
		level.sendParticles(new DustParticleOptions(0xFF3030, 1.6F), at.x, at.y, at.z, 30, 0.4, 0.7, 0.4, 0.0);
	}

	// ---------------------------------------------------------------- taking it

	/** {@code player} tears the rival band off the dazed hunter's wrist; he runs. */
	private static void tearOff(ServerPlayer player, PathfinderMob hunter) {
		ServerLevel level = player.level();
		hunter.removeTag(TAG);
		hunter.addTag(FLED_TAG);
		Hunt hunt = HUNTS.computeIfAbsent(hunter.getUUID(), u -> new Hunt());
		hunt.beatenUntil = 0;
		hunt.fledAt = level.getGameTime();
		// still one loose rival band: now it's in the taker's hands
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		player.getInventory().placeItemBackInInventory(XenoBands.item(XenoBandItem.RIVAL_ALIENS, true, null), net.minecraft.util.Prediction.SERVER_ONLY);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.xenoform.rival_taken").withStyle(ChatFormatting.DARK_RED));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 0.8F);
	}

	/** Bandless, he runs from whoever's nearest, and is gone. */
	private static void flee(ServerLevel level, PathfinderMob hunter, long now) {
		Hunt hunt = HUNTS.computeIfAbsent(hunter.getUUID(), u -> {
			Hunt h = new Hunt();
			h.fledAt = now;
			return h;
		});
		if (now - hunt.fledAt > FLEE_TICKS) {
			HUNTS.remove(hunter.getUUID());
			hunter.discard();
			return;
		}
		net.minecraft.world.entity.player.Player near = level.getNearestPlayer(hunter, 32.0);
		if (near != null && hunter.getNavigation().isDone()) {
			Vec3 away = hunter.position().subtract(near.position()).multiply(1.0, 0.0, 1.0).normalize().scale(16.0);
			Vec3 to = hunter.position().add(away);
			hunter.getNavigation().moveTo(to.x, to.y, to.z, 1.1);
		}
	}

	// ---------------------------------------------------------------- talk

	/** "Talk about work" with him: a few words, never kind. */
	public static void talk(ServerPlayer player, LivingEntity hunter) {
		String key = hunter.entityTags().contains(FLED_TAG) ? "fled" : beaten(hunter) ? "beaten" : "hunting";
		int n = hunter.getRandom().nextInt(3);
		ZFighterNpcs.ask(player, hunter, Component.translatable("talk.jaymospowers.band_hunter." + key + "." + n), List.of());
	}
}
