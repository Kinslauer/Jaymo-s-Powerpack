package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Reaper's soul arts (Andy's Awakening pick "Reaper", 2026-11-17: "Add this to the death spirit"). Whoever carries the
 * Reaper ({@link Reaper#has}) banks a <b>soul</b> for every kill, and the souls are a tier, spent only on the scythe. A keeper with
 * none is as they were before ({@link #HEARTS_NEW} hearts, {@link #DAMAGE_NEW} of every blow); one with {@link #SOULS_FULL} is death itself
 * ({@link #HEARTS_FULL} hearts, {@link #DAMAGE_FULL} times every blow), and everything between slides. The Toll a keeper has
 * already paid is counted in the first time ({@link #SEED_PER_TOLL} souls a point).
 * <p>
 * The arts are on the Soul key ({@link ArtKeys}):
 * <ul>
 * <li><b>Soul wisps</b> (tap): {@link #WISPS} wisps leave the Reaper and hunt the foe you look at (or the nearest), through
 * walls, each a small hurt.</li>
 * <li><b>Brand</b> (sneak + tap at a foe): the branded takes more from its keeper; when it dies the brand jumps to the nearest
 * foe, and its soul is worth more.</li>
 * <li><b>Soul chain</b> (sprint + tap at a foe): a chain between you; while it holds, the foe is drained each second and you
 * are healed.</li>
 * <li><b>Drift</b> (hold): while the key is held you drift through walls ({@code SoulDriftMixin}); the floor still holds.</li>
 * <li><b>The Soul Scythe</b> (sneak + hold, empty hand, {@link #SCYTHE_SOULS} souls spent): the Reaper hands you its scythe.
 * Its blows reap everything beside the one struck, and a kill with it banks twice.</li>
 * </ul>
 */
public final class SoulArts {
	// ---- tuning ----
	/** Souls for a kill, for a great foe (this much health or more), and with the scythe in hand (a factor). */
	static final int SOUL_KILL = 1, SOUL_GREAT = 10, SCYTHE_SOULS_TIMES = 2;
	static final float GREAT_HEALTH = 100.0F;
	/** The tier: souls for the full tier, and the hearts and the share of every blow at none and at full. */
	static final int SOULS_FULL = 1000;
	static final double HEARTS_NEW = 10.0, HEARTS_FULL = 20.0;
	static final float DAMAGE_NEW = 1.0F, DAMAGE_FULL = 6.0F;
	/** Souls a keeper starts with for each point of Toll already paid. */
	static final int SEED_PER_TOLL = 5;
	/** Wisps: how many, reach to pick a foe, speed (blocks a tick), harm each, how long they hunt (ticks), cooldown. */
	static final int WISPS = 3, WISP_LIFE = 100, WISP_COOLDOWN = 120;
	static final double WISP_REACH = 24.0, WISP_SPEED = 0.6, WISP_RETARGET = 8.0;
	static final float WISP_DAMAGE = 3.0F;
	/** Brand: reach, how long it lasts (ticks), the share more the branded takes, how far it jumps, the souls it adds, cooldown. */
	static final double BRAND_REACH = 20.0, BRAND_JUMP = 10.0;
	static final int BRAND_TICKS = 600, BRAND_SOULS = 1, BRAND_COOLDOWN = 200;
	static final float BRAND_TAKEN = 1.25F;
	/** Chain: reach to throw it, the distance that breaks it, how long it holds (ticks), harm and healing each second, cooldown. */
	static final double CHAIN_REACH = 12.0, CHAIN_BREAK = 16.0;
	static final int CHAIN_TICKS = 100, CHAIN_COOLDOWN = 240;
	static final float CHAIN_DAMAGE = 2.0F, CHAIN_HEAL = 1.0F;
	/** Drift: the longest it holds (ticks), the wait after, how far a clear spot is looked for at its end, what ending in stone costs. */
	static final int DRIFT_TICKS = 80, DRIFT_COOLDOWN = 200, DRIFT_OUT = 3;
	static final float DRIFT_DAMAGE = 4.0F;
	/** The scythe: souls its forging spends, the wait between forgings (ticks), its reaping reach and the share of a blow the others take. */
	static final int SCYTHE_SOULS = 500, SCYTHE_COOLDOWN = 24000;
	static final double SCYTHE_REACH = 3.0;
	static final float SCYTHE_REAP = 0.5F;
	static final String SCYTHE_TAG = "jaymoskingdoms_soul_scythe", SCYTHE_OF = "jaymoskingdoms_soul_scythe_of", SCYTHE_NTH = "jaymoskingdoms_soul_scythe_nth";
	static final Identifier WAR_SCYTHE = Identifier.fromNamespaceAndPath("jaymospowers", "netherite_war_scythe");
	static final int PALE = 0x9FE8E0;

	/** The souls a keeper has banked. Saved, kept through death. */
	static final AttachmentType<Integer> SOULS = AttachmentRegistry.create(JaymosKingdoms.id("reaper_souls"),
		b -> b.persistent(Codec.INT).copyOnDeath());
	/**
	 * How many scythes a keeper has forged. Saved, kept through death. Only the last one forged is theirs: an older one, or
	 * another keeper's, comes apart in the hand, so the forging can't be turned into a pile of free scythes.
	 */
	static final AttachmentType<Integer> FORGED = AttachmentRegistry.create(JaymosKingdoms.id("reaper_scythes"),
		b -> b.persistent(Codec.INT).copyOnDeath());
	/** On a keeper while drifting, for both sides' collision. Not saved. */
	public static final AttachmentType<Boolean> DRIFTING = AttachmentRegistry.create(JaymosKingdoms.id("reaper_drifting"),
		b -> b.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private static final Identifier HEALTH_ID = JaymosKingdoms.id("reaper_souls_health");

	private static final class Wisp {
		final ServerLevel level;
		final ServerPlayer owner;
		LivingEntity target;
		Vec3 at;
		int life = WISP_LIFE;

		Wisp(ServerLevel level, ServerPlayer owner, LivingEntity target, Vec3 at) {
			this.level = level;
			this.owner = owner;
			this.target = target;
			this.at = at;
		}
	}

	private record Brand(UUID by, long until) {
	}

	private record Chain(ServerPlayer owner, LivingEntity target, long until) {
	}

	private static final class Drift {
		int ticks;
		Vec3 free;
	}

	private static final List<Wisp> WISPS_OUT = new ArrayList<>();
	private static final Map<LivingEntity, Brand> BRANDS = new WeakHashMap<>();
	private static final List<Chain> CHAINS = new ArrayList<>();
	private static final Map<UUID, Drift> DRIFTS = new HashMap<>();
	/** Set while the drift itself asks the world "is this spot clear?", so it gets the true answer. */
	private static final ThreadLocal<Boolean> ASKING = ThreadLocal.withInitial(() -> false);
	private static boolean reaping;

	private SoulArts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SoulArts::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(SoulArts::died);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			// the scythe reaps: whoever stands beside the one struck takes a share
			if (reaping || taken <= 0.0F || blocked || !(source.getEntity() instanceof ServerPlayer keeper) || source.getDirectEntity() != keeper
				|| !source.is(DamageTypes.PLAYER_ATTACK) || !mine(keeper, keeper.getMainHandItem()) || !(victim.level() instanceof ServerLevel level)) {
				return;
			}
			reaping = true;
			try {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(SCYTHE_REACH), e -> e != victim && ArtKeys.foe(keeper, e))) {
					e.hurtServer(level, level.damageSources().indirectMagic(keeper, keeper), taken * SCYTHE_REAP);
				}
			} finally {
				reaping = false;
			}
			Fx.ring(level, victim.position().add(0.0, 0.3, 0.0), (float) SCYTHE_REACH, Reaper.RED);
		});
		// drifting, stone does not crush
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof Player player && player.getAttached(DRIFTING) != null && (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING))));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> endDrift(handler.player, false));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WISPS_OUT.clear();
			BRANDS.clear();
			CHAINS.clear();
			DRIFTS.clear();
		});
	}

	// ---- the souls ------------------------------------------------------------------------------------------------------------

	/** The souls {@code player} has banked (0 without the Reaper). */
	public static int souls(Player player) {
		return Reaper.has(player) ? player.getAttachedOrElse(SOULS, 0) : 0;
	}

	/** How far up the tier {@code player} is, 0 to 1. */
	static float tier(Player player) {
		return Mth.clamp(souls(player) / (float) SOULS_FULL, 0.0F, 1.0F);
	}

	private static void bank(ServerPlayer keeper, int souls) {
		int now = Math.min(SOULS_FULL, keeper.getAttachedOrElse(SOULS, 0) + souls);
		keeper.setAttached(SOULS, now);
		keeper.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.soul.banked", now).withStyle(s -> s.withColor(Reaper.RED)));
	}

	private static void died(LivingEntity dead, DamageSource source) {
		if (!(dead.level() instanceof ServerLevel level)) {
			return;
		}
		Brand brand = BRANDS.remove(dead);
		ServerPlayer brander = brand == null ? null : level.getServer().getPlayerList().getPlayer(brand.by);
		// (only the brander's own kill: a branded mob dying of anything else, in a grinder say, pays nothing and passes nothing on)
		if (brander != null && source.getEntity() == brander && level.getGameTime() < brand.until) {
			// the brand jumps to the nearest foe, and the branded soul is worth more
			if (Reaper.has(brander)) {
				bank(brander, BRAND_SOULS);
			}
			LivingEntity next = null;
			double best = BRAND_JUMP * BRAND_JUMP;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(BRAND_JUMP), e -> e != dead && ArtKeys.foe(brander, e))) {
				double d = e.distanceToSqr(dead);
				if (d < best && !BRANDS.containsKey(e)) {
					best = d;
					next = e;
				}
			}
			if (next != null) {
				brand(level, brander, next);
				Fx.beam(level, dead.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), 0.06F, Reaper.RED, 12);
			}
		}
		if (source.getEntity() instanceof ServerPlayer keeper && keeper != dead && Reaper.has(keeper)) {
			int souls = dead.getMaxHealth() >= GREAT_HEALTH ? SOUL_GREAT : SOUL_KILL;
			bank(keeper, mine(keeper, keeper.getMainHandItem()) ? souls * SCYTHE_SOULS_TIMES : souls);
			level.sendParticles(ParticleTypes.SOUL, dead.getX(), dead.getY(0.5), dead.getZ(), 4, 0.2, 0.3, 0.2, 0.03);
		}
	}

	/** What the souls do to a blow ({@code LivingEntityHurtMixin}): a keeper's blows by their tier, and more on the one they branded. */
	public static float dealt(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0.0F || !(source.getEntity() instanceof Player keeper) || keeper == victim || !Reaper.has(keeper)) {
			return amount;
		}
		// (the scythe's reaping is a share of a blow the tier has already weighed)
		if (!reaping) {
			amount *= Mth.lerp(tier(keeper), DAMAGE_NEW, DAMAGE_FULL);
		}
		Brand brand = BRANDS.get(victim);
		if (brand != null && brand.by.equals(keeper.getUUID()) && victim.level().getGameTime() < brand.until) {
			amount *= BRAND_TAKEN;
		}
		return amount;
	}

	// ---- the key --------------------------------------------------------------------------------------------------------------

	static void key(ServerPlayer player, ArtKeys.Gesture gesture) {
		if (!Reaper.has(player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		switch (gesture) {
			case TAP -> wisps(player, level);
			case SNEAK -> {
				LivingEntity foe = ArtKeys.sight(player, BRAND_REACH, e -> !ArtKeys.mine(player, e));
				if (foe == null) {
					ArtKeys.refuse(player, "no_foe");
				} else if (!ArtKeys.cooling(player, "soul_brand", BRAND_COOLDOWN)) {
					brand(level, player, foe);
				}
			}
			case SPRINT -> {
				LivingEntity foe = ArtKeys.sight(player, CHAIN_REACH, e -> !ArtKeys.mine(player, e));
				if (foe == null) {
					ArtKeys.refuse(player, "no_foe");
				} else if (!ArtKeys.cooling(player, "soul_chain", CHAIN_COOLDOWN)) {
					CHAINS.removeIf(c -> c.owner == player);
					CHAINS.add(new Chain(player, foe, level.getGameTime() + CHAIN_TICKS));
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
				}
			}
			case HOLD -> endDrift(player, false);
			case SNEAK_HOLD -> forge(player, level);
		}
	}

	private static void wisps(ServerPlayer player, ServerLevel level) {
		LivingEntity foe = ArtKeys.sight(player, WISP_REACH, e -> !ArtKeys.mine(player, e));
		if (foe == null) {
			foe = nearest(level, player, player.position(), WISP_REACH);
		}
		if (foe == null) {
			ArtKeys.refuse(player, "no_foe");
			return;
		}
		if (ArtKeys.cooling(player, "soul_wisps", WISP_COOLDOWN)) {
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 side = new Vec3(-look.z, 0.0, look.x);
		for (int i = 0; i < WISPS; i++) {
			WISPS_OUT.add(new Wisp(level, player, foe, player.getEyePosition().add(side.scale((i - (WISPS - 1) / 2.0) * 0.8)).add(0.0, 0.4, 0.0)));
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 1.2F);
	}

	private static @Nullable LivingEntity nearest(ServerLevel level, ServerPlayer player, Vec3 from, double reach) {
		LivingEntity best = null;
		double bestD = reach * reach;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(from, from).inflate(reach), e -> ArtKeys.foe(player, e))) {
			double d = e.distanceToSqr(from);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	private static void brand(ServerLevel level, ServerPlayer by, LivingEntity foe) {
		BRANDS.put(foe, new Brand(by.getUUID(), level.getGameTime() + BRAND_TICKS));
		Fx.sigil(level, foe.position().add(0.0, 0.05, 0.0), foe.getBbWidth() + 0.6F, Reaper.RED, 40);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.5F, 0.5F);
	}

	// ---- the scythe -----------------------------------------------------------------------------------------------------------

	/** Whether {@code stack} is the Soul Scythe. */
	public static boolean scythe(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(SCYTHE_TAG);
	}

	/** Whether {@code stack} is the scythe {@code keeper} forged last (the only one that reaps). */
	static boolean mine(Player keeper, ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !Reaper.has(keeper)) {
			return false;
		}
		net.minecraft.nbt.CompoundTag tag = data.copyTag();
		return tag.contains(SCYTHE_TAG) && keeper.getUUID().toString().equals(tag.getStringOr(SCYTHE_OF, ""))
			&& tag.getIntOr(SCYTHE_NTH, 0) == keeper.getAttachedOrElse(FORGED, 0);
	}

	/** A scythe in hand that isn't its holder's last forging comes apart. */
	private static void unmake(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (scythe(held) && !mine(player, held)) {
				player.setItemInHand(hand, ItemStack.EMPTY);
				if (player.level() instanceof ServerLevel level) {
					level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(1.0), player.getZ(), 16, 0.3, 0.5, 0.3, 0.04);
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
				}
			}
		}
	}

	private static void forge(ServerPlayer player, ServerLevel level) {
		int souls = souls(player);
		if (souls < SCYTHE_SOULS) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.soul.scythe_needs", souls, SCYTHE_SOULS).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!player.getMainHandItem().isEmpty()) {
			ArtKeys.refuse(player, "empty_hand");
			return;
		}
		if (player.getInventory().contains(SoulArts::scythe)) {
			ArtKeys.refuse(player, "soul_scythe_held");
			return;
		}
		if (ArtKeys.cooling(player, "soul_scythe", SCYTHE_COOLDOWN)) {
			return;
		}
		// the scythe is made of the souls: they leave the keeper's tier
		player.setAttached(SOULS, souls - SCYTHE_SOULS);
		// Jaymo's Powers' war scythe where it is in the pack (its art), else a hoe
		Item item = BuiltInRegistries.ITEM.containsKey(WAR_SCYTHE) ? BuiltInRegistries.ITEM.getValue(WAR_SCYTHE) : Items.NETHERITE_HOE;
		ItemStack scythe = new ItemStack(item);
		// the newest is the only one: any forged before stops being theirs
		int nth = player.getAttachedOrElse(FORGED, 0) + 1;
		player.setAttached(FORGED, nth);
		CustomData.update(DataComponents.CUSTOM_DATA, scythe, tag -> {
			tag.putBoolean(SCYTHE_TAG, true);
			tag.putString(SCYTHE_OF, player.getUUID().toString());
			tag.putInt(SCYTHE_NTH, nth);
		});
		scythe.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.soul_scythe").withStyle(s -> s.withColor(Reaper.RED).withItalic(false)));
		scythe.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms.soul_scythe.desc").withStyle(ChatFormatting.GRAY))));
		scythe.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
		Fx.blast(level, player.position().add(0.0, 1.0, 0.0), 1.6F, Reaper.RED);
		level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(1.0), player.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4F, 1.6F);
	}

	// ---- the drift ------------------------------------------------------------------------------------------------------------

	/** Whether {@code player} is drifting and the collision should let them through (either side). */
	public static boolean drifting(Player player) {
		return !ASKING.get() && player.getAttached(DRIFTING) != null;
	}

	private static boolean clear(ServerPlayer player, Vec3 at) {
		ASKING.set(true);
		try {
			return player.level().noCollision(player, player.getBoundingBox().move(at.subtract(player.position())));
		} finally {
			ASKING.set(false);
		}
	}

	private static void endDrift(ServerPlayer player, boolean spent) {
		Drift drift = DRIFTS.remove(player.getUUID());
		if (drift == null) {
			return;
		}
		player.removeAttached(DRIFTING);
		ArtKeys.cooling(player, "soul_drift", DRIFT_COOLDOWN);
		if (player.level() instanceof ServerLevel level && !clear(player, player.position())) {
			Vec3 out = drift.free;
			double best = Double.MAX_VALUE;
			for (int dx = -DRIFT_OUT; dx <= DRIFT_OUT; dx++) {
				for (int dy = -DRIFT_OUT; dy <= DRIFT_OUT; dy++) {
					for (int dz = -DRIFT_OUT; dz <= DRIFT_OUT; dz++) {
						Vec3 spot = player.position().add(dx, dy, dz);
						double d = dx * dx + dy * dy + dz * dz;
						// never out below the world
						if (d < best && spot.y >= level.getMinY() && clear(player, spot)) {
							best = d;
							out = spot;
						}
					}
				}
			}
			player.teleportTo(level, out.x, out.y, out.z, Set.of(), player.getYRot(), player.getXRot(), false);
			if (spent) {
				player.hurtServer(level, level.damageSources().magic(), DRIFT_DAMAGE);
			}
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickKeeper(player);
		}
		for (Iterator<Wisp> it = WISPS_OUT.iterator(); it.hasNext();) {
			Wisp w = it.next();
			if (--w.life <= 0 || !w.owner.isAlive() || w.owner.level() != w.level) {
				it.remove();
				continue;
			}
			if (!w.target.isAlive() || w.target.isRemoved() || w.target.level() != w.level) {
				LivingEntity next = nearest(w.level, w.owner, w.at, WISP_RETARGET);
				if (next == null) {
					it.remove();
					continue;
				}
				w.target = next;
			}
			Vec3 to = w.target.getBoundingBox().getCenter().subtract(w.at);
			if (to.length() <= WISP_SPEED + 0.4) {
				it.remove();
				w.target.setInvulnerableTime(0);
				w.target.hurtServer(w.level, w.level.damageSources().indirectMagic(w.owner, w.owner), WISP_DAMAGE);
				Fx.blast(w.level, w.target.getBoundingBox().getCenter(), 0.5F, PALE);
				continue;
			}
			w.at = w.at.add(to.normalize().scale(WISP_SPEED));
			w.level.sendParticles(ParticleTypes.SOUL, w.at.x, w.at.y, w.at.z, 1, 0.0, 0.0, 0.0, 0.0);
			w.level.sendParticles(Fx.mote(PALE, 1.0F), w.at.x, w.at.y, w.at.z, 2, 0.05, 0.05, 0.05, 0.0);
		}
		for (Iterator<Chain> it = CHAINS.iterator(); it.hasNext();) {
			Chain c = it.next();
			if (!(c.owner.level() instanceof ServerLevel level) || !c.owner.isAlive() || !c.target.isAlive() || c.target.level() != level
				|| level.getGameTime() >= c.until || c.owner.distanceToSqr(c.target) > CHAIN_BREAK * CHAIN_BREAK) {
				it.remove();
				continue;
			}
			// a steady beat of its own
			long beat = level.getGameTime() + c.owner.getId();
			if (beat % 10L == 0L) {
				Fx.beam(level, c.owner.position().add(0.0, 1.0, 0.0), c.target.getBoundingBox().getCenter(), 0.07F, Reaper.RED, 11);
			}
			if (beat % 20L == 0L) {
				c.target.setInvulnerableTime(0);
				if (c.target.hurtServer(level, level.damageSources().indirectMagic(c.owner, c.owner), CHAIN_DAMAGE)) {
					c.owner.heal(CHAIN_HEAL);
				}
			}
		}
	}

	private static void tickKeeper(ServerPlayer player) {
		if (player.tickCount % 20 == 0) {
			unmake(player);
		}
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (!Reaper.has(player)) {
			if (health != null && health.hasModifier(HEALTH_ID)) {
				health.removeModifier(HEALTH_ID);
			}
			endDrift(player, false);
			return;
		}
		if (player.getAttached(SOULS) == null) {
			// the Toll already paid counts
			Reaper.State state = player.getAttached(Reaper.REAPER);
			player.setAttached(SOULS, Math.min(SOULS_FULL, (state == null ? 0 : state.toll()) * SEED_PER_TOLL));
		}
		if (health != null && player.tickCount % 20 == 0) {
			double hearts = Mth.lerp(tier(player), HEARTS_NEW, HEARTS_FULL);
			double amount = hearts * 2.0 - 20.0;
			AttributeModifier old = health.getModifier(HEALTH_ID);
			if (old == null || Math.abs(old.amount() - amount) > 0.01) {
				// (saved with the keeper: a transient one would read their saved health against 10 hearts at every login)
				health.addOrReplacePermanentModifier(new AttributeModifier(HEALTH_ID, amount, AttributeModifier.Operation.ADD_VALUE));
				if (player.getHealth() > player.getMaxHealth()) {
					player.setHealth(player.getMaxHealth());
				}
			}
		}
		// the drift: for as long as the Soul key is held (not sneaking: that is the forging)
		Drift drift = DRIFTS.get(player.getUUID());
		long held = ArtKeys.heldFor(player, ArtKeys.SOUL);
		if (drift == null) {
			if (held == ArtKeys.HOLD_TICKS && !player.isShiftKeyDown() && player.isAlive() && !player.isPassenger()
				&& !ArtKeys.cooling(player, "soul_drift", 0)) {
				drift = new Drift();
				drift.free = player.position();
				DRIFTS.put(player.getUUID(), drift);
				player.setAttached(DRIFTING, true);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
			}
			return;
		}
		if (held < 0L || !player.isAlive()) {
			endDrift(player, false);
			return;
		}
		if (clear(player, player.position())) {
			drift.free = player.position();
		}
		if (player.level() instanceof ServerLevel level && player.tickCount % 3 == 0) {
			level.sendParticles(Fx.miasma(Reaper.SMOKE, 1.4F), player.getX(), player.getY(0.5), player.getZ(), 3, 0.3, 0.6, 0.3, 0.01);
		}
		if (++drift.ticks >= DRIFT_TICKS) {
			endDrift(player, true);
		}
	}
}
