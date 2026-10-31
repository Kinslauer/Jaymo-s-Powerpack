package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.319) Andy (2026-10-28): "Mad tinkerer, Hermit, all z fighter npcs need MCA villager equivalents. it destroys
 * immersion seeing one of those things instead of a humanoid ... they should have context sensitive talk interactions
 * that handle the quest objectives instead of them appearing in chat like now."
 * <p>
 * Jaymo's Powers' Z Fighter characters (the Keeper of the Lookout, the Tower Hermit, the Mad Tinkerer, the Judge, the
 * Master of the Road and the Fortune Teller of the Other World, the three demon masters) were wandering traders frozen in
 * place. With MCA they're MCA villagers instead ({@link #spawn}, called by Powers' {@code ZFighterNpcs} by reflection):
 * grown, in an outfit of ours for their part ({@link Uniforms#zfighter}), named for it, out of MCA's family tree, never
 * hurt, and held at their post (their brain waits, {@link #step}: they turn to whoever's near and step back if pushed).
 * They're enlisted to no settlement in {@link #ROLE}, so no town takes them on, and they're kept off every roster.
 * <p>
 * Their work is "Talk about work" in MCA's menu: Powers' {@code ZFighterNpcs.talk} opens a talk screen ({@link #ask})
 * with what they have for you (a blessing, a lesson, his wares, the operation, the way back to the living), and every
 * answer comes back on the screen as their words, never in chat. A town's Z Fighter masters (villagers of their town)
 * get "Talk about training" ({@link #TRAINING}) for their lessons and the sagas.
 */
public final class ZFighterFolk {
	/** Their role ({@link Worker#role}): none of a settlement's. */
	public static final int ROLE = 95;
	/** MCA's main menu: "Talk about training", on a town's Z Fighter master. */
	public static final String TRAINING = "jaymoskingdoms_training";
	/** How far they may be pushed from their post before they step back (blocks), and how far they look for someone to face. */
	static final double POST_SLACK = 1.5, LOOK_RANGE = 8.0;

	/** Powers' Omen pilots (the future's mech pilots): their tag and kind. They roam and watch on Powers' own say, in MCA's own clothes. */
	static final String PILOT_TAG = "jaymospowers.omen_pilot", PILOT_KIND = "omen_pilot";
	/** (0.7.359) Powers' galactic patrolmen: they come and go in their pods, roam and fight on Powers' say, in the patrol's uniform. */
	static final String PATROL_TAG = "jaymospowers.patrolman";
	/** (0.7.446) Powers' band hunter (and the same man, bandless, running): Powers walks him to his prey and away. */
	static final String HUNTER_TAG = "jaymospowers_band_hunter", HUNTER_FLED_TAG = "jaymospowers_band_hunter_fled";

	/** Powers' tags for its characters, and the outfits each wears ({@code skins/clothing/zfighter/<kind>_<n>}). */
	static final Map<String, String> KINDS = Map.ofEntries(
		Map.entry("jaymospowers_keeper", "keeper"),
		Map.entry("jaymospowers_hermit", "hermit"),
		Map.entry("jaymospowers_scientist", "tinkerer"),
		Map.entry("jaymospowers_judge", "judge"),
		Map.entry("jaymospowers_afterlife_master", "road_master"),
		Map.entry("jaymospowers_fortune_teller", "teller"),
		Map.entry("jaymospowers_demon_master", "demon_master"),
		// (0.7.348) Andy: an Omen's pilot, out of its mech in Powers' future, a curious villager (Powers moves and talks for them)
		Map.entry(PILOT_TAG, PILOT_KIND),
		Map.entry(PATROL_TAG, "patrolman"),
		// (0.7.442) Powers' star-readers, who come to a fallen star's crater and keep to its rim
		Map.entry("jaymospowers_star_reader", "star_reader"),
		// (0.7.444) Powers' star forge smith, who keeps the forge on a far world and reforges Xeno-Bands
		Map.entry("jaymospowers_star_smith", "star_smith"),
		// (0.7.446) Powers' band hunter, an outcast with the rival band who hunts Xeno-Band wearers (and runs once it's torn off)
		Map.entry(HUNTER_TAG, "band_hunter"),
		Map.entry(HUNTER_FLED_TAG, "band_hunter"));
	/**
	 * (0.7.405) Andy: the Judge's MCA screen said "Jobless" and "Mood: Depressed". Which character they are ({@link #KINDS}),
	 * synced so MCA's screen shows their part and bearing in place of a trade and a mood ({@code McaZFighterInfoMixin}).
	 * Set by {@link #step}, so it needs no saving.
	 */
	public static final AttachmentType<String> PART = AttachmentRegistry.create(JaymosKingdoms.id("zfighter_part"),
		builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	/** A town's (or a camp's) Z Fighter masters: their own villagers, picked by Powers. */
	static final Set<String> MASTERS = Set.of("jaymospowers_master", "jaymospowers_dark_master");

	/** A talk's menu: who's talking, and what picking each answer does. */
	private record Menu(int speaker, IntConsumer pick) {
	}

	private static final Map<UUID, Menu> MENUS = new HashMap<>();

	private ZFighterFolk() {
	}

	static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MENUS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MENUS.clear());
	}

	/** {@code e} is one of Powers' Z Fighter characters (as an MCA villager). */
	public static boolean is(@Nullable Entity e) {
		return e != null && !(e instanceof Folk) && kind(e) != null;
	}

	/** Which character {@code e} is ({@link #KINDS}), or null. */
	static @Nullable String kind(Entity e) {
		for (String tag : e.entityTags()) {
			String kind = KINDS.get(tag);
			if (kind != null) {
				return kind;
			}
		}
		return null;
	}

	/** {@code e} is a town's (or a camp's) Z Fighter master. */
	public static boolean trains(@Nullable Entity e) {
		if (e == null) {
			return false;
		}
		for (String tag : e.entityTags()) {
			if (MASTERS.contains(tag)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Called by Powers (reflection): a new grown MCA villager at {@code at} facing {@code yaw}, tagged {@code tag} (one of
	 * {@link #KINDS}), named {@code name}, a woman if {@code female}; held at that spot. Null without MCA.
	 */
	public static @Nullable LivingEntity spawn(ServerLevel level, Vec3 at, float yaw, String tag, Component name, boolean female) {
		if (!McaCompat.LOADED) {
			return null;
		}
		return McaBridge.zfighter(level, at, yaw, tag, name.getString(), female, "hermit".equals(KINDS.get(tag)) || "tinkerer".equals(KINDS.get(tag)));
	}

	/**
	 * Their step in place of their brain ({@link Workers#step}): at their post, facing whoever is talking to them or the
	 * nearest player. Always true: they never wander, work, sleep or go home.
	 */
	static boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		String kind = kind(mob);
		if (kind != null && !kind.equals(mob.getAttached(PART))) {
			mob.setAttached(PART, kind);
		}
		if (mob.entityTags().contains(PILOT_TAG) || mob.entityTags().contains(PATROL_TAG) || mob.entityTags().contains(HUNTER_TAG)
			|| mob.entityTags().contains(HUNTER_FLED_TAG)) {
			// (0.7.348) an Omen's pilot: Powers walks them to whoever they're curious about, and has them look
			// ((0.7.359) a galactic patrolman: Powers walks them round the town and into fights)
			// ((0.7.446) the band hunter: Powers walks him down his prey, and off once he's beaten)
			return true;
		}
		mob.getNavigation().stop();
		if (mob.hasHome() && mob.distanceToSqr(Vec3.atBottomCenterOf(mob.getHomePosition())) > POST_SLACK * POST_SLACK && !mob.isPassenger()) {
			Vec3 post = Vec3.atBottomCenterOf(mob.getHomePosition());
			mob.teleportTo(post.x, post.y, post.z);
		}
		Player near = level.getNearestPlayer(mob, LOOK_RANGE);
		if (near != null) {
			mob.getLookControl().setLookAt(near, 30.0F, 30.0F);
		}
		return true;
	}

	/** (0.7.405) Their part on MCA's screen, in place of a trade ("The Judge of the Dead"). */
	public static Component part(String kind) {
		return Component.translatable("zfighter.jaymoskingdoms.part." + kind);
	}

	/** (0.7.405) Their bearing on MCA's screen, in place of a mood ("Bearing: Stern"). */
	public static Component bearing(String kind) {
		return Component.translatable("zfighter.jaymoskingdoms.bearing", Component.translatable("zfighter.jaymoskingdoms.bearing." + kind));
	}

	/** Their outfits ({@link Uniforms#zfighter}), or null for anyone else. */
	static @Nullable List<String> outfits(Entity e) {
		String kind = kind(e);
		// (an Omen's pilot keeps MCA's own clothes: no outfits of ours)
		return kind == null ? null : PILOT_KIND.equals(kind) ? List.of() : Uniforms.zfighter(kind);
	}

	/** "Talk about work" with one of them: Powers' talk. */
	public static void talk(ServerPlayer player, LivingEntity speaker) {
		if (!PowersCompat.zfighterTalk(player, speaker)) {
			Speech.tell(player.level(), speaker, player, Component.translatable("talk.jaymoskingdoms.zfighter.nothing"), Speech.WHITE);
		}
	}

	/**
	 * Called by Powers (reflection): {@code speaker} says {@code words} on the talk screen, with these answers (greyed out
	 * where not {@code enabled}); the answer picked goes to {@code pick} (-1: the screen was shut). Without Kingdoms on the
	 * client, the words float over their head.
	 */
	public static void ask(ServerPlayer player, LivingEntity speaker, Component words, List<Component> options, List<Boolean> enabled, IntConsumer pick) {
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			Speech.tell(player.level(), speaker, player, words, Speech.WHITE);
			return;
		}
		MENUS.put(player.getUUID(), new Menu(speaker.getId(), pick));
		ServerPlayNetworking.send(player, new TalkNet.Ask(speaker.getId(), speaker.getDisplayName(), words, List.copyOf(options), List.copyOf(enabled)));
	}

	/** An answer to one of their talks; false when it wasn't one ({@link Approaches}). */
	static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		Menu menu = MENUS.get(player.getUUID());
		if (menu == null || menu.speaker() != answer.entityId()) {
			return false;
		}
		MENUS.remove(player.getUUID());
		Entity speaker = player.level().getEntity(answer.entityId());
		if (answer.option() < 0 || speaker == null || !speaker.isAlive() || speaker.distanceTo(player) > Careers.TALK_RANGE) {
			return true;
		}
		menu.pick().accept(answer.option());
		return true;
	}
}
