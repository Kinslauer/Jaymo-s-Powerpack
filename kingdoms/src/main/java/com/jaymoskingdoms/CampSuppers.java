package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A band's supper (docs/Pawn-Plan.md row D4, foe side, Andy's note: "enemy bands do the same in their camps, so you can
 * ambush, poison or confront them"). At dusk ({@link #FROM} to {@link #TO}) a camp's band, with a player anywhere
 * within {@link #WATCHED} to see it, gathers in a ring at its fire (lit, or built if the camp has none, {@link #fire}),
 * sits ({@link GroundSeats}) and eats.
 * <ul>
 * <li><b>Ambush</b>: while they eat they're slow to look up: a sneaking player farther than {@link #UNWARY} isn't
 * noticed, and the first blow on one who hadn't seen you lands {@link #AMBUSH} times as hard; the rest scramble up
 * slowed ({@link #SCRAMBLE}).</li>
 * <li><b>Poison</b>: sneak and use something poisonous (a spider eye, a poisonous potato, a pufferfish, a potion of
 * poison) on their fire before or while they eat, unseen by any of them within {@link #CAUGHT}: that supper sickens them
 * all (poison, weakness, the heaves). Seen doing it, and they come for you.</li>
 * <li><b>Confront</b>: walk up openly (not sneaking) within {@link #CONFRONT}: they put down the bowls, stand, and the
 * leader calls you out.</li>
 * </ul>
 * The friend side of this is {@link TableTalk}.
 */
public final class CampSuppers {
	/** Supper runs these day times. */
	static final int FROM = 12000, TO = 13800;
	/** A camp with a player this near (blocks) sits down to supper; its band is everyone of it this near its fire. */
	static final double WATCHED = 96.0, BAND = 32.0;
	/** The ring they sit in (blocks from the fire). */
	static final double RING = 3.0;
	/** While eating a sneaking player farther than this isn't noticed (blocks); openly this near and they stand (blocks). */
	static final double UNWARY = 4.0, CONFRONT = 12.0;
	/** Poisoning seen by any of them this near (blocks); within this of the fire it's their food (blocks). */
	static final double CAUGHT = 8.0, FIRE = 4.0;
	/** The first unseen blow on an eater is this much harder; the others are slowed this long (ticks). */
	static final float AMBUSH = 2.0F;
	static final int SCRAMBLE = 60;
	/** A poisoned supper: poison, weakness, the heaves (ticks). */
	static final int POISON = 600, WEAK = 2400, HEAVES = 200;
	/** Looked over this often (ticks); a bite this often (ticks). */
	static final int EVERY = 10, BITE = 60;
	/** A camp with no fire builds one this near its centre (blocks). */
	static final int BUILD_NEAR = 3;

	/** On the camps' level: camp id → the day its food was poisoned. Saved. */
	static final AttachmentType<Map<String, Long>> POISONED = AttachmentRegistry.create(JaymosKingdoms.id("camp_supper_poisoned"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	private static final class Supper {
		final int camp;
		final BlockPos fire;
		final List<Mob> eaters = new ArrayList<>();
		final long day;
		int ticks;
		boolean sickened;

		Supper(int camp, BlockPos fire, long day) {
			this.camp = camp;
			this.fire = fire;
			this.day = day;
		}
	}

	private static final Map<Integer, Supper> SUPPERS = new HashMap<>();
	private static final Map<Mob, Supper> EATING = new IdentityHashMap<>();
	/** Camps whose supper is over for the day (camp → day). */
	private static final Map<Integer, Long> DONE = new HashMap<>();

	private CampSuppers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CampSuppers::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SUPPERS.clear();
			EATING.clear();
			DONE.clear();
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && !world.isClientSide() && player instanceof ServerPlayer sp && sp.isShiftKeyDown()
				&& poisonous(sp.getMainHandItem()) && poison(sp, hit.getBlockPos())) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	/** {@code m} sits at its band's supper. */
	static boolean eating(LivingEntity m) {
		return m instanceof Mob mob && EATING.containsKey(mob);
	}

	/** (The hurt mixin) the first blow on an eater who hadn't seen it coming lands harder; the band scrambles. */
	public static float dealt(LivingEntity self, DamageSource source, float amount) {
		if (!(self instanceof Mob m) || !(source.getEntity() instanceof Player)) {
			return amount;
		}
		Supper s = EATING.get(m);
		if (s == null) {
			return amount;
		}
		boolean unseen = m.getTarget() == null;
		end(s, true);
		return unseen ? amount * AMBUSH : amount;
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long dayTime = level.getOverworldClockTime() % 24000L, day = level.getOverworldClockTime() / 24000L;
		boolean dusk = dayTime >= FROM && dayTime < TO;
		if (!dusk) {
			for (Supper s : new ArrayList<>(SUPPERS.values())) {
				end(s, false);
			}
			return;
		}
		for (Map.Entry<String, Camps.CampData> e : Camps.all(server).entrySet()) {
			int id;
			try {
				id = Integer.parseInt(e.getKey());
			} catch (NumberFormatException ex) {
				continue;
			}
			BlockPos fire = e.getValue().center();
			if (SUPPERS.containsKey(id) || DONE.getOrDefault(id, -1L) == day || !level.isLoaded(fire) || !watched(level, fire)) {
				continue;
			}
			start(level, id, fire, day);
		}
		for (Supper s : new ArrayList<>(SUPPERS.values())) {
			step(level, s);
		}
	}

	private static boolean watched(ServerLevel level, BlockPos fire) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.blockPosition().closerThan(fire, WATCHED)) {
				return true;
			}
		}
		return false;
	}

	private static void start(ServerLevel level, int id, BlockPos center, long day) {
		String side = "f:" + id;
		BlockPos fire = fire(level, center);
		Supper s = new Supper(id, fire, day);
		for (Mob m : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(fire).inflate(BAND),
			m -> m.isAlive() && !m.isBaby() && !Downed.isDowned(m) && Temperaments.humanoid(m) && Comrades.side(m).equals(side)
				&& Comrades.target(m) == null && !Comrades.busy(m))) {
			s.eaters.add(m);
			EATING.put(m, s);
		}
		if (s.eaters.isEmpty()) {
			DONE.put(id, day);
			return;
		}
		SUPPERS.put(id, s);
	}

	private static void step(ServerLevel level, Supper s) {
		s.ticks += EVERY;
		s.eaters.removeIf(m -> {
			boolean gone = !m.isAlive() || m.isRemoved() || Downed.isDowned(m) || Comrades.busy(m);
			if (gone) {
				EATING.remove(m);
				GroundSeats.standUp(m);
			}
			return gone;
		});
		if (s.eaters.isEmpty()) {
			end(s, false);
			return;
		}
		// someone walking up openly is called out
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.isShiftKeyDown() || p.isInvisible() || !p.blockPosition().closerThan(s.fire, CONFRONT)) {
				continue;
			}
			Mob caller = s.eaters.get(0);
			for (Mob m : s.eaters) {
				if (BandLeaders.isLeader(m)) {
					caller = m;
					break;
				}
			}
			if (caller.hasLineOfSight(p)) {
				end(s, false);
				Callouts.say(caller, "supper.confront", p, true, p.getDisplayName());
				for (Mob m : s.eaters) {
					m.getLookControl().setLookAt(p, 30.0F, 30.0F);
				}
				Comrades.aim(caller, p);
				return;
			}
		}
		boolean poisoned = level.getAttachedOrElse(POISONED, Map.of()).getOrDefault(Integer.toString(s.camp), -1L) == s.day;
		for (int i = 0; i < s.eaters.size(); i++) {
			Mob m = s.eaters.get(i);
			// unwary: one creeping up isn't seen till close
			if (m.getTarget() instanceof Player p && p.isShiftKeyDown() && m.distanceTo(p) > UNWARY && m.hurtTime == 0) {
				m.setTarget(null);
			}
			if (m.getTarget() != null) {
				GroundSeats.standUp(m);
				continue;
			}
			double a = Math.PI * 2.0 * i / s.eaters.size();
			Vec3 seat = Vec3.atBottomCenterOf(s.fire).add(Math.cos(a) * RING, 0.0, Math.sin(a) * RING);
			if (!GroundSeats.sitting(m) && m.position().distanceToSqr(seat) > 1.5 * 1.5) {
				if (m.getNavigation().isDone()) {
					m.getNavigation().moveTo(seat.x, seat.y, seat.z, 0.5);
				}
				continue;
			}
			if (!GroundSeats.sitting(m) && !GroundSeats.sitDown(m, Vec3.atBottomCenterOf(s.fire))) {
				// no room to sit: they crouch by it
				m.getNavigation().stop();
				m.setShiftKeyDown(true);
			}
			m.getLookControl().setLookAt(s.fire.getX() + 0.5, s.fire.getY() + 0.5, s.fire.getZ() + 0.5);
			if ((s.ticks + i * 7) % BITE < EVERY) {
				m.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				level.playSound(null, m.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.HOSTILE, 0.5F, 0.9F + m.getRandom().nextFloat() * 0.2F);
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.COOKED_MUTTON), m.getX(), m.getEyeY() - 0.1, m.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
			}
		}
		if (s.ticks % 40 == 0 && !level.getBlockState(s.fire).is(net.minecraft.tags.BlockTags.CAMPFIRES)) {
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, s.fire.getX() + 0.5, s.fire.getY() + 0.8, s.fire.getZ() + 0.5, 1, 0.1, 0.1, 0.1, 0.01);
		}
		if (poisoned && !s.sickened && s.ticks >= BITE * 2) {
			s.sickened = true;
			for (Mob m : s.eaters) {
				m.addEffect(new MobEffectInstance(MobEffects.POISON, POISON, 1));
				m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAK, 1));
				m.addEffect(new MobEffectInstance(MobEffects.NAUSEA, HEAVES, 0));
			}
			Callouts.say(s.eaters.get(level.getRandom().nextInt(s.eaters.size())), "supper.sick", null, true);
		}
	}

	/** Supper's over: they stand ({@code scramble}: in a hurry, slowed, and someone says so). */
	private static void end(Supper s, boolean scramble) {
		SUPPERS.remove(s.camp);
		DONE.put(s.camp, s.day);
		for (Mob m : s.eaters) {
			EATING.remove(m);
			m.setShiftKeyDown(false);
			GroundSeats.standUp(m);
			if (scramble) {
				m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SCRAMBLE, 1));
			}
		}
		if (scramble && !s.eaters.isEmpty()) {
			Callouts.say(s.eaters.get(0), "supper.scramble", null, true);
		}
	}

	/**
	 * The camp's fire: a lit one near its centre, or, if it has none, one they build there for supper (a campfire on clear
	 * ground within {@link #BUILD_NEAR}, and it stays: a camp has a fire from then on). The centre itself if there's no room.
	 */
	private static BlockPos fire(ServerLevel level, BlockPos center) {
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-(int)FIRE, -2, -(int)FIRE), center.offset((int)FIRE, 2, (int)FIRE))) {
			if (level.getBlockState(p).is(net.minecraft.tags.BlockTags.CAMPFIRES)) {
				BlockState state = level.getBlockState(p);
				if (state.hasProperty(CampfireBlock.LIT) && !state.getValue(CampfireBlock.LIT)) {
					level.setBlock(p, state.setValue(CampfireBlock.LIT, true), Block.UPDATE_ALL);
				}
				return p.immutable();
			}
		}
		for (int r = 0; r <= BUILD_NEAR; r++) {
			for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 2, r))) {
				if (Math.max(Math.abs(p.getX() - center.getX()), Math.abs(p.getZ() - center.getZ())) != r) {
					continue;
				}
				BlockState here = level.getBlockState(p), below = level.getBlockState(p.below());
				if (here.isAir() && level.getBlockState(p.above()).isAir() && below.isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
					&& !below.is(net.minecraft.tags.BlockTags.LOGS) && !below.is(net.minecraft.tags.BlockTags.PLANKS)) {
					level.setBlock(p, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
					level.playSound(null, p, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.0F);
					return p.immutable();
				}
			}
		}
		return center;
	}

	// ---- poisoning -----------------------------------------------------------------------------------------------------

	static boolean poisonous(ItemStack stack) {
		if (stack.is(Items.SPIDER_EYE) || stack.is(Items.FERMENTED_SPIDER_EYE) || stack.is(Items.POISONOUS_POTATO) || stack.is(Items.PUFFERFISH)) {
			return true;
		}
		PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
		if (potion != null) {
			for (MobEffectInstance e : potion.getAllEffects()) {
				if (e.getEffect().value() == MobEffects.POISON.value()) {
					return true;
				}
			}
		}
		return false;
	}

	/** {@code player} puts poison in a camp's food at {@code at}. True if it was a camp's fire. */
	private static boolean poison(ServerPlayer player, BlockPos at) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = Factions.level(server);
		if (player.level() != level) {
			return false;
		}
		for (Map.Entry<String, Camps.CampData> e : Camps.all(server).entrySet()) {
			if (!at.closerThan(e.getValue().center(), FIRE)) {
				continue;
			}
			String side = "f:" + e.getKey();
			for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(CAUGHT),
				m -> m.isAlive() && !Downed.isDowned(m) && Comrades.side(m).equals(side) && m.hasLineOfSight(player))) {
				// seen at it
				Callouts.say(m, "supper.caught", player, true, player.getDisplayName());
				Comrades.aim(m, player);
				return true;
			}
			ItemStack held = player.getMainHandItem();
			if (!player.isCreative()) {
				held.shrink(1);
				if (held.getItem() instanceof net.minecraft.world.item.PotionItem && !player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
					player.drop(new ItemStack(Items.GLASS_BOTTLE), false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
			Map<String, Long> all = new HashMap<>(level.getAttachedOrElse(POISONED, Map.of()));
			all.put(e.getKey(), level.getOverworldClockTime() / 24000L + (level.getOverworldClockTime() % 24000L >= TO ? 1 : 0));
			level.setAttached(POISONED, all);
			level.sendParticles(ParticleTypes.WITCH, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
			level.playSound(null, at, SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.4F, 0.8F);
			return true;
		}
		return false;
	}
}
