package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.73) Guards aren't killed where they stand: the blow that would kill one of a realm's guards or archers puts them
 * down instead; a player can take the weapons out of their hands (right-click), but nothing else. (0.7.84) Andy:
 * "villagers should try to help others that are downed, and they can all be downed now, unless decapitated": every
 * villager and the settlements' own folk (not their rulers) go down, and the folk about look after their own: a grown
 * villager near a downed one, not afraid or fighting, comes over, kneels and tends them, and after {@link #TEND_TICKS} at
 * their side has them back on their feet (a player's hit while they tend starts it over).
 * <p>
 * (0.7.98) Andy (2026-10-19): "remove our downed system for a universal one". The downed state itself (going down, the
 * AI hold, bleeding out, wounds, the food revive, carrying, the pose) has moved into Jaymo's Powers' Wounds
 * ({@code core/wounds/Downed}), for players and tamed animals too. Kingdoms never compiles against Powers: it reads the
 * state from the scoreboard tag {@link #DOWNED_TAG}, marks which of its folk may go down with {@link #DOWNABLE_TAG} (when
 * they're hurt), and asks for a tended one to get up with {@link #REVIVE_TAG}. What stays here is what only Kingdoms
 * knows: who its folk are, the tending, disarming a downed guard, and (in {@code Rearm}, {@code Pickpocket},
 * {@code ShutIn}, {@code Routines}, {@code Workers}) its folk leaving the downed alone. Without Powers, its folk simply
 * die.
 * <p>
 * (0.7.102) Andy (2026-10-20): "I dont want any modern medicine". The tending is a bandaging now: when it's done the body
 * is tagged {@link #BANDAGED_TAG} as well, and Powers stops its bleeding and gives it a bandage's heal.
 * <p>
 * (0.7.453) Andy (2026-10-31): "mounted guards should dismount to revive, right now they just walk into you perpetually".
 * A rider may come to help: he rides over, gets down {@link #DISMOUNT_REACH} off, tends them on foot while his horse
 * waits, and climbs back up when he's done (or called away). The horse he left is kept for him meanwhile: no other
 * rider takes it and a lent one doesn't vanish ({@link #waiting}).
 */
public final class Downed {
	/** (0.7.84) Help: how far a villager comes to tend a downed one, how often one is looked for, how close is at their side, how long tending takes (ticks). */
	static final double HELP_RANGE = 24.0, TEND_REACH = 1.8;
	static final int HELP_LOOK_TICKS = 20, TEND_TICKS = 200;
	static final float HELP_SPEED = 0.8F;
	/** (0.7.453) A mounted helper gets down this near the downed one (blocks), and climbs back on a horse no farther off than this. */
	static final double DISMOUNT_REACH = 4.0, REMOUNT_REACH = 10.0;
	/** (0.7.181) A downed player is tended where their reputation is above this. */
	static final int HELP_PLAYER_REP = 0;
	/** (0.7.98) How far round each player the downed are looked for, to be tended (blocks). */
	static final double LOOK_AROUND = 64.0;

	/** (0.7.98) Jaymo's Powers' scoreboard tags: on the downed; on what may go down; on a tended one to get up. */
	public static final String DOWNED_TAG = "jaymospowers_downed", DOWNABLE_TAG = "jaymospowers_downable", REVIVE_TAG = "jaymospowers_revive";
	/**
	 * (0.7.102) Jaymo's Powers' tag on a body a villager has bandaged: Powers takes it off, stops that body's bleeding and
	 * gives it a linen bandage's heal (Wounds phase 4).
	 */
	public static final String BANDAGED_TAG = "jaymospowers_bandaged";
	/** Our hurt listener runs before everyone else's (Powers' takes a player's blow on the downed, and stops it there). */
	private static final Identifier EARLY = JaymosKingdoms.id("downed_early");

	/** (0.7.84) Who's tending each downed one, and how long they've been at it (ticks). Not saved. */
	private static final class Tending {
		final Villager helper;
		int ticks;
		/** (0.7.453) The horse they got down from to tend them, waiting to be ridden again. */
		@Nullable AbstractHorse horse;

		Tending(Villager helper) {
			this.helper = helper;
		}
	}

	private static final Map<LivingEntity, Tending> TENDED = new WeakHashMap<>();
	/** The villagers tending someone just now. */
	private static final Map<Villager, LivingEntity> HELPING = new WeakHashMap<>();
	/** (0.7.453) Horses waiting while their riders tend someone downed. */
	private static final Map<AbstractHorse, Villager> WAITING = new WeakHashMap<>();

	private Downed() {
	}

	static void register() {
		// (0.7.459) righteous guards arrest the downed rather than execute them (Powers' MobExecutions)
		PowersCompat.spareRighteousGuards();
		ServerLivingEntityEvents.ALLOW_DAMAGE.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(EARLY, (entity, source, amount) -> {
			if (!(entity.level() instanceof ServerLevel)) {
				return true;
			}
			if (isDowned(entity)) {
				Tending tending = TENDED.get(entity);
				if (tending != null && source.getEntity() instanceof Player) {
					// (a player's blow: whoever was tending them starts over)
					tending.ticks = 0;
				}
				return true;
			}
			// (0.7.98) which of ours go down, should this blow kill: Powers reads the mark
			boolean mark = downable(entity);
			if (mark != entity.entityTags().contains(DOWNABLE_TAG)) {
				if (mark) {
					entity.addTag(DOWNABLE_TAG);
				} else {
					entity.removeTag(DOWNABLE_TAG);
				}
			}
			return true;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			// (sneak + use on the downed is Powers' lift; a plain hold is its revive, which only watches, so the disarm still comes first)
			if (!(entity instanceof Mob body) || !isDowned(body) || player.isSpectator() || player.isShiftKeyDown() || !ours(body)) {
				return InteractionResult.PASS;
			}
			// (taken here, so a downed villager's screen doesn't open, and neither hand gets at their pockets)
			if (!world.isClientSide() && hand == InteractionHand.MAIN_HAND) {
				disarm(player, body);
			}
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Downed::tick);
	}

	/** One of a realm's guards or archers: its folk, a villager in its service, or a villager in that job. */
	public static boolean isGuard(LivingEntity entity) {
		if (entity instanceof Worker w && (w.role() == Folk.GUARD || w.role() == Folk.ARCHER)) {
			return true;
		}
		if (entity instanceof Villager v && !v.isBaby()) {
			String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
			return job.equals("guard") || job.equals("archer");
		}
		return false;
	}

	/**
	 * (0.7.84) Whether the blow that would kill {@code entity} downs it instead: any villager, (0.7.132) any of Kingdoms'
	 * folk. (0.7.98) Marked for Powers with {@link #DOWNABLE_TAG}; Powers itself downs every humanoid.
	 */
	public static boolean downable(LivingEntity entity) {
		// (0.7.132) Andy: "ALL HUMANOIDS go down, if they werent decapitated or otherwise prevented from the downstate":
		// rulers, orcs, raiders, barbarians and looters too (Powers downs every humanoid; the mark is for its other checks)
		return isGuard(entity) || entity instanceof Villager || entity instanceof Folk;
	}

	/** (0.7.132) Someone villagers would tend: a villager, or a settlement's own folk (not a ruler, orc, raider or barbarian). */
	private static boolean kin(LivingEntity body) {
		if (campMember(body)) {
			// (0.7.184) a camp's own are tended by the camp's own villagers (see sameSide)
			return true;
		}
		if (body instanceof Villager || isGuard(body)) {
			return true;
		}
		if (body instanceof Folk folk && !Orcs.isOrc(folk) && body.level() instanceof ServerLevel level) {
			Folk.Role role = Folk.role(folk.role());
			if (role != null && role.leader() || Assault.attacker(folk)) {
				return false;
			}
			Faction f = Factions.get(level.getServer(), folk.factionId());
			return f != null && f.type() != FactionType.BARBARIAN;
		}
		return false;
	}

	/**
	 * (0.7.184) A barbarian camp's or pirate cove's own: its MCA villagers, and its raiders and crews as folk (not a chief,
	 * a captain, an orc or a goblin).
	 */
	private static boolean campMember(LivingEntity body) {
		if (Barbarians.isCampVillager(body)) {
			return true;
		}
		return body instanceof Folk folk && (folk.role() == Folk.BARBARIAN || Pirates.isPirate(folk.role()) && folk.role() != Pirates.CAPTAIN);
	}

	/**
	 * (0.7.184) Who tends whom: a realm's villagers their own side, a camp's villagers only their own camp's; neither
	 * crosses over.
	 */
	private static boolean sameSide(Villager helper, LivingEntity body) {
		boolean campHelper = Barbarians.isCampVillager(helper);
		if (campHelper != campMember(body)) {
			return false;
		}
		if (!campHelper) {
			return true;
		}
		int camp = body instanceof Worker w ? w.factionId() : -2;
		return helper instanceof Worker hw && hw.factionId() == camp;
	}

	/**
	 * (0.7.181) Andy (2026-10-24): "guards and villagers should help players up as long as they are above 0 rep": a
	 * downed player whose reputation with the settlement they lie in (its land, or a seat within a village's reach) is
	 * above {@link #HELP_PLAYER_REP}. (0.7.186) That's now the dutiful villager's way: whether each one comes is their
	 * temperament's to say ({@link Temperaments#helpsPlayer}): the kindhearted help anyone, in the wild too; the
	 * spiteful only a friend.
	 */
	private static boolean helpable(ServerPlayer player) {
		return !player.isCreative() && !player.isSpectator();
	}

	/** (0.7.98) Ours to disarm: a villager or one of our folk (not a downed player or someone's dog). Server side. */
	private static boolean ours(LivingEntity body) {
		return body instanceof Villager || body.entityTags().contains(DOWNABLE_TAG);
	}

	/** (0.7.84) {@code v} is tending someone downed just now. */
	public static boolean helping(LivingEntity v) {
		return v instanceof Villager villager && HELPING.containsKey(villager);
	}

	/** (0.7.98) Down, by Jaymo's Powers' tag. Server side only (scoreboard tags aren't sent to the clients). */
	public static boolean isDowned(Entity entity) {
		return entity.entityTags().contains(DOWNED_TAG);
	}

	/**
	 * (0.7.98) The tending, every tick: whoever is at it carries on; and every {@link #HELP_LOOK_TICKS}, the downed near
	 * any player (where the world is awake) are looked over for someone to come and tend them.
	 */
	private static void tick(MinecraftServer server) {
		if (!TENDED.isEmpty()) {
			for (Map.Entry<LivingEntity, Tending> e : new ArrayList<>(TENDED.entrySet())) {
				LivingEntity body = e.getKey();
				if (!(body.level() instanceof ServerLevel level) || !body.isAlive() || body.isRemoved() || !isDowned(body)) {
					untend(body);
					continue;
				}
				if (tend(level, body, e.getValue())) {
					// (Powers gets them up on their next tick; (0.7.102) and sees to the bandaging: their bleeding stops)
					body.addTag(REVIVE_TAG);
					body.addTag(BANDAGED_TAG);
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, body.getX(), body.getY() + 0.8, body.getZ(), 10, 0.4, 0.4, 0.4, 0.0);
				}
			}
		}
		if (server.getTickCount() % HELP_LOOK_TICKS != 0) {
			return;
		}
		List<Mob> seen = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			if (player.isAlive() && isDowned(player) && !TENDED.containsKey(player)) {
				// (0.7.181) a downed player: (0.7.186) whoever's nature has them come over
				findHelper(level, player);
				Temperaments.jeer(level, player);
			}
			for (Mob body : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(LOOK_AROUND),
				m -> m.isAlive() && isDowned(m) && !TENDED.containsKey(m))) {
				if (!seen.contains(body)) {
					seen.add(body);
					findHelper(level, body);
				}
			}
		}
	}

	/**
	 * (0.7.84) Someone comes to tend a downed {@code body}: the nearest grown villager free to (not downed, afraid,
	 * fighting, asleep or tending another) walks over.
	 */
	private static void findHelper(ServerLevel level, LivingEntity body) {
		// (0.7.132) only their own are tended: villagers and a settlement's folk, not a looter, a raider or a downed monster;
		// (0.7.181) and players the settlement thinks well of; (0.7.186) or, either way, whomever a helper's temperament
		// has them tend (Temperaments)
		if (body.isPassenger() || body.isOnFire() || body.entityTags().contains(REVIVE_TAG) || body instanceof ServerPlayer p && !helpable(p)) {
			return;
		}
		ServerPlayer player = body instanceof ServerPlayer p ? p : null;
		Faction place = player == null ? null : Temperaments.placeOf(level.getServer(), level, player);
		boolean kin = player == null && !Looters.is(body) && kin(body);
		if (player == null && !kin && !Temperaments.humanoid(body)) {
			// (a downed animal or monster: nobody's)
			return;
		}
		Villager helper = null;
		double best = Double.MAX_VALUE;
		double reach = HELP_RANGE * Temperaments.RANGE_KIND / 100.0;
		for (Villager v : level.getEntitiesOfClass(Villager.class, body.getBoundingBox().inflate(reach), v -> canHelp(v, body) && !HELPING.containsKey(v))) {
			double d = v.distanceToSqr(body);
			double range = Temperaments.helpRange(v);
			if (d < best && d <= range * range
				&& (player != null ? Temperaments.helpsPlayer(level, v, player, place) : Temperaments.tendsMob(level, v, body, kin))) {
				best = d;
				helper = v;
			}
		}
		if (helper == null) {
			return;
		}
		TENDED.put(body, new Tending(helper));
		HELPING.put(helper, body);
		level.playSound(null, helper.getX(), helper.getY(), helper.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.2F);
		if (player != null) {
			Temperaments.comes(level, helper, player);
		}
	}

	/** (0.7.84) The helper walks over, kneels and sees to them. True once they've been at it long enough to get them up. */
	private static boolean tend(ServerLevel level, LivingEntity body, Tending tending) {
		if (body.isPassenger() || body.isOnFire() || !canHelp(tending.helper, body)) {
			untend(body);
			return false;
		}
		Villager helper = tending.helper;
		AbstractHorse ridden = Cavalry.horse(helper);
		boolean gotDown = ridden != null && helper.distanceToSqr(body) <= DISMOUNT_REACH * DISMOUNT_REACH;
		if (gotDown) {
			// (0.7.453) a rider gets down to see to them; his horse waits
			helper.stopRiding();
			ridden.getNavigation().stop();
			tending.horse = ridden;
			WAITING.put(ridden, helper);
			helper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		if (tending.horse != null && tending.horse.isAlive() && !tending.horse.isVehicle()) {
			tending.horse.getNavigation().stop();
		}
		if (helper.distanceToSqr(body) > TEND_REACH * TEND_REACH) {
			helper.setShiftKeyDown(false);
			if (body.tickCount % 10 == 0 || gotDown) {
				BehaviorUtils.setWalkAndLookTargetMemories(helper, body.blockPosition(), HELP_SPEED, 1);
			}
			return false;
		}
		// at their side: kneeling, hands busy
		helper.getNavigation().stop();
		helper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		helper.getLookControl().setLookAt(body.getX(), body.getY() + 0.2, body.getZ());
		helper.setShiftKeyDown(true);
		if (++tending.ticks % 25 == 0) {
			helper.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			level.sendParticles(ParticleTypes.HEART, body.getX(), body.getY() + 0.6, body.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
		}
		if (tending.ticks >= Temperaments.tendTicks(helper)) {
			untend(body);
			if (body instanceof ServerPlayer player) {
				Temperaments.gotUp(level, helper, player);
				// (0.7.254) they stay a moment, to be thanked ({@link SharedHistory})
				SharedHistory.revived(helper, player);
			}
			return true;
		}
		return false;
	}

	private static boolean canHelp(Villager v, LivingEntity body) {
		// ((0.7.453) a rider may come too: he gets down at their side)
		return v.isAlive() && v != body && !v.isBaby() && sameSide(v, body) && !isDowned(v) && !v.isSleeping() && (!v.isPassenger() || Cavalry.mounted(v)) && !v.isNoAi() && v.getTarget() == null
			&& (!Frights.afraid(v.getUUID()) || Temperaments.fearless(v)) && !McaCompat.panicking(v) && v.level() == body.level()
			&& v.distanceToSqr(body) < Temperaments.helpRange(v) * Temperaments.helpRange(v) * 1.5;
	}

	private static void untend(LivingEntity body) {
		Tending tending = TENDED.remove(body);
		if (tending != null) {
			Villager helper = tending.helper;
			HELPING.remove(helper);
			helper.setShiftKeyDown(false);
			// (0.7.453) no longer bound for them (a rider left steering into them forever, before)
			helper.getBrain().getMemory(MemoryModuleType.WALK_TARGET).ifPresent(walk -> {
				if (walk.getTarget().currentBlockPosition().closerThan(body.blockPosition(), 2.0)) {
					helper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
					helper.getNavigation().stop();
				}
			});
			remount(helper, tending.horse);
		}
	}

	/** (0.7.453) A rider who got down to tend someone climbs back on his horse, if it's still there and free. */
	private static void remount(Villager helper, @Nullable AbstractHorse horse) {
		if (horse == null) {
			return;
		}
		WAITING.remove(horse);
		if (helper.isAlive() && !isDowned(helper) && !helper.isSleeping() && !helper.isPassenger() && horse.isAlive() && !horse.isRemoved()
			&& !horse.isVehicle() && horse.level() == helper.level() && helper.distanceToSqr(horse) <= REMOUNT_REACH * REMOUNT_REACH) {
			helper.getNavigation().stop();
			helper.startRiding(horse);
		}
	}

	/** (0.7.453) {@code horse} waits for its rider, who got down to tend someone downed: keep it for him. */
	static boolean waiting(Entity horse) {
		return horse instanceof AbstractHorse h && WAITING.containsKey(h);
	}

	/** {@code player} takes the weapons out of a downed guard's hands (and nothing else). */
	private static void disarm(Player player, LivingEntity body) {
		boolean took = false;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
			ItemStack held = body.getItemBySlot(slot);
			if (held.isEmpty()) {
				continue;
			}
			body.setItemSlot(slot, ItemStack.EMPTY);
			if (!player.getInventory().add(held) && !held.isEmpty()) {
				player.drop(held, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			took = true;
		}
		if (took) {
			body.level().playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.downed.disarmed", body.getDisplayName()).withStyle(ChatFormatting.GRAY));
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.downed.nothing").withStyle(ChatFormatting.GRAY));
		}
	}
}
