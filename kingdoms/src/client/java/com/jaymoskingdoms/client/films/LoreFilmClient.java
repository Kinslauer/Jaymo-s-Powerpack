package com.jaymoskingdoms.client.films;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.hud.HudZones;
import com.jaymoskingdoms.films.LoreFilms;
import com.jaymoskingdoms.inhabitants.Draugr;
import com.jaymoskingdoms.inhabitants.Inhabitants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A lore film ({@link LoreFilms}) played on this game alone: a story staged in front of you with actors only your game
 * sees, shot through letterbox bars with the HUD cleared and no words on screen (sounds only). Sneak held for a second
 * skips it.
 * <p>
 * <b>The Long Defence</b> (about 39 seconds), staged on the ground ahead of where you stand, as a memory: dim, and dark
 * between the flickers.
 * <ol>
 * <li>A line of hearthlanders at night, and behind them the five mages: Grimsby, Thessaly, Ambrose and Varro, each the
 * living man or woman their spirit was ({@code tools/gen_film_skins.py}), and a fifth in plain grey, a step back.</li>
 * <li>Far off across the field stands the sorcerer's living host, orcs and Warrior-born (in the draugr's gear, as they
 * were alive), and the sorcerer among them:
 * the Grue as it was, hooded, its eyes lit violet.</li>
 * <li>The sorcerer raises a hand and calls up the rest. Andy: "shadows rising from the ground like flame, flickering and
 * revealing the growing legion with every flicker". Black flame flares across the field; each flare lights it, and each
 * time more of the dead stand in it, risen out of the ground in front of the host: dead orcs and zombified piglins.</li>
 * <li>A horn. The dead come on, the host behind them, and meet the line; some of the hearthlanders fall. Grimsby, sword
 * in hand, throws bolts into them with the other.</li>
 * <li>Andy: "at least one of the heroes should be shown" in-balming an orc ({@code InBalming}). One orc of the host breaks
 * through the line at Varro; Varro beats it to its knees, sets his ki mark on its brow, and it gets up and turns on the
 * dead, fighting at the front for him until the rout.</li>
 * <li>The mages, each in turn, each in their own colour: Varro's fire, Thessaly's ward (a wall of light the dead are thrown
 * back from), Grimsby's spell-cut (glyphs round him, then one cut of light that runs the length of the field), Ambrose's
 * light (day, for a breath).</li>
 * <li>The fifth walks out alone through what's left. The dead fall as he passes. He faces the sorcerer, raises his hand,
 * and the sorcerer sinks into the ground in shadow. Nobody says who he was; he looks like none of the spirits.</li>
 * <li>The field, wide, and the line still standing. Fade.</li>
 * </ol>
 */
public final class LoreFilmClient {
	/** The film's beats (ticks): fade in, the reveal, the sorcerer, the clash, the spells, the fifth, the end. */
	static final int REVEAL = 50, SORCERER = 214, CLASH = 272, OVER_SHOULDER = 345, SPELLS = 422, CAST_TICKS = 36, ROUT = 566, QUIET = 572,
		FACE_OFF = 650, BANISH = 662, END = 708, LENGTH = LoreFilms.FILM_TICKS;
	/** The flickers: the first, how far apart, how many, and how long each lasts (ticks). */
	static final int FLICKER_FIRST = 62, FLICKER_EVERY = 25, FLICKERS = 6, FLICKER_TICKS = 8;
	/** Fading in and out (ticks), and how long sneak is held to skip. */
	static final int FADE = 20, SKIP_HOLD = 20;
	/** The staging, in blocks ahead of where you stood ({@code f}) and to the side ({@code s}). */
	static final double LINE_F = 14.0, MAGES_F = 10.0, STRANGER_F = 7.0, LEGION_NEAR = 28.0, LEGION_FAR = 40.0, LEGION_HALF = 13.0,
		SORCERER_F = 45.0, CLASH_F = 16.5;
	static final int SOLDIERS = 9, LEGION = 36;
	/** The living host (orcs and Warrior-born): how many, and where they stand, behind where the dead will rise. */
	static final int HOST = 16;
	/** Orcs are drawn bigger than a man (as BodySizes draws the real ones). */
	static final double ORC_SCALE = 1.12;
	/** The film's orcs' greens (as tools/gen_film_skins.py paints them), and the dead ones', for GreenskinLooks to tint by. */
	static final int[] ORC_GREENS = {0xFF7C8A62, 0xFF687854, 0xFF566646}, DEAD_GREENS = {0xFF5E6452, 0xFF545C48, 0xFF4E5444};
	static final double HOST_NEAR = 40.5, HOST_FAR = 43.5;
	/**
	 * Varro's marked orc: where it starts in the host (aside), how fast it charges (blocks a tick), and the beats: Varro
	 * beats it down, sets the mark, it rises; how often it strikes at the dead after (ticks), and how near they must be.
	 */
	static final double MARKED_S = 6.0, MARKED_RUN = 0.42, MARKED_KNEEL = 0.45, MARKED_REACH = 3.0;
	static final int MARK_HIT = 350, MARK_SET = 364, MARK_RISE = 388, MARKED_STRIKE = 9;
	/** How often Grimsby throws a bolt in the clash (ticks). */
	static final int BOLT_EVERY = 22;
	/** How far under the ground a hidden actor waits, and how fast actors walk (blocks a tick). */
	static final double HIDDEN = 3.2, WALK = 0.14, STRANGER_WALK = 0.27;
	/** The dark over the screen: the memory's dim, between flickers, and at a flicker's height. */
	static final float DIM = 0.32F, BETWEEN = 0.74F, FLARE = 0.12F;
	static final float BAR_SHARE = 0.11F;
	/** The vanilla HUD hidden while a film plays. */
	private static final Identifier[] HIDDEN_HUD = {VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS};

	enum Role {
		SOLDIER, MAGE, STRANGER, SORCERER, DEAD, HOST, MARKED
	}

	/** One actor: its entity, where it stands on the stage and where it's headed, how far under the ground, which flicker shows it. */
	static final class Actor {
		final LivingEntity e;
		final Role role;
		double f, s, toF, toS, homeF, homeS;
		double sink;
		int group;
		int fallAt = -1;
		boolean down;
		float yaw;

		Actor(LivingEntity e, Role role, double f, double s, float yaw) {
			this.e = e;
			this.role = role;
			this.f = this.toF = this.homeF = f;
			this.s = this.toS = this.homeS = s;
			this.yaw = yaw;
		}
	}

	/** A film playing: the stage (where you stood, which way you faced), how far in, and its actors. */
	static final class Film {
		final ClientLevel level;
		final Vec3 origin, fwd, right;
		final float yaw;
		final RandomSource rnd = RandomSource.create();
		final List<Actor> actors = new ArrayList<>();
		/** The dead and the living host: everything on the far side that the mages strike at. */
		final List<Actor> legion = new ArrayList<>();
		final List<Actor> soldiers = new ArrayList<>();
		/** Each orc actor → the green its orc look is tinted by ({@link #orcLook}). */
		final java.util.Map<LivingEntity, Integer> orcs = new java.util.IdentityHashMap<>();
		@Nullable Actor grimsby, thessaly, ambrose, varro, stranger, sorcerer, marked;
		@Nullable CameraType viewBefore;
		int tick, skip;
		float flash, flashO;

		Film(ClientLevel level, Vec3 origin, float yaw) {
			this.level = level;
			this.origin = origin;
			this.yaw = yaw;
			this.fwd = Vec3.directionFromRotation(0.0F, yaw);
			this.right = new Vec3(-this.fwd.z, 0.0, this.fwd.x);
		}

		/** The stage point {@code f} ahead and {@code s} aside, {@code up} over the ground there. */
		Vec3 at(double f, double s, double up) {
			double x = this.origin.x + this.fwd.x * f + this.right.x * s, z = this.origin.z + this.fwd.z * f + this.right.z * s;
			return new Vec3(x, ground(x, z) + up, z);
		}

		/** The ground's height at {@code x, z}, looked for near the height you stood at (so a cave or a house has a floor). */
		double ground(double x, double z) {
			int top = Mth.floor(this.origin.y) + 6;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), top, Mth.floor(z));
			boolean airAbove = false;
			for (int y = top; y > top - 20; y--) {
				pos.setY(y);
				boolean solid = !this.level.getBlockState(pos).getCollisionShape(this.level, pos).isEmpty();
				if (solid && airAbove) {
					return y + 1;
				}
				airAbove = !solid;
			}
			return this.origin.y;
		}
	}

	private static @Nullable Film film;
	private static int nextId = -1_700_000;
	private static float bars, barsO;

	private LoreFilmClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LoreFilms.Play.TYPE, (payload, context) -> context.client().execute(() -> start(context.client(), payload.film())));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!minecraft.isPaused()) {
				tick(minecraft);
			}
		});
		for (Identifier id : HIDDEN_HUD) {
			HudElementRegistry.replaceElement(id, old -> (graphics, delta) -> {
				if (film == null) {
					old.extractRenderState(graphics, delta);
				}
			});
		}
		HudElementRegistry.addLast(JaymosKingdoms.id("lore_film"), (HudElement)LoreFilmClient::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			film = null;
			bars = barsO = 0.0F;
		});
	}

	/** Whether a film is playing (movement keys don't answer). */
	public static boolean locked() {
		return film != null;
	}

	// ---- staging ------------------------------------------------------------------------------------------------------------

	private static void start(Minecraft minecraft, String name) {
		if (!LoreFilms.LONG_DEFENCE.equals(name) || minecraft.level == null || minecraft.player == null) {
			return;
		}
		stop(minecraft);
		Film f = new Film(minecraft.level, minecraft.player.position(), minecraft.player.getYRot());
		f.viewBefore = minecraft.options.getCameraType();
		film = f;
		float facing = f.yaw, facingBack = f.yaw + 180.0F;
		// the line: hearthlanders in iron, swords and shields
		for (int i = 0; i < SOLDIERS; i++) {
			double s = (i - (SOLDIERS - 1) / 2.0) * 2.0;
			LivingEntity e = person(f, null, false);
			if (e != null) {
				e.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				e.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				e.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
				e.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
				e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(i % 3 == 1 ? Items.IRON_AXE : Items.IRON_SWORD));
				if (i % 2 == 0) {
					e.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				}
				f.soldiers.add(cast(f, e, Role.SOLDIER, LINE_F + f.rnd.nextDouble() * 0.4, s + f.rnd.nextDouble() * 0.4 - 0.2, facing));
			}
		}
		// the four mages behind them, and the fifth a step back
		f.grimsby = mage(f, "grimsby", false, -6.0, Items.IRON_SWORD);
		f.thessaly = mage(f, "thessaly", true, -2.0, null);
		f.ambrose = mage(f, "ambrose", false, 2.0, Items.BLAZE_ROD);
		f.varro = mage(f, "varro", false, 6.0, Items.NETHERITE_AXE);
		if (f.varro != null) {
			f.varro.e.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			f.varro.e.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			f.varro.e.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		}
		LivingEntity quiet = person(f, "stranger", false);
		if (quiet != null) {
			f.stranger = cast(f, quiet, Role.STRANGER, STRANGER_F, 0.0, facing);
		}
		// the legion, under the ground until its flicker
		for (int i = 0; i < LEGION; i++) {
			LivingEntity e = dead(f, i);
			if (e == null) {
				continue;
			}
			double depth = LEGION_NEAR + f.rnd.nextDouble() * (LEGION_FAR - LEGION_NEAR);
			double s = (f.rnd.nextDouble() * 2.0 - 1.0) * LEGION_HALF * (0.6 + 0.4 * (depth - LEGION_NEAR) / (LEGION_FAR - LEGION_NEAR));
			Actor a = cast(f, e, Role.DEAD, depth, s, facingBack);
			a.sink = HIDDEN;
			a.group = i % FLICKERS;
			f.legion.add(a);
		}
		// the living host, standing from the start, a gap in the middle for the one they serve
		for (int i = 0; i < HOST; i++) {
			boolean orc = i % 5 < 3;
			LivingEntity e = orc ? orc(f, "orc_" + i % 3) : warriorBorn(f, i);
			if (e == null) {
				continue;
			}
			if (orc) {
				e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(i % 2 == 0 ? Items.IRON_AXE : Items.STONE_AXE));
			}
			double side = (i < HOST / 2 ? -1.0 : 1.0) * (2.5 + (i % (HOST / 2)) * (LEGION_HALF - 2.5) / (HOST / 2 - 1));
			Actor a = cast(f, e, Role.HOST, HOST_NEAR + f.rnd.nextDouble() * (HOST_FAR - HOST_NEAR), side + spread(f, 0.4), facingBack);
			a.group = -1;
			f.legion.add(a);
		}
		// the orc Varro will mark, in the host on his side
		LivingEntity brute = orc(f, "orc_0");
		if (brute != null) {
			brute.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			f.marked = cast(f, brute, Role.MARKED, HOST_FAR, MARKED_S + 1.5, facingBack);
		}
		LivingEntity grue = person(f, "sorcerer", false);
		if (grue != null) {
			f.sorcerer = cast(f, grue, Role.SORCERER, SORCERER_F, 0.0, facingBack);
		}
		sound(SoundEvents.AMBIENT_CAVE.value(), 0.7F, 0.5F);
	}

	private static @Nullable Actor mage(Film f, String skin, boolean slim, double s, @Nullable Item held) {
		LivingEntity e = person(f, skin, slim);
		if (e == null) {
			return null;
		}
		if (held != null) {
			e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(held));
		}
		return cast(f, e, Role.MAGE, MAGES_F, s, f.yaw);
	}

	/** A person (a mannequin of our own: no name over it), in {@code textures/entity/film/<skin>.png} or the plain default. */
	private static @Nullable LivingEntity person(Film f, @Nullable String skin, boolean slim) {
		LivingEntity e = EntityTypes.MANNEQUIN.create(f.level, EntitySpawnReason.LOAD);
		if (e == null) {
			return null;
		}
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("hide_description", true);
		if (skin != null) {
			CompoundTag profile = new CompoundTag();
			profile.putString("texture", JaymosKingdoms.id("entity/film/" + skin).toString());
			profile.putString("model", slim ? "slim" : "wide");
			tag.put("profile", profile);
		}
		try {
			e.load(TagValueInput.create(ProblemReporter.DISCARDING, f.level.registryAccess(), tag));
		} catch (RuntimeException ex) {
			JaymosKingdoms.LOGGER.debug("lore film actor {} kept its default look", skin, ex);
		}
		return e;
	}

	/** An orc (or a dead one): a mannequin in its skin, drawn bigger, and given the real orcs' look ({@link #orcLook}). */
	private static @Nullable LivingEntity orc(Film f, String skin) {
		LivingEntity e = person(f, skin, false);
		if (e != null) {
			var scale = e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
			if (scale != null) {
				scale.setBaseValue(ORC_SCALE);
			}
			int n = skin.charAt(skin.length() - 1) - '0';
			f.orcs.put(e, skin.startsWith("dead") ? DEAD_GREENS[n % DEAD_GREENS.length] : ORC_GREENS[n % ORC_GREENS.length]);
		}
		return e;
	}

	/**
	 * The green a lore film's orc is tinted by, for {@code GreenskinLooks} to draw the real orcs' tusks, brow, ears, marks
	 * and scars on it; 0 for anything else.
	 */
	public static int orcLook(net.minecraft.world.entity.Entity e) {
		Film f = film;
		if (f == null || e.getId() >= 0) {
			return 0;
		}
		Integer green = f.orcs.get(e);
		return green == null ? 0 : green;
	}

	/** A Warrior-born of the host: a draugr's helm, mail and fur, worn by the living man in better days (Andy). */
	private static @Nullable LivingEntity warriorBorn(Film f, int i) {
		Draugr e = Inhabitants.DRAUGR.create(f.level, EntitySpawnReason.LOAD);
		if (e != null) {
			e.stageLiving(i % 3 == 0);
		}
		return e;
	}

	/** One of the dead the sorcerer raises: dead orcs and zombified piglins, half and half. */
	private static @Nullable LivingEntity dead(Film f, int i) {
		if (i % 2 == 0) {
			LivingEntity e = orc(f, "dead_orc_" + (i / 2) % 3);
			if (e != null && i % 6 == 0) {
				e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
			}
			return e;
		}
		LivingEntity e = EntityTypes.ZOMBIFIED_PIGLIN.create(f.level, EntitySpawnReason.LOAD);
		if (e != null) {
			e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(i % 3 == 0 ? Items.IRON_SWORD : Items.GOLDEN_SWORD));
		}
		return e;
	}

	private static Actor cast(Film f, LivingEntity e, Role role, double fwd, double side, float yaw) {
		Actor a = new Actor(e, role, fwd, side, yaw);
		Vec3 at = f.at(fwd, side, -a.sink);
		e.setId(nextId--);
		e.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		e.setYHeadRot(yaw);
		e.setYBodyRot(yaw);
		f.level.addEntity(e);
		f.actors.add(a);
		return a;
	}

	private static void stop(Minecraft minecraft) {
		Film f = film;
		if (f == null) {
			return;
		}
		film = null;
		if (minecraft.level == f.level) {
			for (Actor a : f.actors) {
				f.level.removeEntity(a.e.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		if (f.viewBefore != null) {
			minecraft.options.setCameraType(f.viewBefore);
		}
	}

	// ---- the film, tick by tick ---------------------------------------------------------------------------------------------

	private static void tick(Minecraft minecraft) {
		barsO = bars;
		Film f = film;
		bars += ((f != null ? 1.0F : 0.0F) - bars) * 0.25F;
		if (f == null) {
			return;
		}
		if (minecraft.level != f.level || minecraft.player == null) {
			// a new level (a dimension change, a respawn): its actors went with the old one; give the camera back
			film = null;
			if (f.viewBefore != null) {
				minecraft.options.setCameraType(f.viewBefore);
			}
			return;
		}
		if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		}
		f.skip = minecraft.options.keyShift.isDown() ? f.skip + 1 : 0;
		if (f.skip == SKIP_HOLD && f.tick < LENGTH - FADE) {
			f.tick = LENGTH - FADE;
		}
		f.flashO = f.flash;
		f.flash *= 0.82F;
		int t = f.tick;
		beats(f, t);
		for (Actor a : f.actors) {
			move(f, a, t);
		}
		if (++f.tick >= LENGTH) {
			stop(minecraft);
		}
	}

	private static void beats(Film f, int t) {
		// the reveal: black flame flares, and with each more of the dead stand in it
		int flicker = flickerAt(t);
		if (t >= REVEAL && t < CLASH) {
			for (int i = 0; i < 3; i++) {
				shadowFlame(f, f.rnd.nextDouble() * (LEGION_FAR - LEGION_NEAR + 6.0) + LEGION_NEAR - 2.0, (f.rnd.nextDouble() * 2.0 - 1.0) * LEGION_HALF, 0.06);
			}
		}
		if (flicker >= 0) {
			int into = t - (FLICKER_FIRST + flicker * FLICKER_EVERY);
			for (int i = 0; i < 26; i++) {
				shadowFlame(f, f.rnd.nextDouble() * (LEGION_FAR - LEGION_NEAR + 6.0) + LEGION_NEAR - 2.0, (f.rnd.nextDouble() * 2.0 - 1.0) * LEGION_HALF, 0.22);
			}
			if (into == 0) {
				sound(SoundEvents.SOUL_ESCAPE.value(), 0.6F + flicker * 0.05F, 1.0F);
				sound(SoundEvents.WARDEN_HEARTBEAT, 0.8F, 0.9F);
				for (Actor a : f.legion) {
					if (a.group == flicker) {
						a.sink = HIDDEN * 0.75;
					}
				}
			}
		}
		// the sorcerer raises a hand, and the dark starts to come up
		if (t == REVEAL - 6 && f.sorcerer != null) {
			f.sorcerer.e.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			sound(SoundEvents.EVOKER_PREPARE_SUMMON, 0.6F, 0.9F);
		}
		// the last of them up, and the sorcerer behind them all
		if (t == SORCERER && f.sorcerer != null) {
			sound(SoundEvents.WITHER_SPAWN, 0.6F, 0.45F);
			f.flash = 0.0F;
		}
		if (t >= SORCERER && t < SORCERER + 30 && f.sorcerer != null) {
			for (int i = 0; i < 10; i++) {
				shadowFlame(f, SORCERER_F + f.rnd.nextDouble() * 3.0 - 1.5, f.rnd.nextDouble() * 3.0 - 1.5, 0.3);
			}
		}
		// the clash
		if (t == CLASH) {
			sound(SoundEvents.RAID_HORN.value(), 1.0F, 0.6F);
			for (Actor a : f.legion) {
				// the dead in front, the host pressing in behind them
				a.toF = a.role == Role.HOST ? CLASH_F + 5.5 + (a.homeF - HOST_NEAR) * 0.4 : CLASH_F + 1.0 + (a.homeF - LEGION_NEAR) * 0.3;
				a.toS = a.homeS * 0.75;
			}
			for (Actor a : f.soldiers) {
				a.toF = CLASH_F - 0.6;
			}
		}
		if (t > CLASH + 40 && t < SPELLS && t % 5 == 0) {
			Actor hitter = f.rnd.nextBoolean() ? alive(f, f.soldiers) : front(f);
			if (hitter != null) {
				hitter.e.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			}
			if (t % 10 == 0) {
				sound(f.rnd.nextBoolean() ? SoundEvents.ZOMBIE_ATTACK_IRON_DOOR : SoundEvents.PLAYER_ATTACK_STRONG, 0.8F + f.rnd.nextFloat() * 0.3F, 0.5F);
			}
			if (t % 15 == 0) {
				Actor d = front(f);
				if (d != null) {
					d.fallAt = t;
				}
			}
		}
		marking(f, t);
		// Grimsby, sword in one hand, throws bolts with the other
		if (t > CLASH + 30 && t < SPELLS && (t - CLASH) % BOLT_EVERY == 0 && f.grimsby != null) {
			Actor d = front(f);
			if (d != null) {
				bolt(f, f.grimsby, d);
			}
		}
		if ((t == CLASH + 60 || t == CLASH + 95 || t == CLASH + 125) && f.soldiers.size() > 7) {
			// three of the line fall: the outer ones, then one near the middle
			int[] which = {0, SOLDIERS - 1, SOLDIERS / 2 + 1};
			Actor down = f.soldiers.get(which[t == CLASH + 60 ? 0 : t == CLASH + 95 ? 1 : 2]);
			down.fallAt = t;
			sound(SoundEvents.SHIELD_BLOCK.value(), 0.7F, 0.6F);
		}
		// the spells, each mage in turn
		if (t >= SPELLS && t < ROUT) {
			int which = (t - SPELLS) / CAST_TICKS, into = (t - SPELLS) % CAST_TICKS;
			Actor mage = caster(f, which);
			if (mage != null) {
				spell(f, which, mage, into);
			}
		}
		if (t == ROUT) {
			for (Actor a : f.legion) {
				a.toF = a.f + 4.0;
			}
		}
		// the fifth
		if (t == QUIET && f.stranger != null) {
			// (from in front of the line, past the fallen: the cut hides the step)
			f.stranger.f = CLASH_F + 3.5;
			f.stranger.toF = SORCERER_F - 4.0;
			f.stranger.toS = 0.0;
		}
		if (t > QUIET && t < FACE_OFF && f.stranger != null) {
			for (Actor a : f.legion) {
				if (!a.down && a.fallAt < 0 && a.f < f.stranger.f + 6.0) {
					a.fallAt = t + f.rnd.nextInt(8);
				}
			}
		}
		if (t == BANISH && f.stranger != null) {
			f.stranger.e.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			sound(SoundEvents.SOUL_ESCAPE.value(), 0.5F, 1.0F);
			sound(SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 0.35F);
		}
		if (t >= BANISH && t < END && f.sorcerer != null) {
			f.sorcerer.sink = HIDDEN * (t - BANISH) / (double)(END - BANISH - 8);
			for (int i = 0; i < 8; i++) {
				shadowFlame(f, SORCERER_F + f.rnd.nextDouble() * 2.0 - 1.0, f.rnd.nextDouble() * 2.0 - 1.0, -0.05);
			}
		}
	}

	/** The mage casting {@code which} (0 Varro, 1 Thessaly, 2 Grimsby, 3 Ambrose). */
	private static @Nullable Actor caster(Film f, int which) {
		return switch (which) {
			case 0 -> f.varro;
			case 1 -> f.thessaly;
			case 2 -> f.grimsby;
			case 3 -> f.ambrose;
			default -> null;
		};
	}

	/** Where {@code which}'s spell lands. */
	private static Vec3 target(Film f, int which, Actor mage) {
		return switch (which) {
			case 0 -> f.at(CLASH_F + 5.0, mage.s, 0.5);
			case 1 -> f.at(CLASH_F, 0.0, 1.0);
			case 2 -> f.at(CLASH_F + 8.0, mage.s, 1.0);
			default -> f.at(CLASH_F + 8.0, 0.0, 0.0);
		};
	}

	/** Varro's marked orc: it charges him, is beaten down, marked on the brow, and rises to fight the dead for him. */
	private static void marking(Film f, int t) {
		Actor orc = f.marked, varro = f.varro;
		if (orc == null || varro == null) {
			return;
		}
		if (t == CLASH) {
			// through the line, straight at Varro
			orc.toF = MAGES_F + 1.6;
			orc.toS = varro.homeS;
		}
		if (t == MARK_HIT) {
			varro.e.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			orc.e.hurtTime = orc.e.hurtDuration = 10;
			orc.e.setPose(net.minecraft.world.entity.Pose.CROUCHING);
			orc.sink = MARKED_KNEEL;
			sound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.7F, 0.9F);
			sound(SoundEvents.ANVIL_LAND, 0.5F, 0.35F);
		}
		Vec3 brow = orc.e.position().add(0.0, 1.55 * ORC_SCALE - orc.sink, 0.0).subtract(f.fwd.scale(0.3));
		if (t == MARK_SET) {
			// his hand to its brow, and the ki mark burns there
			varro.e.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
			sound(SoundEvents.FIRECHARGE_USE, 0.7F, 0.8F);
			sound(SoundEvents.BEACON_POWER_SELECT, 0.6F, 0.8F);
			for (int i = 0; i < 24; i++) {
				double turn = i * Math.PI / 12.0;
				particle(f, i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.END_ROD,
					brow.add(Math.cos(turn) * 0.35, Math.sin(turn) * 0.35, 0.0), 0.0, 0.01, 0.0);
			}
		}
		if (t > MARK_SET && t < ROUT && t % 4 == 0) {
			particle(f, ParticleTypes.SMALL_FLAME, brow, 0.0, 0.005, 0.0);
		}
		if (t == MARK_RISE) {
			// up, and turned on the dead
			orc.e.setPose(net.minecraft.world.entity.Pose.STANDING);
			orc.sink = 0.0;
			orc.yaw = f.yaw;
			orc.toF = CLASH_F - 0.4;
			orc.toS = varro.homeS * 0.8;
			sound(SoundEvents.RAVAGER_ROAR, 0.9F, 0.7F);
		}
		if (t > MARK_RISE + 20 && t < ROUT && t % MARKED_STRIKE == 0) {
			orc.e.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			for (Actor d : f.legion) {
				if (!d.down && d.fallAt < 0 && d.sink <= 0.0 && Math.abs(d.f - orc.f) < MARKED_REACH && Math.abs(d.s - orc.s) < MARKED_REACH) {
					d.fallAt = t + 2;
					sound(SoundEvents.PLAYER_ATTACK_STRONG, 0.7F, 0.5F);
					break;
				}
			}
		}
	}

	/** Grimsby's bolt: off his free hand, a streak of light into {@code at}, which falls. */
	private static void bolt(Film f, Actor mage, Actor at) {
		mage.e.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		sound(SoundEvents.EVOKER_CAST_SPELL, 1.3F, 0.6F);
		Vec3 from = mage.e.position().add(0.0, 1.4, 0.0).add(f.right.scale(-0.4)), to = at.e.position().add(0.0, 1.1, 0.0);
		for (int i = 0; i <= 24; i++) {
			Vec3 p = from.lerp(to, i / 24.0);
			particle(f, i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ENCHANTED_HIT, p, 0.0, 0.0, 0.0);
		}
		at.fallAt = f.tick + 2;
	}

	private static void spell(Film f, int which, Actor mage, int into) {
		if (which == 2 && into < 12) {
			// Grimsby: glyphs gather and turn about him as he calls it up, his free hand raised
			if (into == 2) {
				mage.e.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
				sound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 0.8F);
			}
			for (int i = 0; i < 6; i++) {
				double turn = (into * 0.5 + i * Math.PI / 3.0);
				Vec3 p = mage.e.position().add(Math.cos(turn) * 1.3, 0.3 + into * 0.12, Math.sin(turn) * 1.3);
				particle(f, ParticleTypes.ENCHANT, p, 0.0, 0.0, 0.0);
				particle(f, ParticleTypes.ENCHANTED_HIT, p, 0.0, 0.02, 0.0);
			}
		}
		if (into == 10) {
			mage.e.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
		if (into < 12) {
			return;
		}
		Vec3 aim = target(f, which, mage);
		switch (which) {
			case 0 -> {
				// Varro: fire, thrown into the thick of them
				if (into == 12) {
					sound(SoundEvents.BLAZE_SHOOT, 0.8F, 0.9F);
					sound(SoundEvents.GENERIC_EXPLODE.value(), 1.1F, 0.6F);
					for (int i = 0; i < 70; i++) {
						particle(f, i % 6 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, aim.add(spread(f, 2.5), f.rnd.nextDouble(), spread(f, 2.5)), spread(f, 0.25),
							f.rnd.nextDouble() * 0.25, spread(f, 0.25));
					}
					fell(f, aim, 4.5, 0);
				}
			}
			case 1 -> {
				// Thessaly: a wall of light across the front, and the dead thrown back from it
				if (into == 12) {
					sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.0F);
					sound(SoundEvents.BEACON_ACTIVATE, 1.4F, 0.7F);
					for (Actor a : f.legion) {
						if (!a.down && a.f < CLASH_F + 4.0) {
							a.toF = a.f + 3.5;
						}
					}
				}
				if (into < 30) {
					for (int i = 0; i < 18; i++) {
						Vec3 p = f.at(CLASH_F - 0.2, (f.rnd.nextDouble() * 2.0 - 1.0) * LEGION_HALF, f.rnd.nextDouble() * 3.0);
						particle(f, i % 3 == 0 ? ParticleTypes.ENCHANT : ParticleTypes.END_ROD, p, 0.0, 0.04, 0.0);
					}
				}
			}
			case 2 -> {
				// Grimsby: the spell let go down his blade, one cut of light that runs the length of the field
				int run = into - 12;
				if (run == 0) {
					sound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.0F);
					sound(SoundEvents.EVOKER_CAST_SPELL, 0.8F, 0.9F);
				}
				if (run < 14) {
					double along = CLASH_F + run * (LEGION_FAR + 2.0 - CLASH_F) / 14.0;
					Vec3 p = f.at(along, mage.s, 1.2);
					particle(f, ParticleTypes.SWEEP_ATTACK, p, 0.0, 0.0, 0.0);
					for (int i = 0; i < 4; i++) {
						particle(f, ParticleTypes.CRIT, p.add(spread(f, 1.0), spread(f, 0.5), spread(f, 1.0)), 0.0, 0.05, 0.0);
						particle(f, ParticleTypes.ENCHANTED_HIT, p.add(spread(f, 1.2), spread(f, 0.8), spread(f, 1.2)), 0.0, 0.03, 0.0);
					}
					for (Actor a : f.legion) {
						if (!a.down && a.fallAt < 0 && Math.abs(a.f - along) < 1.6 && Math.abs(a.s - mage.s) < 2.6) {
							a.fallAt = f.tick;
						}
					}
				}
			}
			default -> {
				// Ambrose: day, for a breath
				if (into == 12) {
					sound(SoundEvents.TRIDENT_THUNDER.value(), 1.2F, 0.5F);
					sound(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.0F);
					f.flash = 0.75F;
					fell(f, aim, 8.0, 6);
				}
				if (into < 24) {
					for (int i = 0; i < 20; i++) {
						particle(f, ParticleTypes.END_ROD, aim.add(spread(f, 2.0), f.rnd.nextDouble() * 14.0, spread(f, 2.0)), 0.0, -0.3, 0.0);
					}
				}
			}
		}
	}

	/** The dead within {@code reach} of {@code at} fall, over {@code spreadTicks}. */
	private static void fell(Film f, Vec3 at, double reach, int spreadTicks) {
		for (Actor a : f.legion) {
			if (!a.down && a.fallAt < 0 && a.e.position().distanceToSqr(at.x, a.e.getY(), at.z) < reach * reach) {
				a.fallAt = f.tick + (spreadTicks > 0 ? f.rnd.nextInt(spreadTicks) : 0);
			}
		}
	}

	/** Moves one actor toward where it's headed, rises or sinks it, and drops it when its time comes. */
	private static void move(Film f, Actor a, int t) {
		LivingEntity e = a.e;
		if (a.fallAt >= 0 && t >= a.fallAt && !a.down) {
			a.down = true;
			e.hurtTime = e.hurtDuration = 10;
			e.setHealth(0.0F);
			if (a.role == Role.DEAD) {
				particle(f, ParticleTypes.LARGE_SMOKE, e.position().add(0.0, 0.8, 0.0), 0.0, 0.05, 0.0);
			}
		}
		if (!a.down) {
			double df = a.toF - a.f, ds = a.toS - a.s, d = Math.sqrt(df * df + ds * ds);
			double speed = a.role == Role.STRANGER ? STRANGER_WALK : a.role == Role.MARKED && f.tick < MARK_HIT ? MARKED_RUN : WALK;
			if (d > 0.05) {
				double step = Math.min(d, speed);
				a.f += df / d * step;
				a.s += ds / d * step;
			}
		}
		// rising out of the ground at its flicker (the dead), or as the film began (everyone else)
		if (a.role == Role.DEAD && a.sink > 0.0 && a.sink < HIDDEN) {
			a.sink = Math.max(0.0, a.sink - 0.2);
		}
		Vec3 at = f.at(a.f, a.s, -a.sink);
		e.setPos(at.x, at.y, at.z);
		float yaw = a.yaw;
		if (a.role == Role.STRANGER && f.tick >= QUIET) {
			yaw = f.yaw;
		}
		e.setYRot(yaw);
		e.setYHeadRot(yaw);
		e.setYBodyRot(yaw);
		e.setRemainingFireTicks(0);
		e.calculateEntityAnimation(false);
		if (a.role == Role.SORCERER && a.sink < HIDDEN && f.tick % 3 == 0) {
			particle(f, ParticleTypes.WITCH, e.position().add(spread(f, 0.6), 1.2 + f.rnd.nextDouble(), spread(f, 0.6)), 0.0, 0.02, 0.0);
		}
		if (a.role == Role.SORCERER && f.tick >= END) {
			a.sink = HIDDEN + 2.0;
		}
	}

	private static int flickerAt(int t) {
		if (t < FLICKER_FIRST) {
			return -1;
		}
		int k = (t - FLICKER_FIRST) / FLICKER_EVERY;
		return k < FLICKERS && (t - FLICKER_FIRST) % FLICKER_EVERY < FLICKER_TICKS ? k : -1;
	}

	private static @Nullable Actor alive(Film f, List<Actor> from) {
		List<Actor> up = from.stream().filter(a -> !a.down).toList();
		return up.isEmpty() ? null : up.get(f.rnd.nextInt(up.size()));
	}

	/** One of the dead in the front rank, still up. */
	private static @Nullable Actor front(Film f) {
		List<Actor> up = f.legion.stream().filter(a -> !a.down && a.fallAt < 0 && a.f < CLASH_F + 3.5 && a.sink <= 0.0).toList();
		return up.isEmpty() ? null : up.get(f.rnd.nextInt(up.size()));
	}

	/** Black flame: ink and smoke licking up out of the ground at {@code (fwd, side)}. */
	private static void shadowFlame(Film f, double fwd, double side, double rise) {
		Vec3 p = f.at(fwd, side, 0.1);
		particle(f, f.rnd.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SQUID_INK, p, spread(f, 0.03), rise + f.rnd.nextDouble() * 0.08, spread(f, 0.03));
		if (f.rnd.nextInt(9) == 0) {
			particle(f, ParticleTypes.SOUL_FIRE_FLAME, p, 0.0, rise * 0.4, 0.0);
		}
	}

	private static void particle(Film f, ParticleOptions type, Vec3 p, double vx, double vy, double vz) {
		f.level.addParticle(type, true, true, p.x, p.y, p.z, vx, vy, vz);
	}

	private static double spread(Film f, double by) {
		return (f.rnd.nextDouble() * 2.0 - 1.0) * by;
	}

	private static void sound(SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	// ---- the camera ---------------------------------------------------------------------------------------------------------

	/** Where the camera is and which way it looks this frame ({x, y, z, yaw, pitch}), or null when no film is playing. */
	public static double @Nullable [] view(Minecraft minecraft, float partialTicks) {
		Film f = film;
		if (f == null || minecraft.player == null || minecraft.getCameraEntity() != minecraft.player || minecraft.level != f.level) {
			return null;
		}
		float t = Math.min(f.tick + partialTicks, LENGTH);
		Vec3 pos;
		Vec3 look;
		if (t < REVEAL) {
			// along the line's backs, the dark field beyond
			float k = smooth(t / REVEAL);
			pos = f.at(LINE_F - 2.4, Mth.lerp(k, -7.0, -2.5), 1.5);
			look = f.at(HOST_NEAR, 2.0, 1.4);
		} else if (t < SORCERER) {
			// over the mages' heads, out at the field as it flickers
			float k = smooth((t - REVEAL) / (SORCERER - REVEAL));
			pos = f.at(MAGES_F - 3.0 + 2.5 * k, 1.0, 2.6);
			look = f.at(LEGION_NEAR + 5.0, 0.0, 0.8);
		} else if (t < CLASH) {
			// low among the dead, through the gap in the host, up at the one behind them
			float k = smooth((t - SORCERER) / (CLASH - SORCERER));
			pos = f.at(SORCERER_F - 7.5 + 1.5 * k, 1.2, 0.9);
			look = f.sorcerer != null ? f.sorcerer.e.getPosition(partialTicks).add(0.0, 1.6, 0.0) : f.at(SORCERER_F, 0.0, 1.6);
		} else if (t < OVER_SHOULDER) {
			// off the flank as they meet
			float k = smooth((t - CLASH) / (OVER_SHOULDER - CLASH));
			pos = f.at(CLASH_F + 1.0, -LEGION_HALF - 5.0 + 3.0 * k, 2.2);
			look = f.at(CLASH_F + 1.0, 0.0, 1.0);
		} else if (t < SPELLS) {
			// over Varro's shoulder, down at the orc he marks, then out at the field as it turns on the dead
			Vec3 m = f.varro != null ? f.varro.e.getPosition(partialTicks) : f.at(MAGES_F, 6.0, 0.0);
			pos = m.subtract(f.fwd.scale(1.6)).add(f.right.scale(0.7)).add(0.0, 2.0, 0.0);
			look = f.marked != null && t < MARK_RISE + 8 ? f.marked.e.getPosition(partialTicks).add(0.0, 1.2, 0.0) : f.at(CLASH_F + 2.0, 3.0, 1.0);
		} else if (t < ROUT) {
			int which = (int)((t - SPELLS) / CAST_TICKS);
			float into = (t - SPELLS) - which * CAST_TICKS;
			Actor mage = caster(f, which);
			Vec3 m = mage != null ? mage.e.getPosition(partialTicks) : f.at(MAGES_F, 0.0, 0.0);
			if (into < 12) {
				// their face as they call it up
				double side = which % 2 == 0 ? 1.1 : -1.1;
				pos = m.add(f.fwd.scale(2.6)).add(f.right.scale(side)).add(0.0, 1.6, 0.0);
				look = m.add(0.0, 1.55, 0.0);
			} else {
				// and over their shoulder as it lands
				pos = m.subtract(f.fwd.scale(1.8)).add(f.right.scale(0.8)).add(0.0, 2.0, 0.0);
				look = mage != null ? target(f, which, mage) : f.at(CLASH_F + 6.0, 0.0, 0.0);
			}
		} else if (t < QUIET) {
			pos = f.at(MAGES_F - 6.0, 0.0, 6.0);
			look = f.at(LEGION_NEAR, 0.0, 0.0);
		} else if (t < FACE_OFF) {
			// behind the fifth as he walks out
			Vec3 q = f.stranger != null ? f.stranger.e.getPosition(partialTicks) : f.at(STRANGER_F, 0.0, 0.0);
			pos = q.subtract(f.fwd.scale(3.2)).add(f.right.scale(0.6)).add(0.0, 2.0, 0.0);
			look = q.add(f.fwd.scale(6.0)).add(0.0, 1.4, 0.0);
		} else if (t < END) {
			// side on, the two of them
			Vec3 q = f.stranger != null ? f.stranger.e.getPosition(partialTicks) : f.at(SORCERER_F - 4.0, 0.0, 0.0);
			Vec3 g = f.at(SORCERER_F, 0.0, 0.0);
			Vec3 mid = q.add(g).scale(0.5);
			float k = smooth((t - FACE_OFF) / (END - FACE_OFF));
			pos = mid.add(f.right.scale(7.0 - 1.5 * k)).add(0.0, 1.6, 0.0);
			look = mid.add(0.0, 1.3, 0.0);
		} else {
			// the field, wide, drawing up and away
			float k = smooth((t - END) / (LENGTH - END));
			pos = f.at(LINE_F - 8.0 - 2.0 * k, 0.0, 3.0 + 4.0 * k);
			look = f.at(LINE_F + 8.0, 0.0, 0.0);
		}
		Vec3 dir = look.subtract(pos);
		dir = dir.lengthSqr() < 1.0E-6 ? f.fwd : dir.normalize();
		double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z));
		double pitch = Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1.0, 1.0)));
		return new double[] {pos.x, pos.y, pos.z, yaw, pitch};
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	// ---- the screen ---------------------------------------------------------------------------------------------------------

	/** The bars, the dark (dim as a memory, darker between flickers), Ambrose's light, and the fades. */
	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float shown = Mth.lerp(partial, barsO, bars);
		if (shown <= 0.01F) {
			return;
		}
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		Film f = film;
		if (f != null) {
			HudZones.hideFor(100);
			float t = f.tick + partial;
			float dark = DIM;
			if (t >= REVEAL && t < CLASH) {
				dark = BETWEEN;
				int flicker = flickerAt(f.tick);
				if (flicker >= 0) {
					float into = t - (FLICKER_FIRST + flicker * FLICKER_EVERY);
					dark = Mth.lerp(Mth.sin(Mth.clamp(into / FLICKER_TICKS, 0.0F, 1.0F) * Mth.PI), BETWEEN, FLARE);
				} else if (t >= SORCERER) {
					dark = Mth.lerp(smooth((t - SORCERER) / 20.0F), BETWEEN, DIM + 0.1F);
				}
			}
			float fade = Math.max(1.0F - t / FADE, (t - (LENGTH - FADE)) / FADE);
			dark = Math.max(dark, Mth.clamp(fade, 0.0F, 1.0F));
			graphics.fill(0, 0, width, height, (Math.round(dark * 255.0F) << 24) | 0x05060C);
			float light = Mth.lerp(partial, f.flashO, f.flash);
			if (light > 0.01F) {
				graphics.fill(0, 0, width, height, (Math.round(light * 255.0F) << 24) | 0xFFF6DC);
			}
		}
		int bar = Math.round(height * BAR_SHARE * shown);
		graphics.fill(0, 0, width, bar, 0xFF000000);
		graphics.fill(0, height - bar, width, height, 0xFF000000);
	}
}
