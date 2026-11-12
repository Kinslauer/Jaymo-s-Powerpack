package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A hunter's pack, for a player who holds the Hunter job ({@link Careers.Job#HUNTER}). Tuning is in the constants.
 * <ul>
 * <li><b>The pack</b>: a tamed wolf that is always with you (a Master keeps two). When one falls, the lodge sends
 * another after {@link #REPLACE_TICKS}. One that has strayed far or been left in another land comes to you. Kept in
 * the {@link #PACK} attachment (saved, kept through death).</li>
 * <li><b>Mark a quarry</b>: use a bone on a beast. It glows and takes {@link #MARK_BONUS} more from you and your
 * pack for {@link #MARK_TICKS} (marks are not saved: a reload forgets them).</li>
 * <li><b>Send and call</b>: a bone used in the air sends the pack at the marked quarry; sneaking with it calls
 * the pack back to your side.</li>
 * <li><b>Journeyman</b>: the pack follows a quarry's scent when it is out of sight, mends on its own while calm,
 * and fetches drops lying about to your feet. Blowing a goat horn is a howl that marks and slows every beast
 * within {@link #HOWL_RANGE}.</li>
 * </ul>
 */
public final class HunterPack {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** How often (ticks) a hunter's pack is looked after. */
	static final int EVERY = 20;
	/** A fallen wolf is replaced after this long (ticks). */
	static final long REPLACE_TICKS = 2400L;
	/** Wolves in the pack by rank (Apprentice, Journeyman, Master). */
	static final int[] PACK_SIZE = {1, 1, 2};
	/** A pack wolf farther than this from you (blocks), not sitting or hunting, comes to you; recalled, this far is too far. */
	static final double STRAY = 40.0, KEEP_NEAR = 6.0;
	/** A mark lasts this long (ticks); the share of extra damage the hunter and the pack deal to it. */
	static final int MARK_TICKS = 2400;
	static final float MARK_BONUS = 0.25F;
	/** The pack is sent after a quarry no farther than this (blocks). */
	static final double SEND_RANGE = 64.0;
	/** The howl: beasts this near are marked and slowed; for how long (ticks); the horn is ready again after (ticks). */
	static final double HOWL_RANGE = 20.0;
	static final int HOWL_MARK_TICKS = 600, HOWL_SLOW_TICKS = 200, HOWL_SLOW_LEVEL = 1, HOWL_COOLDOWN = 600;
	/** Journeyman: a calm pack wolf mends this much each pass. */
	static final float MEND = 1.0F;
	/** Journeyman: a pack wolf fetches drops lying within this of it and this far from you; not older than this (ticks). */
	static final double FETCH_RANGE = 14.0, FETCH_MIN = 4.0, FETCH_REACH = 2.0;
	static final int FETCH_AGE = 3000;
	/** Entity tag on a wolf of the pack. */
	public static final String TAG = "jaymoskingdoms.pack_wolf";

	// ---- state ---------------------------------------------------------------------------------------------------------

	/** A hunter's pack: the wolves alive (ids), and when each fallen one is to be replaced (game time). */
	public record Pack(List<String> wolves, List<Long> due) {
		public static final Codec<Pack> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.listOf().fieldOf("wolves").forGetter(Pack::wolves),
			Codec.LONG.listOf().fieldOf("due").forGetter(Pack::due)).apply(i, Pack::new));
		public static final Pack NONE = new Pack(List.of(), List.of());
	}

	/** On a player: their pack. Saved, kept through death. */
	public static final AttachmentType<Pack> PACK = AttachmentRegistry.create(JaymosKingdoms.id("hunter_pack"),
		builder -> builder.persistent(Pack.CODEC).copyOnDeath());

	/** A marked beast: whose mark it is and when it ends (game time). */
	record Mark(UUID hunter, long until) {
	}

	private static final Map<UUID, Mark> MARKS = new HashMap<>();
	/** Each hunter's main quarry (the beast marked last), and the hunters whose pack has been sent after it. */
	private static final Map<UUID, UUID> QUARRY = new HashMap<>();
	private static final Set<UUID> SENT = new HashSet<>();

	private HunterPack() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HunterPack::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(HunterPack::died);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MARKS.clear();
			QUARRY.clear();
			SENT.clear();
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !sp.getMainHandItem().is(Items.BONE)
				|| Careers.heldRank(sp, Careers.Job.HUNTER) < 0 || !(entity instanceof LivingEntity target) || !quarry(target)) {
				return InteractionResult.PASS;
			}
			mark(sp, target, MARK_TICKS);
			QUARRY.put(sp.getUUID(), target.getUUID());
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hunter.marked", target.getName()));
			return InteractionResult.SUCCESS_SERVER;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			int rank = Careers.heldRank(sp, Careers.Job.HUNTER);
			if (rank < 0) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getItemInHand(hand);
			if (held.is(Items.BONE) && hand == InteractionHand.MAIN_HAND && !sp.getCooldowns().isOnCooldown(held)) {
				sp.getCooldowns().addCooldown(held, 10);
				if (sp.isShiftKeyDown()) {
					recall(sp);
				} else {
					send(sp);
				}
				return InteractionResult.SUCCESS_SERVER;
			}
			if (held.is(Items.GOAT_HORN) && rank >= Careers.JOURNEYMAN && !sp.getCooldowns().isOnCooldown(held)) {
				// the horn sounds as it always does; the hunters' part is what follows
				howl(sp, held);
			}
			return InteractionResult.PASS;
		});
	}

	// ---- marks ---------------------------------------------------------------------------------------------------------

	/** Whether {@code e} is something a hunter may mark: a living beast that is nobody's, not a pack wolf, nor a wolf that might be tamed. */
	static boolean quarry(LivingEntity e) {
		if (!e.isAlive() || !Careers.beast(e) || e.entityTags().contains(TAG)) {
			return false;
		}
		if (e instanceof TamableAnimal t && (t.isTame() || e instanceof Wolf w && !w.isAngry())) {
			return false;
		}
		return !(e instanceof net.minecraft.world.entity.animal.equine.AbstractHorse h && h.isTamed());
	}

	/** {@code target} glows and takes more from {@code hunter} and the pack, for {@code ticks}. */
	static void mark(ServerPlayer hunter, LivingEntity target, int ticks) {
		if (!(target.level() instanceof ServerLevel level)) {
			return;
		}
		MARKS.put(target.getUUID(), new Mark(hunter.getUUID(), level.getGameTime() + ticks));
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false, false));
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.5F, 1.4F);
	}

	/** Whose mark {@code e} carries now, or null. */
	static @Nullable UUID markOwner(LivingEntity e) {
		Mark m = MARKS.get(e.getUUID());
		return m != null && m.until() > e.level().getGameTime() ? m.hunter() : null;
	}

	/** (from {@code LivingEntityHurtMixin}) A marked beast takes more from its hunter and the hunter's pack. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (MARKS.isEmpty()) {
			return amount;
		}
		UUID hunter = markOwner(target);
		Entity by = source.getEntity();
		if (hunter == null || by == null) {
			return amount;
		}
		boolean theirs = by.getUUID().equals(hunter)
			|| by instanceof Wolf w && w.entityTags().contains(TAG) && w.getOwner() != null && w.getOwner().getUUID().equals(hunter);
		return theirs ? amount * (1.0F + MARK_BONUS) : amount;
	}

	/** The howl: every beast near is marked and slowed. */
	private static void howl(ServerPlayer hunter, ItemStack horn) {
		hunter.getCooldowns().addCooldown(horn, HOWL_COOLDOWN);
		int n = 0;
		for (LivingEntity e : hunter.level().getEntitiesOfClass(LivingEntity.class, hunter.getBoundingBox().inflate(HOWL_RANGE), HunterPack::quarry)) {
			mark(hunter, e, HOWL_MARK_TICKS);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HOWL_SLOW_TICKS, HOWL_SLOW_LEVEL, false, true, false));
			n++;
		}
		for (Wolf w : wolves(hunter)) {
			w.playSound(SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.CLASSIC).adultSounds().growlSound().value(), 1.2F, 0.6F);
		}
		hunter.sendOverlayMessage(Component.translatable(n == 0 ? "message.jaymoskingdoms.hunter.howl.none" : "message.jaymoskingdoms.hunter.howl", n));
	}

	// ---- the pack ------------------------------------------------------------------------------------------------------

	private static List<Wolf> wolves(ServerPlayer hunter) {
		List<Wolf> out = new ArrayList<>();
		ServerLevel overworld = hunter.level().getServer().overworld();
		for (String id : hunter.getAttachedOrCreate(PACK, () -> Pack.NONE).wolves()) {
			try {
				if (overworld.getEntityInAnyDimension(UUID.fromString(id)) instanceof Wolf w && w.isAlive()) {
					out.add(w);
				}
			} catch (IllegalArgumentException e) {
				// a bad id in the save: it is never found, and the pack is topped up from the sizes
			}
		}
		return out;
	}

	private static void send(ServerPlayer hunter) {
		UUID id = QUARRY.get(hunter.getUUID());
		if (id != null && hunter.level().getEntity(id) instanceof LivingEntity q && q.isAlive() && q.distanceTo(hunter) < SEND_RANGE) {
			SENT.add(hunter.getUUID());
			hunter.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hunter.sent", q.getName()));
			for (Wolf w : wolves(hunter)) {
				w.setOrderedToSit(false);
				w.setTarget(q);
			}
		} else {
			hunter.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hunter.noquarry"));
		}
	}

	private static void recall(ServerPlayer hunter) {
		SENT.remove(hunter.getUUID());
		for (Wolf w : wolves(hunter)) {
			w.setTarget(null);
			w.setOrderedToSit(false);
			w.getNavigation().stop();
			if (w.distanceTo(hunter) > KEEP_NEAR * 2 || w.level() != hunter.level()) {
				bring(hunter, w);
			}
		}
		hunter.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hunter.recalled"));
	}

	private static void bring(ServerPlayer hunter, Wolf w) {
		Vec3 at = hunter.position().add(hunter.getLookAngle().multiply(-2, 0, -2));
		w.teleportTo(hunter.level(), at.x, hunter.getY(), at.z, Set.of(), w.getYRot(), w.getXRot(), false);
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		MARKS.values().removeIf(m -> m.until() <= now);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int rank = Careers.heldRank(player, Careers.Job.HUNTER);
			if (rank >= 0 && player.isAlive() && !player.isSpectator()) {
				keep(player, rank, now);
			}
		}
	}

	private static void keep(ServerPlayer player, int rank, long now) {
		Pack pack = player.getAttachedOrCreate(PACK, () -> Pack.NONE);
		List<String> ids = new ArrayList<>(pack.wolves());
		List<Long> due = new ArrayList<>(pack.due());
		boolean changed = false;
		while (ids.size() + due.size() < PACK_SIZE[rank]) {
			due.add(now);
			changed = true;
		}
		for (int i = due.size() - 1; i >= 0; i--) {
			if (due.get(i) <= now && ids.size() < PACK_SIZE[rank]) {
				Wolf w = newWolf(player);
				if (w != null) {
					ids.add(w.getStringUUID());
					due.remove(i);
					changed = true;
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hunter.newwolf"));
				}
			}
		}
		if (changed) {
			player.setAttached(PACK, new Pack(List.copyOf(ids), List.copyOf(due)));
		}
		UUID quarryId = QUARRY.get(player.getUUID());
		LivingEntity quarry = quarryId != null && player.level().getEntity(quarryId) instanceof LivingEntity q && q.isAlive() ? q : null;
		if (quarry == null) {
			SENT.remove(player.getUUID());
		}
		for (Wolf w : wolves(player)) {
			look(player, w, rank, quarry, SENT.contains(player.getUUID()));
		}
	}

	private static @Nullable Wolf newWolf(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos at = Construction.standable(level, player.blockPosition().offset(level.getRandom().nextInt(5) - 2, 0, level.getRandom().nextInt(5) - 2), 3);
		Wolf w = EntityTypes.WOLF.spawn(level, at, EntitySpawnReason.MOB_SUMMONED);
		if (w == null) {
			return null;
		}
		w.tame(player);
		w.setOrderedToSit(false);
		w.setPersistenceRequired();
		w.addTag(TAG);
		level.sendParticles(ParticleTypes.HEART, w.getX(), w.getEyeY() + 0.3, w.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
		return w;
	}

	/** One pack wolf's pass: keep up, follow the scent, mend, fetch. */
	private static void look(ServerPlayer owner, Wolf w, int rank, @Nullable LivingEntity quarry, boolean sent) {
		if ((w.level() != owner.level() || w.distanceTo(owner) > STRAY) && w.getTarget() == null && !w.isInSittingPose()) {
			bring(owner, w);
			return;
		}
		if (sent && quarry != null) {
			if (w.getTarget() != quarry) {
				w.setTarget(quarry);
			}
			if (rank >= Careers.JOURNEYMAN && w.distanceTo(quarry) > 3.0) {
				// by scent: it keeps on the trail with the quarry out of sight
				w.getNavigation().moveTo(quarry, 1.3);
			}
			return;
		}
		if (rank < Careers.JOURNEYMAN || w.getTarget() != null || w.isInSittingPose()) {
			return;
		}
		if (w.getHealth() < w.getMaxHealth()) {
			w.heal(MEND);
		}
		fetch(owner, w);
	}

	private static void fetch(ServerPlayer owner, Wolf w) {
		ItemEntity best = null;
		for (ItemEntity item : owner.level().getEntitiesOfClass(ItemEntity.class, w.getBoundingBox().inflate(FETCH_RANGE),
			i -> i.isAlive() && i.getOwner() == null && i.getAge() < FETCH_AGE && i.distanceTo(owner) > FETCH_MIN && i.distanceTo(owner) < STRAY)) {
			if (best == null || item.distanceTo(w) < best.distanceTo(w)) {
				best = item;
			}
		}
		if (best == null) {
			return;
		}
		if (best.distanceTo(w) <= FETCH_REACH) {
			best.teleportTo(owner.getX(), owner.getY(), owner.getZ());
			best.setPickUpDelay(10);
			owner.level().playSound(null, w.getX(), w.getY(), w.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.4F, 1.2F);
		} else {
			w.getNavigation().moveTo(best, 1.2);
		}
	}

	/** A pack wolf died: the lodge sends another after a while. */
	private static void died(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Wolf w) || !w.entityTags().contains(TAG) || !(w.level() instanceof ServerLevel level)
			|| !(w.getOwner() instanceof ServerPlayer owner)) {
			return;
		}
		Pack pack = owner.getAttachedOrCreate(PACK, () -> Pack.NONE);
		List<String> ids = new ArrayList<>(pack.wolves());
		if (ids.remove(w.getStringUUID())) {
			List<Long> due = new ArrayList<>(pack.due());
			due.add(level.getGameTime() + REPLACE_TICKS);
			owner.setAttached(PACK, new Pack(List.copyOf(ids), List.copyOf(due)));
			owner.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hunter.fell"));
		}
	}
}
