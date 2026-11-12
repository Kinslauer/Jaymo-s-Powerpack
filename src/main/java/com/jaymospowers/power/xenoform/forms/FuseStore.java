package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.xenoform.XenoformPower;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Fuse's two extras. Smother (Z tap): it throws itself on the nearest lit creeper or primed TNT and puts it out with its
 * own body, and fires round it go out; it takes a small share of what the blast would have done. Stored hurt: damage Fuse
 * takes fills a bar (synced for the HUD); full, sneak + B lets it all go in one blast scaled by what was stored. Below a
 * third health, once per change, a shell makes Fuse untouchable for a few seconds and then bursts.
 */
public final class FuseStore {
	// ---- tuning ----
	/** The bar runs 0 to MAX; each point of damage taken adds this many (before armour; falls and drowning add none). */
	public static final int MAX = 100;
	public static final float GAIN_PER_DAMAGE = 5.0F;
	/** The release: explosion power at an empty-ish bar and the extra at a full one. */
	public static final float RELEASE_POWER = 2.0F, RELEASE_EXTRA = 3.5F;
	/** Smother: how far it throws itself, how far fires go out round the spot, the cooldown, the hurt it takes from a smothered blast. */
	public static final double SMOTHER_REACH = 10.0, SMOTHER_LEAP = 1.1;
	public static final int SMOTHER_FIRE_RADIUS = 4, SMOTHER_COOLDOWN = 80;
	public static final float SMOTHER_HURT = 2.0F;
	/** The last-resort shell: below this share of its health, for this long, then the burst (power, with the bar's share added). */
	public static final float SHELL_BELOW = 1.0F / 3.0F;
	public static final int SHELL_TICKS = 70;
	public static final float SHELL_POWER = 3.0F;
	public static final int COLOR = 0x58A048;

	/** The stored hurt, 0 to {@link #MAX}. Synced to its owner for the HUD; absent when empty. */
	public static final AttachmentType<Integer> STORED = AttachmentRegistry.create(JaymosPowers.id("fuse_stored"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private static final Map<UUID, Long> SHELL_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> SMOTHER_NEXT = new HashMap<>();
	private static final java.util.Set<UUID> SHELL_SPENT = new java.util.HashSet<>();
	private static final java.util.Set<UUID> SHELL_BURSTING = new java.util.HashSet<>();

	private FuseStore() {
	}

	public static int stored(Player player) {
		Integer value = player.getAttached(STORED);
		return value == null ? 0 : value;
	}

	private static void set(ServerPlayer player, int value) {
		if (value <= 0) {
			player.removeAttached(STORED);
		} else {
			player.setAttached(STORED, Math.min(MAX, value));
		}
	}

	/** Fuse is over (or just begun): the bar empties and the shell is ready again. */
	public static void clear(ServerPlayer player) {
		set(player, 0);
		SHELL_UNTIL.remove(player.getUUID());
		SMOTHER_NEXT.remove(player.getUUID());
		SHELL_SPENT.remove(player.getUUID());
	}

	/**
	 * A blow of {@code amount} is about to land on Fuse (its own explosions and Defuse's guard already let it through). False: the
	 * shell takes it. Otherwise it fills the bar, and a blow that would leave Fuse under a third of its health puts the shell up
	 * (once per change) and is taken by the shell.
	 */
	public static boolean hurt(ServerPlayer player, DamageSource source, float amount) {
		UUID id = player.getUUID();
		if (SHELL_UNTIL.containsKey(id)) {
			return source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
		}
		if (amount <= 0.0F || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
			return true;
		}
		boolean unavoidable = source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (!unavoidable && !SHELL_SPENT.contains(id) && player.getHealth() - amount < player.getMaxHealth() * SHELL_BELOW) {
			SHELL_SPENT.add(id);
			SHELL_UNTIL.put(id, player.level().getGameTime() + SHELL_TICKS);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.fuse.shell").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 1.6F, 0.5F);
			return false;
		}
		set(player, stored(player) + Math.round(amount * GAIN_PER_DAMAGE));
		return true;
	}

	/** Every tick as Fuse: the shell holds, then bursts. */
	public static void tick(ServerPlayer player) {
		Long until = SHELL_UNTIL.get(player.getUUID());
		if (until == null) {
			return;
		}
		ServerLevel level = player.level();
		if (player.tickCount % 4 == 0) {
			MagicFx.orb(level, player.position().add(0.0, player.getBbHeight() * 0.5, 0.0), 1.1F, COLOR, 6);
		}
		if (level.getGameTime() >= until) {
			SHELL_UNTIL.remove(player.getUUID());
			burst(player, SHELL_POWER + RELEASE_EXTRA * share(player));
			set(player, 0);
		}
	}

	private static float share(Player player) {
		return Math.min(1.0F, stored(player) / (float)MAX);
	}

	/** Sneak + B: when the bar is full, all of it goes in one blast. */
	public static boolean release(ServerPlayer player) {
		if (stored(player) < MAX) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.fuse.not_full").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return false;
		}
		burst(player, RELEASE_POWER + RELEASE_EXTRA * share(player));
		set(player, 0);
		return true;
	}

	/** The blast at Fuse's feet: its own, so it never hurts it (Fuse.allowDamage); blocks only where mob griefing allows. */
	private static void burst(ServerPlayer player, float power) {
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 0.5, 0.0);
		MagicFx.ring(level, player.position(), power * 2.0F, COLOR);
		MagicFx.blast(level, at, power, COLOR);
		level.explode(player, at.x, at.y, at.z, power, Level.ExplosionInteraction.MOB);
	}

	// ---------------------------------------------------------------- smother

	/** Z tap: smother the nearest lit creeper or primed TNT (and the fires round it), or just the fires round Fuse. */
	public static boolean smother(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (SMOTHER_NEXT.getOrDefault(player.getUUID(), 0L) > now) {
			return false;
		}
		Entity threat = null;
		double best = SMOTHER_REACH * SMOTHER_REACH;
		var box = player.getBoundingBox().inflate(SMOTHER_REACH);
		for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, box, c -> c.isAlive() && (c.getSwellDir() > 0 || c.isIgnited()))) {
			if (creeper.distanceToSqr(player) < best) {
				best = creeper.distanceToSqr(player);
				threat = creeper;
			}
		}
		for (PrimedTnt tnt : level.getEntitiesOfClass(PrimedTnt.class, box)) {
			if (tnt.distanceToSqr(player) < best) {
				best = tnt.distanceToSqr(player);
				threat = tnt;
			}
		}
		Vec3 spot = threat != null ? threat.position() : player.position();
		int fires = putOutFires(player, level, BlockPos.containing(spot));
		if (threat == null && fires == 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.fuse.nothing").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return false;
		}
		if (threat != null) {
			Vec3 to = threat.position().subtract(player.position());
			player.setDeltaMovement(to.normalize().scale(Math.min(SMOTHER_LEAP, 0.4 + to.length() * 0.2)).add(0.0, 0.2, 0.0));
			player.syncVelocity = true;
			if (threat instanceof PrimedTnt tnt) {
				level.addFreshEntity(new ItemEntity(level, tnt.getX(), tnt.getY(), tnt.getZ(), new ItemStack(Items.TNT)));
				tnt.discard();
			} else if (threat instanceof Creeper creeper) {
				if (creeper.isIgnited()) {
					level.addFreshEntity(new ItemEntity(level, creeper.getX(), creeper.getY(), creeper.getZ(), new ItemStack(Items.GUNPOWDER, 2)));
					creeper.discard();
				} else {
					creeper.setSwellDir(-1);
				}
			}
			MagicFx.blast(level, spot.add(0.0, 0.6, 0.0), 1.4F, 0x808080);
			player.hurtServer(level, level.damageSources().generic(), SMOTHER_HURT);
		}
		player.clearFire();
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.sendParticles(ParticleTypes.SMOKE, spot.x, spot.y + 0.5, spot.z, 14, 0.4, 0.3, 0.4, 0.02);
		SMOTHER_NEXT.put(player.getUUID(), now + SMOTHER_COOLDOWN);
		return true;
	}

	/** Every fire within {@link #SMOTHER_FIRE_RADIUS} of {@code centre} goes out (where the player may build); how many. */
	private static int putOutFires(ServerPlayer player, ServerLevel level, BlockPos centre) {
		int out = 0;
		int r = SMOTHER_FIRE_RADIUS;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
			if (level.getBlockState(pos).is(BlockTags.FIRE) && player.mayInteract(level, pos)) {
				level.removeBlock(pos, false);
				out++;
			}
		}
		for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(centre).inflate(r), Entity::isOnFire)) {
			e.clearFire();
		}
		return out;
	}
}
