package com.jaymoskingdoms;

import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.270) Andy: "characters should have context sensitive talk options." What a town's villager opens a talk about
 * work ({@link Careers#talk}) with depends on who they are and where you stand with their town, before MCA's plain
 * "Looking for work?":
 * <ol>
 * <li>your apprentice: eager to show you what they've learned;</li>
 * <li>its ruler: your service to the realm by name, or what they'd have of a newcomer;</li>
 * <li>one of its watch: your round, if you're of the watch; else whether it needs another pair of eyes;</li>
 * <li>one of your trade (an MCA job matching a job you hold there): one of their own, by your rank;</li>
 * <li>one whose trade the town is hiring for: they'd show you how it's done;</li>
 * <li>anyone, to one who works there: back to work, and your orders open.</li>
 * </ol>
 */
public final class WorkTalk {
	/** MCA's (and vanilla's) professions → the job that's their trade. */
	static final Map<String, Careers.Job> TRADES = Map.ofEntries(
		Map.entry("butcher", Careers.Job.BUTCHER),
		Map.entry("farmer", Careers.Job.FARMER),
		Map.entry("fisherman", Careers.Job.FISHER),
		Map.entry("armorer", Careers.Job.SMITH),
		Map.entry("weaponsmith", Careers.Job.SMITH),
		Map.entry("toolsmith", Careers.Job.SMITH),
		Map.entry("leatherworker", Careers.Job.HUNTER),
		Map.entry("mason", Careers.Job.BUILDER),
		Map.entry("lumberjack", Careers.Job.WOODCUTTER),
		Map.entry("miner", Careers.Job.MINER));

	private WorkTalk() {
	}

	/**
	 * What {@code speaker} of {@code f} opens a talk about work with {@code player}, knowing who they are; null for the
	 * plain greeting. {@code open}: the jobs {@code f} would give them.
	 */
	static @Nullable Component greeting(ServerPlayer player, LivingEntity speaker, Faction f, List<Careers.Job> open) {
		Careers.Career c = Careers.of(player);
		Careers.Post apprenticeOf = null, best = null;
		for (Careers.Post p : c.posts()) {
			if (p.faction() != f.id() || p.kind() == null) {
				continue;
			}
			if (p.apprentice().isPresent() && p.apprentice().get().equals(speaker.getUUID())) {
				apprenticeOf = p;
			}
			if (best == null || c.rank(p.job()) > c.rank(best.job())) {
				best = p;
			}
		}
		if (apprenticeOf != null) {
			return Component.translatable("talk.jaymoskingdoms.work.ctx.apprentice", player.getDisplayName(), apprenticeOf.kind().title());
		}
		if (Rulers.is(speaker)) {
			return best != null
				? Component.translatable("talk.jaymoskingdoms.work.ctx.ruler_serving", f.name(), titled(c, best))
				: Component.translatable("talk.jaymoskingdoms.work.ctx.ruler", f.name());
		}
		if (speaker instanceof Worker w && w.role() == Folk.GUARD) {
			// (0.7.385) a schooled guard looks down on a rival school's student
			Component rival = com.jaymoskingdoms.forms.Rivals.greeting(player, speaker);
			if (rival != null) {
				return rival;
			}
			Careers.Post watch = c.post(Careers.Job.WATCH.key);
			if (watch != null && watch.faction() == f.id()) {
				return Component.translatable("talk.jaymoskingdoms.work.ctx.guard_watch", titled(c, watch));
			}
			return open.contains(Careers.Job.WATCH) ? Component.translatable("talk.jaymoskingdoms.work.ctx.guard") : null;
		}
		Careers.Job trade = trade(speaker);
		if (trade != null) {
			Careers.Post mine = c.post(trade.key);
			if (mine != null && mine.faction() == f.id()) {
				return Component.translatable("talk.jaymoskingdoms.work.ctx.same_trade", titled(c, mine));
			}
			if (open.contains(trade)) {
				return Component.translatable("talk.jaymoskingdoms.work.ctx.trade_wanted", trade.title());
			}
		}
		if (best != null) {
			int orders = c.posts().stream().filter(p -> p.faction() == f.id()).mapToInt(p -> p.orders().size()).sum();
			return Component.translatable("talk.jaymoskingdoms.work.ctx.posted", titled(c, best), orders);
		}
		return null;
	}

	/** "Journeyman Smith". */
	private static Component titled(Careers.Career c, Careers.Post p) {
		return Careers.title(p.kind(), c.rank(p.job()));
	}

	/** The job that's {@code mob}'s trade (by their MCA profession), or null. */
	private static Careers.@Nullable Job trade(LivingEntity mob) {
		if (!(mob instanceof Villager v)) {
			return null;
		}
		return TRADES.get(BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath());
	}
}
