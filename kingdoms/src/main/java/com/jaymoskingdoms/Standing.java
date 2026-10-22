package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.175) Andy (2026-10-22): "the King or queen's temperament and status should be on this screen. same with guards."
 * What MCA's interact screen shows of a realm's ruler or guard, under its name, trade, mood and personality (drawn by
 * {@code McaInteractInfoMixin}): a ruler's title and realm, temperament ({@link Tempers}), reign, and what they're about;
 * a guard's realm, the ruler they're sworn to (and that ruler's temper), and their duty. Worked out on the server
 * every {@link #EVERY} ticks and synced with the villager, each line with what it says when hovered.
 */
public final class Standing {
	/** How often a ruler's or guard's lines are looked to (ticks). */
	static final int EVERY = 40;

	/** The lines, and (the same count, empty for none) what each says when the mouse is over it. */
	public record Lines(List<Component> lines, List<Component> hovers) {
		static final StreamCodec<RegistryFriendlyByteBuf, Lines> STREAM_CODEC = StreamCodec.composite(
			ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), Lines::lines,
			ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), Lines::hovers,
			Lines::new);
	}

	/** On a ruler or a guard: their lines (absent on anyone else). Synced, not saved. */
	public static final AttachmentType<Lines> LINES = AttachmentRegistry.create(JaymosKingdoms.id("standing"),
		builder -> builder.syncWith(Lines.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private Standing() {
	}

	/** {@code w}'s lines brought up to date (from {@link Workers#step}), or taken off if they're neither ruler nor guard. */
	static void update(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		Faction f = Factions.get(level.getServer(), w.factionId());
		Lines lines = f == null || f.fallen() ? null : Rulers.leaderRole(w.role()) ? ruler(level, mob, w, f) : w.role() == Folk.GUARD ? guard(level, mob, w, f) : null;
		if (lines == null) {
			if (mob.hasAttached(LINES)) {
				mob.removeAttached(LINES);
			}
		} else if (!lines.equals(mob.getAttached(LINES))) {
			mob.setAttached(LINES, lines);
		}
	}

	private static @Nullable Lines ruler(ServerLevel level, PathfinderMob mob, Worker w, Faction f) {
		if (!f.leader().map(mob.getUUID()::equals).orElse(false)) {
			return null;
		}
		List<Component> lines = new ArrayList<>(), hovers = new ArrayList<>();
		boolean female = McaCompat.female(mob);
		int guards = Rulers.guards(level, f, new AABB(f.seat()).inflate(Economy.POPULATION_RANGE));
		lines.add(Component.translatable("standing.jaymoskingdoms.ruler", Rulers.title(w.role(), female), f.name()).withStyle(ChatFormatting.GOLD));
		hovers.add(Component.translatable("standing.jaymoskingdoms.realm.hover", guards, f.treasury()));
		Tempers.Temper t = Tempers.of(f);
		if (t != null) {
			lines.add(temper(t));
			hovers.add(Component.translatable("standing.jaymoskingdoms.temper." + t.key() + ".hover", Purists.bloodName(f)));
		}
		Rulers.Reign reign = Rulers.reignOf(level.getServer(), f);
		if (reign != null && reign.since() >= 0) {
			long days = Math.max(0L, Production.day(level.getServer()) - reign.since());
			lines.add(days == 0 ? Component.translatable("standing.jaymoskingdoms.reign.today")
				: Component.translatable("standing.jaymoskingdoms.reign.days", days));
			Heritage.Kind blood = Heritage.Kind.byKey(reign.blood());
			hovers.add(blood == null ? Component.empty() : Component.translatable("standing.jaymoskingdoms.reign.hover", Purists.name(blood)));
		}
		lines.add(Component.translatable("standing.jaymoskingdoms.status", Component.translatable("standing.jaymoskingdoms.ruler." + rulerDoing(level, mob, f)))
			.withStyle(ChatFormatting.WHITE));
		hovers.add(Component.translatable("standing.jaymoskingdoms.ruler.day.hover"));
		return new Lines(List.copyOf(lines), List.copyOf(hovers));
	}

	/** What a ruler is about just now. */
	private static String rulerDoing(ServerLevel level, PathfinderMob mob, Faction f) {
		long t = Royals.timeOfDay(level);
		if (mob.isSleeping()) {
			return "asleep";
		}
		if (Arrests.judging(mob)) {
			return "judging";
		}
		if (mob.getLastHurtByMob() != null && mob.tickCount - mob.getLastHurtByMobTimestamp() < Rulers.HURT_TICKS) {
			return "danger";
		}
		if (Rulers.offDuty(t)) {
			return Royals.daytime(level) ? "free" : "night";
		}
		if (Royals.out(mob)) {
			return "round";
		}
		if (t < Rulers.ROUND_TO && Wardens.confined(level, f, mob)) {
			return "kept_in";
		}
		return Factions.keep(level.getServer(), f.id()) != null ? "court" : "free";
	}

	private static @Nullable Lines guard(ServerLevel level, PathfinderMob mob, Worker w, Faction f) {
		if (!f.type().isRealm()) {
			return null;
		}
		List<Component> lines = new ArrayList<>(), hovers = new ArrayList<>();
		lines.add(Component.translatable("standing.jaymoskingdoms.guard", f.name()).withStyle(ChatFormatting.GOLD));
		hovers.add(Component.translatable("standing.jaymoskingdoms.guard.hover", Rulers.guards(level, f, new AABB(f.seat()).inflate(Economy.POPULATION_RANGE))));
		if (f.leader().isPresent()) {
			int role = f.type() == FactionType.FIEFDOM ? Folk.LORD : Folk.KING;
			Component title = Rulers.title(role, Rulers.queen(f));
			PathfinderMob ruler = Rulers.of(level, f);
			lines.add(ruler != null ? Component.translatable("standing.jaymoskingdoms.sworn", title, ruler.getDisplayName())
				: Component.translatable("standing.jaymoskingdoms.sworn.away", title));
			Tempers.Temper t = Tempers.of(f);
			hovers.add(t == null ? Component.empty() : Component.translatable("standing.jaymoskingdoms.sworn.hover", temper(t)));
		}
		long time = Royals.timeOfDay(level);
		String duty;
		if (Arrests.busy(mob)) {
			duty = "prisoner";
		} else if (Royals.escortOf(w) != null) {
			duty = "escort";
		} else if (Retinue.sent(mob)) {
			duty = "stranger";
		} else if (Rulers.target(mob) != null) {
			duty = "fighting";
		} else if (mob.isSleeping()) {
			duty = "asleep";
		} else if (Rulers.offDuty(mob, w, time)) {
			duty = "off";
		} else {
			duty = "watch";
		}
		lines.add(Component.translatable("standing.jaymoskingdoms.duty", Component.translatable("standing.jaymoskingdoms.duty." + duty))
			.withStyle(ChatFormatting.WHITE));
		hovers.add(Component.translatable("standing.jaymoskingdoms.duty.hover"));
		return new Lines(List.copyOf(lines), List.copyOf(hovers));
	}

	/** "Temperament: Tyrant", coloured by how they treat people. */
	private static Component temper(Tempers.Temper t) {
		ChatFormatting colour = switch (t) {
			case TYRANT, PARANOID -> ChatFormatting.RED;
			case WARLORD, PURIST -> ChatFormatting.GOLD;
			case NOBLE, REVELER -> ChatFormatting.GREEN;
		};
		return Component.translatable("standing.jaymoskingdoms.temper", Component.translatable("standing.jaymoskingdoms.temper." + t.key())).withStyle(colour);
	}
}
