package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Fuse with a villager. A Z Fighter dances alone (Z, then C, no fighter dancing opposite and no master near) beside a villager who
 * trusts him: with MCA, {@link #TRUSTED_HEARTS} hearts or more, or family, a spouse, a betrothed or hired help (the same ties the
 * "MCA strangers" rule counts, {@code Strangers}); a plain villager, a good name with the village ({@link #TRUSTED_REPUTATION} in
 * the villager's gossip). The villager joins the dance, and when it ends the two are one for {@link #BASE_TICKS} (a little longer
 * for every heart past the first {@link #TRUSTED_HEARTS}), the fighter steering the whole time and the villager <b>held</b> the way
 * a master is ({@link ZFighterMasterFusion#hold}: the same villager, invisible, unhurtable, unthinking and carried along, set
 * down beside him unchanged when it ends, so nothing of what MCA keeps on him is touched). The villager adds a little to the
 * fighter ({@link #FISTS} and the rest) and the body has a <b>knack</b> from his job ({@link Knack}). The body is drawn as both
 * where it can be: an MCA villager's looks are blended in by {@code ZFighterFusionLook}; a plain villager's are not.
 */
public final class ZFighterVillagerFusion {
	/** Hearts an MCA villager needs for you (the same as MCA's own "involved with" line). */
	static final int TRUSTED_HEARTS = 50;
	/** What a plain villager's gossip about you must come to. */
	static final int TRUSTED_REPUTATION = 30;
	/** How close the villager must be, and how far he may be when the dance ends. */
	static final double RANGE = 5.0;
	/** The fusion lasts this long (5 minutes), plus this much for each heart past {@link #TRUSTED_HEARTS} (counting up to {@link #HEART_CAP}). */
	static final int BASE_TICKS = 6000;
	static final int PER_HEART_TICKS = 20;
	static final int HEART_CAP = 50;
	/** What a villager adds, and what the body has on top. */
	static final double FISTS = 1.5, HEALTH = 12.0;
	static final int KI = 20;
	static final float POWER = 0.25F, TOUGHNESS = 0.03F, BONUS = 0.05F;

	/** The knacks' rhythms (ticks between pulses) and sizes. */
	static final int FARM_EVERY = 60, FARM_TRIES = 4, FARM_RADIUS = 6;
	static final int MEND_EVERY = 40, MEND_POINTS = 1;
	static final int HEAL_EVERY = 80, HEAL_RADIUS = 8;
	static final float HEAL_POINTS = 1.0F;
	static final int EFFECT_EVERY = 40, EFFECT_TICKS = 100;
	static final int ARROW_EVERY = 200, ARROW_COUNT = 4, ARROW_STOCK = 24;
	static final int SHEEP_EVERY = 200, SHEEP_RADIUS = 8;
	/** A scholar's extra experience for each kill (points); a butcher's chance of one more of each animal drop. */
	static final int SCHOLAR_XP = 3;
	static final float EXTRA_DROP_CHANCE = 0.5F;

	/** What a villager's job gives the fused body. */
	enum Knack {
		NONE, FARMER, SMITH, CLERIC, FISHER, SCHOLAR, BUTCHER, MASON, FLETCHER, SHEPHERD;

		static Knack of(String job) {
			return switch (job) {
				case "farmer" -> FARMER;
				case "armorer", "toolsmith", "weaponsmith" -> SMITH;
				case "cleric" -> CLERIC;
				case "fisherman" -> FISHER;
				case "librarian", "cartographer" -> SCHOLAR;
				case "butcher", "leatherworker" -> BUTCHER;
				case "mason" -> MASON;
				case "fletcher" -> FLETCHER;
				case "shepherd" -> SHEPHERD;
				default -> NONE;
			};
		}
	}

	/** A villager called to the dance: who, and when the dance ends. */
	private record Waiting(UUID villager, long at) {
	}

	private static final Map<UUID, Waiting> WAITING = new HashMap<>();
	/** The knack of each body (by the one steering) that is a villager's fusion. */
	private static final Map<UUID, Knack> KNACKS = new HashMap<>();
	private static final boolean MCA = FabricLoader.getInstance().isModLoaded("mca");

	private ZFighterVillagerFusion() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterVillagerFusion::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WAITING.clear();
			KNACKS.clear();
		});
		// a scholar's body learns more from every kill
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && KNACKS.get(killer.getUUID()) == Knack.SCHOLAR && entity.level() instanceof ServerLevel level) {
				ExperienceOrb.award(level, entity.position(), SCHOLAR_XP);
			}
		});
		// a butcher's (or a leatherworker's) body takes more from the animals and beasts it kills
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			var key = table.unwrapKey();
			if (key.isEmpty() || !key.get().identifier().getPath().startsWith("entities/") || drops.isEmpty()
				|| !(context.getOptional(LootContextParams.ATTACKING_ENTITY) instanceof ServerPlayer killer) || KNACKS.get(killer.getUUID()) != Knack.BUTCHER) {
				return;
			}
			RandomSource random = context.getRandom();
			for (ItemStack drop : List.copyOf(drops)) {
				if (!drop.isEmpty() && drop.getMaxStackSize() > 1 && random.nextFloat() < EXTRA_DROP_CHANCE) {
					drops.add(drop.copyWithCount(1));
				}
			}
		});
	}

	/**
	 * He danced alone (Z, then C): a villager within {@link #RANGE} who trusts him answers. Returns whether there was a villager to
	 * answer (so the "waiting for a partner" hint is not shown).
	 */
	static boolean begin(ServerPlayer player, long now) {
		Villager villager = nearest(player);
		if (villager == null) {
			return false;
		}
		if (!ZFighterFusion.fusable(player)) {
			return false;
		}
		if (!trusts(villager, player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_villager_refuses", villager.getDisplayName())
				.withStyle(ChatFormatting.AQUA));
			return true;
		}
		WAITING.put(player.getUUID(), new Waiting(villager.getUUID(), now + ZFighterFusion.DANCE_TICKS));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_villager_joins", villager.getDisplayName())
			.withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** The nearest grown villager within range that is not a master and not already held. */
	private static Villager nearest(ServerPlayer player) {
		List<Villager> villagers = player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(RANGE),
			e -> e.isAlive() && !e.isBaby() && !e.entityTags().contains(ZFighterMasters.TAG) && !ZFighterMasterFusion.held(e.getUUID())
				&& e.getAttached(com.jaymospowers.power.smoke.SmokeBanish.UNTIL) == null);
		Villager best = null;
		for (Villager villager : villagers) {
			if (best == null || villager.distanceToSqr(player) < best.distanceToSqr(player)) {
				best = villager;
			}
		}
		return best;
	}

	/** Whether {@code villager} would fuse with {@code player} (willing: trusts him). */
	static boolean trusts(Villager villager, ServerPlayer player) {
		return hearts(villager, player) >= TRUSTED_HEARTS || MCA && Mca.tied(villager, player)
			|| !isMca(villager) && villager.getGossips().getReputation(player.getUUID(), type -> true) >= TRUSTED_REPUTATION;
	}

	private static boolean isMca(Villager villager) {
		return MCA && Mca.is(villager);
	}

	/** His hearts for {@code player}: MCA's, or 0 for a plain villager. */
	private static int hearts(Villager villager, ServerPlayer player) {
		return isMca(villager) ? Mca.hearts(villager, player) : 0;
	}

	/** The villager's job, by its short name ("farmer", "none"). */
	private static String job(Villager villager) {
		if (isMca(villager)) {
			return Mca.job(villager);
		}
		return villager.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (Iterator<Map.Entry<UUID, Waiting>> it = WAITING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Waiting> entry = it.next();
			if (now < entry.getValue().at()) {
				continue;
			}
			it.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Entity villager = player == null ? null : player.level().getEntity(entry.getValue().villager());
			if (player != null && villager instanceof Villager living && living.isAlive() && ZFighterFusion.fusable(player)
				&& player.distanceTo(villager) <= RANGE + 2.0 && !ZFighterMasterFusion.held(villager.getUUID()) && trusts(living, player)) {
				fuse(player, living);
			}
		}
		for (Iterator<Map.Entry<UUID, Knack>> it = KNACKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Knack> entry = it.next();
			ServerPlayer leader = server.getPlayerList().getPlayer(entry.getKey());
			if (leader == null || !leader.isAlive() || ZFighterFusion.kind(leader) != ZFighterFusion.Kind.VILLAGER) {
				it.remove();
				continue;
			}
			knack(leader, entry.getValue(), now);
		}
	}

	/** The dance is done: he holds the villager, and the two are one. */
	private static void fuse(ServerPlayer player, Villager villager) {
		int hearts = hearts(villager, player);
		int length = BASE_TICKS + PER_HEART_TICKS * Math.max(0, Math.min(hearts - TRUSTED_HEARTS, HEART_CAP));
		ZFighterFusion.Stats stats = new ZFighterFusion.Stats(FISTS, HEALTH, KI, POWER, TOUGHNESS);
		Knack knack = Knack.of(job(villager));
		ZFighterFusion.begin(player, List.of(), ZFighterFusion.Kind.VILLAGER, length, BONUS, stats);
		ZFighterMasterFusion.hold(villager, player);
		ZFighterFusion.withMaster(player, villager.getUUID(), new ZFighterFusion.Look(player.getUUID(), villager.getUUID(), 0, 0));
		ZFighterPower.Live live = ZFighterPower.live(player);
		live.ki = Math.min(ZFighterPower.maxKi(player), live.ki + stats.ki() * 0.5F);
		ZFighterPower.refresh(player, live);
		KNACKS.put(player.getUUID(), knack);
		Component message = Component.translatable("message.jaymospowers.zfighter_fused", player.getDisplayName(), villager.getDisplayName())
			.withStyle(ChatFormatting.GOLD);
		for (ServerPlayer near : player.level().players()) {
			if (near.distanceToSqr(player) < 64.0 * 64.0) {
				near.sendSystemMessage(message);
			}
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_fusion_timed", (length + 1199) / 1200).withStyle(ChatFormatting.AQUA));
		if (knack != Knack.NONE) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_knack." + knack.name().toLowerCase(java.util.Locale.ROOT))
				.withStyle(ChatFormatting.GREEN));
		}
	}

	/** What the body's knack does this tick (the ones that pulse; scholars and butchers act on kills). */
	private static void knack(ServerPlayer leader, Knack knack, long now) {
		ServerLevel level = leader.level();
		switch (knack) {
			case FARMER -> {
				if (now % FARM_EVERY == 0) {
					RandomSource random = level.getRandom();
					for (int i = 0; i < FARM_TRIES; i++) {
						BlockPos pos = leader.blockPosition().offset(random.nextInt(FARM_RADIUS * 2 + 1) - FARM_RADIUS, random.nextInt(3) - 1,
							random.nextInt(FARM_RADIUS * 2 + 1) - FARM_RADIUS);
						if (BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, pos)) {
							level.levelEvent(1505, pos, 15);
						}
					}
				}
			}
			case SMITH -> {
				if (now % MEND_EVERY == 0) {
					for (ItemStack held : new ItemStack[] {leader.getMainHandItem(), leader.getOffhandItem()}) {
						if (held.isDamaged()) {
							held.setDamageValue(Math.max(0, held.getDamageValue() - MEND_POINTS));
						}
					}
				}
			}
			case CLERIC -> {
				if (now % HEAL_EVERY == 0) {
					leader.heal(HEAL_POINTS);
					for (ServerPlayer ally : level.players()) {
						if (ally != leader && ally.isAlive() && !ally.isSpectator() && ally.distanceToSqr(leader) <= HEAL_RADIUS * HEAL_RADIUS) {
							ally.heal(HEAL_POINTS);
						}
					}
					level.sendParticles(MagicFx.wisp(0x60E070, 1.2F), leader.getX(), leader.getY() + 1.0, leader.getZ(), 4, 0.4, 0.6, 0.4, 0.0);
				}
			}
			case FISHER -> {
				if (now % EFFECT_EVERY == 0) {
					leader.addEffect(new MobEffectInstance(MobEffects.LUCK, EFFECT_TICKS, 0, true, false));
					leader.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, EFFECT_TICKS, 0, true, false));
				}
			}
			case MASON -> {
				if (now % EFFECT_EVERY == 0) {
					leader.addEffect(new MobEffectInstance(MobEffects.HASTE, EFFECT_TICKS, 0, true, false));
				}
			}
			case FLETCHER -> {
				if (now % ARROW_EVERY == 0 && leader.getInventory().countItem(Items.ARROW) < ARROW_STOCK) {
					leader.getInventory().add(new ItemStack(Items.ARROW, ARROW_COUNT));
				}
			}
			case SHEPHERD -> {
				if (now % SHEEP_EVERY == 0) {
					for (Sheep sheep : level.getEntitiesOfClass(Sheep.class, leader.getBoundingBox().inflate(SHEEP_RADIUS), Sheep::isSheared)) {
						sheep.setSheared(false);
					}
				}
			}
			default -> {
			}
		}
	}

	/** Everything that touches MCA's classes, only loaded when MCA is (it is compiled against, never required). */
	private static final class Mca {
		static boolean is(Villager villager) {
			return villager instanceof net.conczin.mca.entity.VillagerEntityMCA;
		}

		static int hearts(Villager villager, ServerPlayer player) {
			return villager instanceof net.conczin.mca.entity.VillagerEntityMCA mca ? mca.getVillagerBrain().getMemoriesForPlayer(player).getHearts() : 0;
		}

		/** Family, kin, a spouse, a betrothed, a promised one or hired help: the ties MCA's strangers rule counts. */
		static boolean tied(Villager villager, ServerPlayer player) {
			if (!(villager instanceof net.conczin.mca.entity.VillagerEntityMCA mca)) {
				return false;
			}
			for (net.conczin.mca.entity.interaction.Constraint tie : new net.conczin.mca.entity.interaction.Constraint[] {
				net.conczin.mca.entity.interaction.Constraint.FAMILY, net.conczin.mca.entity.interaction.Constraint.RELATIVE,
				net.conczin.mca.entity.interaction.Constraint.SPOUSE, net.conczin.mca.entity.interaction.Constraint.ENGAGED,
				net.conczin.mca.entity.interaction.Constraint.PROMISED, net.conczin.mca.entity.interaction.Constraint.MERCENARY}) {
				if (tie.test(mca, player)) {
					return true;
				}
			}
			return false;
		}

		static String job(Villager villager) {
			return villager instanceof net.conczin.mca.entity.VillagerEntityMCA mca ? mca.getProfessionId().getPath() : "none";
		}
	}
}
