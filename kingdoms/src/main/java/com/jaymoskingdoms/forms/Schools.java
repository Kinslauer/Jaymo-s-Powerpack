package com.jaymoskingdoms.forms;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Post;
import com.jaymoskingdoms.Speech;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.384) The fighting schools (Weapon Forms, patch 4). One school for each weapon family ({@link School}), each kept by a
 * <b>master</b> in a realm's training hall ({@code SchoolHalls}; an MCA villager, held at the post). Which school a realm
 * teaches is fixed by its id ({@link #localSchool}). A player joins one school free; a second only once they have mastered
 * every school they joined ({@link #join}). Joining hands over the school's manual (a written book of odd sayings).
 * <ul>
 * <li><b>Standing</b> in a school grows with the practice of that school's forms ({@link #practiced}, called by
 * {@link Forms#practice}; a class with a matching school gains {@value #CLASS_BONUS} more each time) and, in Patch 5, with
 * duels ({@link #addStanding}). The master teaches one more of the school's arts on each visit once the standing reaches
 * its step ({@link #ART_STANDING}).</li>
 * <li><b>Mastery</b> ({@link #openMasterstroke}): with every art learnt and {@value #MASTERY_STANDING} standing, the master
 * opens the school's masterstroke form (blade: Rain of Cuts, spear: Pinning Throw; the fist, knife and claw schools have none
 * yet, and it only marks them mastered), and hands over the school's cosmetic mask and a Master-grade blueprint for its
 * signature weapon (forged through Tinkers; the fist school has none). Patch 5's duel with the master calls the same method.</li>
 * </ul>
 * Only a master's kindly <i>words</i> are shown: nothing here states lore on a screen, and the manuals only hint.
 */
public final class Schools {
	// ---- tuning ----
	/** The Worker role of a school master (none of a settlement's: {@code ZFighterFolk.ROLE} is 95). */
	public static final int ROLE = 96;
	/** Standing needed for the nth art a school teaches (0 = on joining), and for the masterstroke. */
	static final int[] ART_STANDING = {0, 20, 45};
	static final int MASTERY_STANDING = 150;
	/** The most standing a school gives. */
	static final int STANDING_CAP = 600;
	/** Standing per point of practice, and extra standing per practice for a class with its matching school. */
	static final int PRACTICE_STANDING = 1, CLASS_BONUS = 1;
	/** A Master-grade blueprint's xp (Powers' {@code Blueprints.MASTER_XP}); the signature weapon's shape and its blueprint item, by id. */
	static final int BLUEPRINT_XP = 40;
	static final String BLUEPRINT_ITEM = "jaymospowers:blueprint", BLUEPRINT_DATA = "jaymospowers:blueprint_data";
	/** How far from a master you may talk to them (blocks). */
	static final double TALK_RANGE = 8.0;
	/** How far a realm's seat may be from a spot for the realm to teach there (blocks). */
	static final double REALM_RANGE = 160.0;
	/** Scoreboard tags on a master: the mark, then the school's key, then the realm's id. */
	public static final String MASTER_TAG = "jaymoskingdoms_school_master", SCHOOL_TAG_PREFIX = "jaymoskingdoms_school_",
		REALM_TAG_PREFIX = "jaymoskingdoms_school_realm_";
	/** The MCA {@code main} menu answer (our dialogue data): "Speak of the school". */
	public static final String ANSWER = "jaymoskingdoms_school";

	/** The five schools, one for each weapon family. */
	public enum School {
		BLADE(Forms.Family.BLADE, "weapon/katana_blade", "rain_of_cuts", "sheath_draw", "crescent_sweep"),
		SPEAR(Forms.Family.SPEAR, "weapon/spear_tip", "pinning_throw", "spear_lunge", "couched_lance"),
		FIST(Forms.Family.FIST, null, null, "ground_stomp", "falling_fist"),
		KNIFE(Forms.Family.KNIFE, "weapon/kunai_blade", null, "marked_quarry", "cast_off_cloak", "tangle_cord"),
		CLAW(Forms.Family.CLAW, "weapon/fang_blades", null, "rending_claw", "feral_pounce");

		private final Forms.Family family;
		private final @Nullable String weapon;
		private final @Nullable String masterstroke;
		private final List<String> arts;

		School(Forms.Family family, @Nullable String weapon, @Nullable String masterstroke, String... arts) {
			this.family = family;
			this.weapon = weapon;
			this.masterstroke = masterstroke;
			this.arts = List.of(arts);
		}

		public Forms.Family family() {
			return this.family;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The ids of the arts this school teaches, in order. */
		public List<String> arts() {
			return this.arts;
		}

		/** The id of this school's masterstroke form, or null. */
		public @Nullable String masterstroke() {
			return this.masterstroke;
		}

		/** The signature weapon's blueprint shape (a Powers forging shape), or null: the school has none. */
		@Nullable String weapon() {
			return this.weapon;
		}

		public Component displayName() {
			return Component.translatable("school.jaymoskingdoms." + key());
		}

		public static @Nullable School byKey(String key) {
			for (School s : values()) {
				if (s.key().equals(key)) {
					return s;
				}
			}
			return null;
		}
	}

	/** A player's schools: the standing in each ({@code key} → standing), the schools joined (in order) and those mastered. */
	public record State(Map<String, Integer> standing, List<String> joined, List<String> mastered) {
		public static final State NONE = new State(Map.of(), List.of(), List.of());
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("standing", Map.of()).forGetter(State::standing),
				Codec.STRING.listOf().optionalFieldOf("joined", List.of()).forGetter(State::joined),
				Codec.STRING.listOf().optionalFieldOf("mastered", List.of()).forGetter(State::mastered))
			.apply(i, State::new));

		State withStanding(School s, int value) {
			Map<String, Integer> next = new HashMap<>(this.standing);
			next.put(s.key(), value);
			return new State(Map.copyOf(next), this.joined, this.mastered);
		}

		State withJoined(School s) {
			List<String> next = new ArrayList<>(this.joined);
			next.add(s.key());
			return new State(this.standing, List.copyOf(next), this.mastered);
		}

		State withMastered(School s) {
			List<String> next = new ArrayList<>(this.mastered);
			next.add(s.key());
			return new State(this.standing, this.joined, List.copyOf(next));
		}
	}

	/** On a player: their schools. Saved, kept through death (not synced: the masters' talk is all server side). */
	public static final AttachmentType<State> SCHOOLS = AttachmentRegistry.create(JaymosKingdoms.id("schools"),
		builder -> builder.persistent(State.CODEC).copyOnDeath());

	/** The cosmetic masks, by school (worn on the head; one is given at mastery). */
	private static final Map<School, Item> MASKS = new HashMap<>();

	private Schools() {
	}

	public static void register() {
		for (School s : School.values()) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("school_mask_" + s.key()));
			Equippable worn = Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
				.setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id("school_mask_" + s.key()))).build();
			MASKS.put(s, Registry.register(BuiltInRegistries.ITEM, key,
				new Item(new Item.Properties().setId(key).stacksTo(1).component(DataComponents.EQUIPPABLE, worn))));
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> MASKS.values().forEach(output::accept));
	}

	// ---- the public interface (Patch 5 and the masters' talk) ----------------------------------------------------------

	private static State state(Player player) {
		return player.getAttachedOrElse(SCHOOLS, State.NONE);
	}

	/** The schools {@code p} has joined, in the order they joined them. */
	public static List<School> joined(Player p) {
		List<School> list = new ArrayList<>();
		for (String key : state(p).joined()) {
			School s = School.byKey(key);
			if (s != null) {
				list.add(s);
			}
		}
		return list;
	}

	/** {@code s} is mastered by {@code p}. */
	public static boolean mastered(Player p, School s) {
		return state(p).mastered().contains(s.key());
	}

	/**
	 * Joins {@code p} to {@code s}: the first school is free; another only once every school they've joined is mastered. False
	 * if they've joined it already or may not yet. Hands over the school's manual and teaches its first art.
	 */
	public static boolean join(ServerPlayer p, School s) {
		State state = state(p);
		if (state.joined().contains(s.key())) {
			return false;
		}
		for (School other : joined(p)) {
			if (!mastered(p, other)) {
				return false;
			}
		}
		p.setAttached(SCHOOLS, state.withJoined(s).withStanding(s, 0));
		give(p, manual(s));
		if (!s.arts().isEmpty()) {
			Forms.teach(p, s.arts().get(0));
		}
		return true;
	}

	/** {@code p}'s standing in {@code s} (0 for a school they haven't joined). */
	public static int standing(Player p, School s) {
		return state(p).standing().getOrDefault(s.key(), 0);
	}

	/** Adds {@code amount} to {@code p}'s standing in {@code s}, if they've joined it (at most {@value #STANDING_CAP}). */
	public static void addStanding(ServerPlayer p, School s, int amount) {
		State state = state(p);
		if (amount == 0 || !state.joined().contains(s.key())) {
			return;
		}
		int now = Math.clamp((long)state.standing().getOrDefault(s.key(), 0) + amount, 0, STANDING_CAP);
		p.setAttached(SCHOOLS, state.withStanding(s, now));
	}

	/** {@code s}'s masterstroke is open to {@code p} (a school without one: they've mastered it). */
	public static boolean masterstrokeOpen(Player p, School s) {
		return mastered(p, s);
	}

	/**
	 * {@code p} has mastered {@code s} (once only; if they hadn't joined it, they have now): its masterstroke form is taught, and the school's
	 * mask and its signature weapon's blueprint are theirs. Called by the master's talk at {@value #MASTERY_STANDING} standing,
	 * and by Patch 5's duel with a master.
	 */
	public static void openMasterstroke(ServerPlayer p, School s) {
		State state = state(p);
		if (state.mastered().contains(s.key())) {
			return;
		}
		// (one who beats a master in a duel without having joined is counted as a student of the school from here)
		p.setAttached(SCHOOLS, (state.joined().contains(s.key()) ? state : state.withJoined(s)).withMastered(s));
		String stroke = s.masterstroke();
		if (stroke != null) {
			Forms.teach(p, stroke);
		}
		give(p, new ItemStack(MASKS.get(s)));
		if (p.level() instanceof ServerLevel level) {
			ItemStack blueprint = blueprint(level, s);
			if (!blueprint.isEmpty()) {
				give(p, blueprint);
			}
		}
		Post.tidings(p.getUUID(), Component.translatable("tidings.jaymoskingdoms.school.mastered", s.displayName()));
	}

	/** The school a realm near {@code pos} teaches (the nearest standing realm's, within {@value #REALM_RANGE} blocks), or null. */
	public static @Nullable School localSchool(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		if (level != Factions.level(server)) {
			// (the realms are the Overworld's)
			return null;
		}
		Faction here = Factions.at(server, level, pos);
		if (here == null || !here.type().isRealm() || here.fallen()) {
			here = null;
			double best = REALM_RANGE * REALM_RANGE;
			for (Faction f : Factions.all(server)) {
				double d = f.seat().distSqr(pos);
				if (f.type().isRealm() && !f.fallen() && d <= best) {
					here = f;
					best = d;
				}
			}
		}
		return here == null ? null : ofRealm(here.id());
	}

	/** The school the realm with this id teaches (fixed by its id, so every run of a world agrees). */
	public static School ofRealm(int factionId) {
		School[] all = School.values();
		return all[Math.floorMod(factionId * 0x9E3779B1 + 0x7F4A7C15, all.length)];
	}

	/** {@code e} is a school master. */
	public static boolean isMaster(Entity e) {
		return e != null && e.entityTags().contains(MASTER_TAG);
	}

	/** The school {@code e} keeps, or null if they aren't a master. */
	public static @Nullable School masterOf(Entity e) {
		if (!isMaster(e)) {
			return null;
		}
		for (School s : School.values()) {
			if (e.entityTags().contains(SCHOOL_TAG_PREFIX + s.key())) {
				return s;
			}
		}
		return null;
	}

	// ---- standing from practice -----------------------------------------------------------------------------------------

	/** A form of {@code family} was practised for {@code amount} (from {@link Forms#practice}): standing in the joined school of that family. */
	static void practiced(ServerPlayer player, Forms.Family family, int amount) {
		for (School s : joined(player)) {
			if (s.family() == family) {
				addStanding(player, s, amount * PRACTICE_STANDING + (matchesClass(player, s) ? CLASS_BONUS : 0));
			}
		}
	}

	/** A class with its matching school: Warlord and blade, Brute and fist, Thief and knife, Rider and spear. */
	static boolean matchesClass(Player player, School s) {
		Classes.PlayerClass cls = Classes.classOf(player);
		return cls != null && switch (s) {
			case BLADE -> cls == Classes.PlayerClass.WARLORD;
			case FIST -> cls == Classes.PlayerClass.BRUTE;
			case KNIFE -> cls == Classes.PlayerClass.THIEF;
			case SPEAR -> cls == Classes.PlayerClass.RIDER;
			case CLAW -> false;
		};
	}

	// ---- the master's step -----------------------------------------------------------------------------------------------

	/** How far a master may be pushed from their post before they go back (blocks), and how far they look for someone to face. */
	static final double POST_SLACK = 1.5, LOOK_RANGE = 8.0;

	/** A master's step in place of their brain ({@code Workers.step}): at their post, facing whoever is near. Always true. */
	public static boolean step(ServerLevel level, PathfinderMob mob) {
		mob.getNavigation().stop();
		if (mob.tickCount % 200 == 0 && realmGone(level, mob)) {
			// the realm fell: its master leaves the ruins
			mob.discard();
			return true;
		}
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

	/** Whether the realm a master was posted by (their {@link #REALM_TAG_PREFIX} tag) is gone or fallen. */
	private static boolean realmGone(ServerLevel level, PathfinderMob mob) {
		for (String t : mob.entityTags()) {
			if (t.startsWith(REALM_TAG_PREFIX)) {
				try {
					com.jaymoskingdoms.Faction f = com.jaymoskingdoms.Factions.get(level.getServer(), Integer.parseInt(t.substring(REALM_TAG_PREFIX.length())));
					return f == null || f.fallen();
				} catch (NumberFormatException e) {
					return false;
				}
			}
		}
		return false;
	}

	// ---- the talk --------------------------------------------------------------------------------------------------------

	/** Whether "Speak of the school" shows on {@code who}: a school master, in reach of you. */
	public static boolean canTalk(ServerPlayer player, LivingEntity who) {
		return isMaster(who) && who.isAlive();
	}

	/**
	 * "Speak of the school" ({@link #ANSWER}): not joined, you join (if you may; a second school needs the first mastered); joined,
	 * the master teaches the next art your standing earns, opens the masterstroke when it's time, or says how you stand, by
	 * hints. Spoken in bubbles, never chat.
	 */
	public static void talk(ServerPlayer player, LivingEntity master) {
		School s = masterOf(master);
		if (s == null || !(player.level() instanceof ServerLevel level) || master.distanceTo(player) > TALK_RANGE) {
			return;
		}
		if (!joined(player).contains(s)) {
			if (join(player, s)) {
				say(level, master, player, "school.jaymoskingdoms.welcome", player.getDisplayName(), s.displayName());
				say(level, master, player, "school.jaymoskingdoms." + s.key() + ".welcome");
				Post.tidings(player.getUUID(), Component.translatable("tidings.jaymoskingdoms.school.joined", master.getDisplayName(), s.displayName()));
			} else {
				say(level, master, player, "school.jaymoskingdoms.one_house", player.getDisplayName());
			}
			return;
		}
		int standing = standing(player, s);
		List<String> arts = s.arts();
		for (int i = 0; i < arts.size(); i++) {
			String art = arts.get(i);
			if (Forms.knows(player, art)) {
				continue;
			}
			if (standing >= ART_STANDING[Math.min(i, ART_STANDING.length - 1)] && Forms.teach(player, art)) {
				say(level, master, player, "school.jaymoskingdoms.taught", player.getDisplayName(), formName(art));
				return;
			}
			say(level, master, player, "school.jaymoskingdoms.wait", player.getDisplayName());
			return;
		}
		if (mastered(player, s)) {
			say(level, master, player, "school.jaymoskingdoms.after", player.getDisplayName());
		} else if (standing >= MASTERY_STANDING) {
			openMasterstroke(player, s);
			say(level, master, player, "school.jaymoskingdoms.master", player.getDisplayName(), s.displayName());
			say(level, master, player, "school.jaymoskingdoms." + s.key() + ".mastery");
		} else {
			say(level, master, player, "school.jaymoskingdoms.onward", player.getDisplayName());
		}
	}

	private static Component formName(String id) {
		Forms.Form form = Forms.get(id);
		return form == null ? Component.literal(id) : form.name();
	}

	/** The master says one of three variants of {@code key}. */
	private static void say(ServerLevel level, LivingEntity master, ServerPlayer player, String key, Object... args) {
		Speech.tell(level, master, player, Component.translatable(key + "." + level.getRandom().nextInt(3), args), Speech.GOLD);
	}

	// ---- what a school gives -----------------------------------------------------------------------------------------------

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	/** Founders (the manuals' authors) and the manuals' titles, by school. */
	private static String author(School s) {
		return switch (s) {
			case BLADE -> "Maro Vell";
			case SPEAR -> "Ysolt Ar";
			case FIST -> "Brann Hollowstone";
			case KNIFE -> "Tamsin Quill";
			case CLAW -> "Kesh of the Deep";
		};
	}

	private static String title(School s) {
		return switch (s) {
			case BLADE -> "Sayings of the Quiet Tide";
			case SPEAR -> "Sayings of the Skyward Reach";
			case FIST -> "Sayings of the Stone Root";
			case KNIFE -> "Sayings of the Low Lantern";
			case CLAW -> "Sayings of the Ashen Fang";
		};
	}

	/** A school's manual: a written book of odd sayings (the pages are lang keys, so each reader sees their own tongue). */
	static ItemStack manual(School s) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> pages = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			pages.add(Filterable.passThrough(Component.translatable("book.jaymoskingdoms.school." + s.key() + "." + i)));
		}
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title(s)), author(s), 0, pages, true));
		return book;
	}

	/**
	 * A Master-grade Tinkers blueprint for {@code s}'s signature weapon, made by item id and component, so Kingdoms never needs
	 * Powers: empty if Powers isn't loaded or the school has none (the fist school: no cestus is forged).
	 */
	static ItemStack blueprint(ServerLevel level, School s) {
		String shape = s.weapon();
		if (shape == null || !BuiltInRegistries.ITEM.containsKey(Identifier.parse(BLUEPRINT_ITEM))) {
			return ItemStack.EMPTY;
		}
		if (s == School.BLADE && !BuiltInRegistries.ITEM.containsKey(Identifier.parse("weaponmod:katana"))) {
			// no Balkon's katana to forge: the ninjato it is
			shape = "weapon/ninjato_blade";
		}
		JsonObject data = new JsonObject();
		data.addProperty("shape", shape);
		data.addProperty("xp", BLUEPRINT_XP);
		JsonObject name = new JsonObject();
		name.addProperty("translate", "item.jaymospowers.blueprint.named");
		JsonArray with = new JsonArray();
		JsonObject part = new JsonObject();
		part.addProperty("translate", "item.jaymospowers." + shape.substring(shape.indexOf('/') + 1));
		with.add(part);
		name.add("with", with);
		JsonObject components = new JsonObject();
		components.add(BLUEPRINT_DATA, data);
		components.add("minecraft:item_name", name);
		JsonObject stack = new JsonObject();
		stack.addProperty("id", BLUEPRINT_ITEM);
		stack.addProperty("count", 1);
		stack.add("components", components);
		return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), stack).result().orElse(ItemStack.EMPTY);
	}
}
