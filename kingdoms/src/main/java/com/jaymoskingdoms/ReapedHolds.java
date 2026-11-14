package com.jaymoskingdoms;

import com.jaymoskingdoms.reaped.Reaped;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;

/**
 * The Reaper's holds: illager camps its host has taken. When one of the reaped ({@link Reaped}) kills a camp's warlord
 * the camp doesn't fall: it changes hands. Every illager still in it goes over (dead, as the same kind), a reaped warlord
 * takes the seat, and from then on the camp is the Reaper's standing side in the wars:
 * <ul>
 * <li>it is still a camp ({@link Camps}, {@link IllagerCamps}), so it holds its ground, grows, and raids the realms and
 * villages round it ({@link Raids}) with the reaped where it sent illagers ({@link Illagers#spawn});</li>
 * <li>it grows from the dead as well: a living illager its people kill gets up on its roster ({@link #enrol});</li>
 * <li>the courts round it post its warlord as wanted ({@link Bounties#warlord});</li>
 * <li>it is broken as any camp is: kill the warlord.</li>
 * </ul>
 * The Reaper's hosts never march on one ({@link ZombieHordes}).
 */
public final class ReapedHolds {
	/** Courts this near (blocks) post the hold's warlord as wanted, for this much. */
	static final double BOUNTY_REACH = 600.0;
	static final int BOUNTY = 40;
	/** A hold's roster stops growing from the dead at this many. */
	static final int ROSTER_MOST = 30;
	/** How far round the seat its illagers are turned (blocks). */
	static final double TURN_RANGE = 72.0;

	/** On the Overworld: the camps (faction ids) the Reaper holds. Saved. */
	static final AttachmentType<List<Integer>> HOLDS = AttachmentRegistry.create(JaymosKingdoms.id("reaped_holds"),
		builder -> builder.persistent(Codec.INT.listOf()));

	private ReapedHolds() {
	}

	static void register() {
		// (the attachment is made by loading this class)
	}

	static boolean is(MinecraftServer server, int camp) {
		return Factions.level(server).getAttachedOrElse(HOLDS, List.of()).contains(camp);
	}

	/** {@code warlord} of the living camp {@code campId} died: true if one of the reaped killed him and the camp is the Reaper's now. */
	static boolean take(ServerLevel level, LivingEntity warlord, int campId, DamageSource source) {
		MinecraftServer server = level.getServer();
		Faction camp = Factions.get(server, campId);
		if (!(source.getEntity() instanceof Reaped) || camp == null || camp.fallen() || is(server, campId) || !Camps.illager(server, campId)) {
			return false;
		}
		List<Integer> holds = new ArrayList<>(Factions.level(server).getAttachedOrElse(HOLDS, List.of()));
		holds.add(campId);
		Factions.level(server).setAttached(HOLDS, List.copyOf(holds));
		// everyone still in it goes over
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(camp.seat()).inflate(TURN_RANGE),
			m -> m.isAlive() && m != warlord && !(m instanceof Reaped) && Illagers.tag(m) != null && Illagers.tag(m).camp() == campId)) {
			turn(level, mob);
		}
		BlockPos seat = Construction.standable(level, warlord.blockPosition(), 3);
		Faction held = Illagers.crown(level, camp, seat);
		level.playSound(null, seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 64.0F, 0.5F);
		for (Faction f : Factions.all(server)) {
			if (!f.fallen() && f.type().isRealm() && f.seat().closerThan(seat, BOUNTY_REACH)) {
				Bounties.warlord(server, held, f, "The Reaped Warlord of " + held.name(), BOUNTY);
			}
		}
		JaymosKingdoms.LOGGER.info("{} fell to the Reaper's host and is held by it", camp.name());
		return true;
	}

	/** One of a taken camp's living illagers dies where it stands and gets up as the same kind of reaped, its place in the camp kept. */
	private static void turn(ServerLevel level, Mob mob) {
		Illagers.Tag tag = Illagers.tag(mob);
		EntityType<? extends Mob> as = Reaped.risesAs(mob);
		if (as == null) {
			// (a ravager: no reaped kind; it bolts, and leaves the roster)
			Factions.update(level.getServer(), tag.camp(), f -> f.withHoldings(f.holdings().adjust(tag.role(), -1)));
			mob.discard();
			return;
		}
		Mob risen = as.create(level, EntitySpawnReason.CONVERSION);
		if (risen == null) {
			return;
		}
		risen.snapTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), 0.0F);
		risen.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.CONVERSION, null);
		keep(risen, tag, mob.hasHome() ? mob.getHomePosition() : mob.blockPosition(), mob.hasHome() ? mob.getHomeRadius() : 16);
		level.sendParticles(Fx.miasma(Reaper.SMOKE, 1.3F), mob.getX(), mob.getY() + 1.0, mob.getZ(), 12, 0.3, 0.6, 0.3, 0.01);
		mob.discard();
		level.addFreshEntity(risen);
	}

	private static void keep(Mob mob, Illagers.Tag tag, BlockPos home, int radius) {
		if (mob instanceof Raider raider) {
			raider.setCanJoinRaid(false);
		}
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false);
		mob.setHomeTo(home, radius);
		mob.setAttached(Illagers.TAG, tag);
	}

	/** {@code newcomer} (a living illager {@code killer} killed, got up again) joins the hold {@code killer} belongs to, if it has room. */
	public static void enrol(Mob killer, Mob newcomer) {
		Illagers.Tag tag = Illagers.tag(killer);
		if (tag == null || tag.raid() || !(killer.level() instanceof ServerLevel level) || !is(level.getServer(), tag.camp())) {
			return;
		}
		Faction camp = Factions.get(level.getServer(), tag.camp());
		if (camp == null || camp.fallen() || Illagers.fighters(camp) >= ROSTER_MOST) {
			return;
		}
		int role = newcomer instanceof Pillager ? Illagers.PILLAGER : newcomer instanceof SpellcasterIllager ? Illagers.EVOKER : Illagers.VINDICATOR;
		keep(newcomer, new Illagers.Tag(tag.camp(), role, false, false), killer.hasHome() ? killer.getHomePosition() : camp.seat(), IllagerBuildings.HOME_RADIUS);
		Factions.update(level.getServer(), tag.camp(), f -> f.withHoldings(f.holdings().adjust(role, 1)));
	}
}
