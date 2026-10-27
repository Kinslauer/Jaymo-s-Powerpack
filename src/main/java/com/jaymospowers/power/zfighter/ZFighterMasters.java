package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

/**
 * Masters (needs Jaymo's Kingdoms). Every kingdom has one: its eldest villager (an elder of 8 days or more where folk
 * age), who teaches a Z Fighter the kingdom thinks well of ({@link #GOOD_REPUTATION}) and who isn't on the Rogue's
 * path. Every barbarian camp has a dark master, who teaches Rogues and fighters beaten often enough to be hungry
 * ({@link #DARK_DEFEATS}), and whose lessons draw blood. A master is marked with a tag and a faint glow of motes.
 * <p>
 * Sneak and right-click one bare-handed, once a day: he teaches a technique you haven't learned (good masters favour guarding
 * ones, dark masters killing ones, never the Spirit Bomb) and, once you know them all, a step in your form instead
 * (mastery of your highest form; before Super, a share of the hardship that opens it). Every lesson also pays TP.
 */
final class ZFighterMasters {
	static final String TAG = "jaymospowers_master";
	static final String DARK_TAG = "jaymospowers_dark_master";
	static final int GOOD_REPUTATION = 100;
	static final int DARK_DEFEATS = 3;
	static final long ELDER_DAYS = 8L;
	static final int LESSON_TP = 75;
	static final int LESSON_MASTERY = 1000;
	static final int DARK_HARDSHIP = 6;
	static final float DARK_PRICE = 6.0F;
	static final int SCAN = 200;
	static final double NEAR = 96.0;
	static final double CAMP = 48.0;
	static final long DAY = 24000L;
	/** Good masters teach these first, then the rest; dark masters the other order. */
	private static final int[] GOOD_ORDER = {ZFighterTraining.KI_BARRIER, ZFighterTraining.SOLAR_FLASH, ZFighterTraining.HOMING_VOLLEY,
		ZFighterTraining.KI_DISC, ZFighterTraining.KI_BOMB};
	private static final int[] DARK_ORDER = {ZFighterTraining.KI_DISC, ZFighterTraining.KI_BOMB, ZFighterTraining.HOMING_VOLLEY,
		ZFighterTraining.SOLAR_FLASH, ZFighterTraining.KI_BARRIER};

	/** The day (game time / a day) of his last lesson. */
	public static final AttachmentType<Long> LESSON = AttachmentRegistry.create(JaymosPowers.id("zfighter_lesson"),
		builder -> builder.persistent(Codec.LONG));

	private ZFighterMasters() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterMasters::tick);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			boolean dark = entity.entityTags().contains(DARK_TAG);
			if (!dark && !entity.entityTags().contains(TAG) || hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()
				|| !player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && player instanceof ServerPlayer server) {
				teach(server, entity, dark);
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		if (now % 20 == 0) {
			glow(level);
		}
		if (now % SCAN != 0 || !ZFighterKingdoms.present()) {
			return;
		}
		for (ZFighterKingdoms.Settlement s : ZFighterKingdoms.settlements(server)) {
			boolean kingdom = "KINGDOM".equals(s.type());
			boolean camp = "BARBARIAN".equals(s.type());
			if (!kingdom && !camp || level.getNearestPlayer(s.seat().getX(), s.seat().getY(), s.seat().getZ(), NEAR, false) == null) {
				continue;
			}
			String tag = kingdom ? TAG : DARK_TAG;
			AABB around = new AABB(s.seat()).inflate(CAMP);
			if (!level.getEntitiesOfClass(LivingEntity.class, around, e -> e.isAlive() && e.entityTags().contains(tag)).isEmpty()) {
				continue;
			}
			LivingEntity pick = kingdom ? elder(level, s) : camper(level, s, around);
			if (pick != null) {
				pick.addTag(tag);
				if (pick instanceof Mob mob) {
					mob.setPersistenceRequired();
				}
			}
		}
	}

	/** A kingdom's eldest grown villager (8 days or more where folk age; any grown one where they don't). */
	private static LivingEntity elder(ServerLevel level, ZFighterKingdoms.Settlement s) {
		List<Villager> people = ZFighterKingdoms.people(level, s).stream().filter(v -> !v.isBaby()).toList();
		boolean ageing = people.stream().anyMatch(v -> ZFighterKingdoms.grownDays(level, v) >= 0);
		if (!ageing) {
			return people.isEmpty() ? null : people.get(level.getRandom().nextInt(people.size()));
		}
		return people.stream().filter(v -> ZFighterKingdoms.grownDays(level, v) >= ELDER_DAYS)
			.max(Comparator.comparingLong(v -> ZFighterKingdoms.grownDays(level, v))).orElse(null);
	}

	/** One of a barbarian camp's own (never its chief, who sits nearest the seat). */
	private static LivingEntity camper(ServerLevel level, ZFighterKingdoms.Settlement s, AABB around) {
		List<LivingEntity> folk = level.getEntitiesOfClass(LivingEntity.class, around,
			e -> e.isAlive() && !e.isBaby() && ZFighterKingdoms.factionOf(e) == s.id());
		if (folk.size() < 2) {
			return null;
		}
		LivingEntity chief = folk.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(s.seat())))).orElse(null);
		return folk.stream().filter(e -> e != chief).max(Comparator.comparingDouble(e -> e.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(s.seat())))).orElse(null);
	}

	/** Masters near a player shimmer: pale motes for a master, soul fire for a dark one. */
	private static void glow(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (!Powers.has(player, ZFighterPower.INSTANCE)) {
				continue;
			}
			for (LivingEntity master : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(24.0),
				e -> e.entityTags().contains(TAG) || e.entityTags().contains(DARK_TAG))) {
				boolean dark = master.entityTags().contains(DARK_TAG);
				player.level().sendParticles(player, dark ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD, false, false, master.getX(),
					master.getY() + master.getBbHeight() + 0.3, master.getZ(), 2, 0.25, 0.1, 0.25, 0.01);
			}
		}
	}

	private static void teach(ServerPlayer player, Entity master, boolean dark) {
		String voice = dark ? "message.jaymospowers.zfighter_dark_master" : "message.jaymospowers.zfighter_master";
		ChatFormatting style = dark ? ChatFormatting.DARK_PURPLE : ChatFormatting.AQUA;
		if (!Powers.has(player, ZFighterPower.INSTANCE)) {
			player.sendSystemMessage(Component.translatable(voice + "_no_spark", master.getDisplayName()).withStyle(style));
			return;
		}
		boolean accepts;
		if (dark) {
			accepts = ZFighterKingdoms.rogue(player) || ZFighterPower.zenkai(player) >= DARK_DEFEATS;
		} else {
			int id = settlementOf(player, master);
			accepts = !ZFighterKingdoms.rogue(player) && (id < 0 || ZFighterKingdoms.reputation(player, id) >= GOOD_REPUTATION);
		}
		if (!accepts) {
			player.sendSystemMessage(Component.translatable(voice + "_refuses", master.getDisplayName()).withStyle(style));
			return;
		}
		long day = player.level().getGameTime() / DAY;
		Long last = player.getAttached(LESSON);
		if (last != null && last >= day && !player.isCreative()) {
			player.sendSystemMessage(Component.translatable(voice + "_tomorrow", master.getDisplayName()).withStyle(style));
			return;
		}
		player.setAttached(LESSON, day);
		if (!dark) {
			// (0.17.47) a good master's lesson is a bond the god form's ring can call on
			ZFighterGodRitual.lessonTaught(player);
		}
		Component lesson = lesson(player, dark);
		ZFighterTraining.grant(player, LESSON_TP);
		player.sendSystemMessage(Component.translatable(voice + "_teaches", master.getDisplayName(), lesson).withStyle(style));
		ServerLevel level = player.level();
		if (dark) {
			player.hurtServer(level, level.damageSources().magic(), DARK_PRICE);
			ZFighterPower.addHardship(player, DARK_HARDSHIP);
			ZFighterCorruption.darkLesson(player);
			level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.6F, 1.4F);
		} else {
			level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
	}

	/** The kingdom a master lives in: the nearest standing kingdom seat. */
	private static int settlementOf(ServerPlayer player, Entity master) {
		int best = -1;
		double bestDistance = Double.MAX_VALUE;
		for (ZFighterKingdoms.Settlement s : ZFighterKingdoms.settlements(player.level().getServer())) {
			double d = s.seat().distSqr(master.blockPosition());
			if ("KINGDOM".equals(s.type()) && d < bestDistance) {
				bestDistance = d;
				best = s.id();
			}
		}
		return best;
	}

	/** Teaches the lesson and says what it was. */
	private static Component lesson(ServerPlayer player, boolean dark) {
		ZFighterTraining.Data data = ZFighterTraining.of(player);
		for (int technique : dark ? DARK_ORDER : GOOD_ORDER) {
			if (!data.knows(technique)) {
				ZFighterTraining.learn(player, data, technique);
				return Component.translatable(ZFighterLineage.techniqueKey(player, technique));
			}
		}
		int open = ZFighterTraining.formsOpen(player);
		if (open == 0) {
			ZFighterPower.addHardship(player, ZFighterBattle.SUPER_HARDSHIP / 4);
			return Component.translatable("message.jaymospowers.zfighter_lesson_hardship");
		}
		ZFighterTraining.master(player, open, LESSON_MASTERY);
		return Component.translatable("message.jaymospowers.zfighter_lesson_mastery", Component.translatable(ZFighterLineage.formKey(player, open)));
	}
}
