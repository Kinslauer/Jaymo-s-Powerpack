package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebSymbiote;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The suit doesn't just appear: the symbiote builds it over you. First its tendrils leech out of the chest and wrap the
 * body (the torso, then the arms and head, then the legs), coiling round each part ({@link WebSuitWrapLayer}); then it
 * folds over the body in layers along them, each part covered from its joint outward: raw goo first, then the suit
 * itself rolling over it, its edge a thick band of tendril. Taken off, it all goes back the other way. The big body is
 * built the same way, larger, in the order of the film's transformation (Andy's clip): thick tendrils lash round the
 * chest and arms, then the legs, the head last, and the goo bursts out after them over the chest and arms, streams over
 * the shoulders and down the legs and swallows the head, hugging the body like the plain suit's goo; then every mass
 * swells out into the brute and you grow inside it ({@link WebHulk.Model#build}), doubled over in pain until you rise
 * (see WebFeral). Everyone sees it.
 */
public final class WebSuitGrowth {
	/** How long putting the suit on (or off) takes (ticks); during a roar it pieces itself together quicker. */
	static final float TICKS = 36.0F, ROAR_TICKS = 26.0F;
	/** Building the big body takes longer: it doubles over in pain as it grows (see WebFeral), and the bulk swells gradually (was 60). */
	static final float BULK_TICKS = 72.0F;
	/**
	 * Body, arms, head, legs: when each part's tendrils start out (share of the change). They take {@code WRAP_SPAN} to wrap
	 * it, the goo layer starts {@code GOO_AFTER} after the tendrils do and takes {@code LAYER_SPAN}, the suit follows it
	 * {@code SUIT_AFTER} later.
	 */
	static final float BODY = 0.0F, ARMS = 0.1F, HEAD = 0.16F, LEGS = 0.22F;
	static final float WRAP_SPAN = 0.3F, GOO_AFTER = 0.28F, SUIT_AFTER = 0.14F, LAYER_SPAN = 0.26F;
	/**
	 * Building (or shedding) the big body, the film's order instead (the head swallowed last): when each part's tendrils
	 * start out, body, arms, legs, head; they, and the plain suit's goo and suit under it (put on over bare skin), go on
	 * at {@code BIG_PACE} times the suit's spans (the whole change takes longer, BULK_TICKS). Its thick tendrils sink
	 * into the goo flowing over them {@code BIG_SINK_AFTER} after they start out, over {@code BIG_SINK_SPAN}.
	 */
	static final float BIG_BODY = 0.0F, BIG_ARMS = 0.04F, BIG_LEGS = 0.14F, BIG_HEAD = 0.24F, BIG_PACE = 0.6F;
	static final float BIG_SINK_AFTER = 0.16F, BIG_SINK_SPAN = 0.2F;
	/**
	 * Fighting a takeover's hold (the struggle bar, {@link WebSymbiote#STRUGGLE}): you tear your way out of it as the bar
	 * fills and it pours back over you as it drains. The big body comes apart from the last piece built (the head and
	 * jaws, the claws, the hands and forearms) down to {@code HULK_PEEL} of it at a full bar; under it the suit peels back
	 * off each part toward its joint (body, right arm, left arm, head, right leg, left leg), starting at
	 * {@code PEEL_START} of the bar, over {@code PEEL_SPAN}, at most {@code PEEL_MAX} of the part: the face first, then the
	 * arms, then the chest, the legs hardly at all. It never sits still: it tugs back and forth by up to
	 * {@code TUG_WOBBLE} (more the further out you are), {@code TUG_SPEED} radians a tick.
	 */
	static final float HULK_PEEL = 0.55F;
	static final float[] PEEL_START = {0.45F, 0.2F, 0.25F, 0.0F, 0.6F, 0.6F};
	static final float[] PEEL_MAX = {0.6F, 0.85F, 0.85F, 1.0F, 0.35F, 0.35F};
	static final float PEEL_SPAN = 0.45F, TUG_WOBBLE = 0.09F, TUG_SPEED = 0.85F;
	/**
	 * It fights you for the suit as you take it off ({@link com.jaymospowers.power.web.WebSymbioteBond#FIGHT}): the
	 * tear-off ({@link WebSuitTear}) is held at {@code FIGHT_TEAR_MIN} of the way through (clawing at your chest) and
	 * driven up to {@code FIGHT_TEAR_MAX} (the mask off your face, the arms wrenching) by the struggle bar, back and forth
	 * as it tugs. Won, only the face is bare ({@code WebSymbioteBond.FACE}): the suit's head is gone, and when the time's
	 * up it flows back up over the face over {@code FACE_FLOW_TICKS}. Whatever bares the face (that, the mask taken off
	 * from the menu, a takeover's), the mask peels off it over {@code FACE_PEEL_TICKS} first (Andy: "the mask should
	 * always animate peeling, on or off"), and flows back the same way; switched mid-way, it turns from where it is.
	 */
	static final float FIGHT_TEAR_MIN = 0.06F, FIGHT_TEAR_MAX = 0.3F, FACE_FLOW_TICKS = 16.0F, FACE_PEEL_TICKS = 14.0F;
	/**
	 * When the fight ends with the suit still on (won to a bare face, or lost), the tear-off it was held at settles back
	 * over {@code FIGHT_SETTLE_TICKS} (the suit flowing back over the chest and arms) instead of popping back.
	 */
	static final float FIGHT_SETTLE_TICKS = 10.0F;

	/** What each of the suit layer's models is. */
	enum Role {
		/** The suit itself (and the veins over it). */
		SUIT,
		/** The raw goo under it, laid first. */
		GOO,
		/** The big body. */
		HULK
	}

	/**
	 * On a player's render state while their suit is changing. {@code from}, {@code to}: 0 bare, 1 suit, 2 Living Armor.
	 * {@code tear}: it's coming off by being torn away ({@link WebSuitTear}), not withdrawing.
	 */
	public record Growth(int from, int to, float t, boolean tear) {
	}

	public static final RenderStateDataKey<Growth> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_suit_growth");
	/** On a player's render state while they fight a takeover's hold: how far out of it they've torn (0-1, tugging), else null. */
	/**
	 * (0.13.4) How far the goo has pooled up the legs from the feet (0-1) while it braces for a deadly landing
	 * ({@link com.jaymospowers.power.web.WebSymbioteCushion}), or null. Only drawn bare of the suit (the suit takes no
	 * fall damage).
	 */
	public static final RenderStateDataKey<Float> CUSHION = RenderStateDataKey.create(() -> "jaymospowers:web_suit_cushion");
	/** How much wider than the leg the goo pools at the foot, and how hard its surface ripples. */
	static final float CUSHION_SPREAD = 0.22F, CUSHION_RIPPLE = 0.06F;
	public static final RenderStateDataKey<Float> PULL = RenderStateDataKey.create(() -> "jaymospowers:web_suit_pull");
	/** On a player's render state while only their face is bare (or the suit flowing back over it): how bare (0-1), else null. */
	public static final RenderStateDataKey<Float> FACE = RenderStateDataKey.create(() -> "jaymospowers:web_suit_face");

	/** The suit layer's own models (only these are grown), and what each one is. */
	private static final Map<Model<?>, Role> MODELS = new WeakHashMap<>();

	/** entity id → {shown level, level before the change, tick it changed, 1 if a takeover put it on (quicker)}. */
	private static final Map<Integer, long[]> CHANGES = new HashMap<>();
	/** Per player whose mask is peeling off, off, or flowing back: which way, when it last turned, and how bare the face was then. */
	private static final Map<Integer, Peel> FACES = new HashMap<>();

	private static final class Peel {
		boolean bare;
		long turned;
		float from;
	}
	/** Players fighting it for the suit (or settling after): how far the tear-off was, and when the fight ended (-1: still on). */
	private static final Map<Integer, Settle> FIGHTS = new HashMap<>();

	private static final class Settle {
		float t;
		long ended = -1L;
	}

	private WebSuitGrowth() {
	}

	static void register(Model<?> model, Role role) {
		MODELS.put(model, role);
	}

	/** What one of the suit layer's models is (null: any other model). */
	static @org.jspecify.annotations.Nullable Role role(Model<?> model) {
		return MODELS.get(model);
	}

	/** 0 bare, 1 the suit, 2 the big body. */
	static int level(Player player) {
		return WebSymbiote.bulked(player) ? 2 : WebSymbiote.suitShown(player) ? 1 : 0;
	}

	/** While extracting a player's render state: notes a change, and puts the one under way on the state. */
	static void extract(Player player, AvatarRenderState state, float partialTicks) {
		int now = level(player);
		state.setData(CUSHION, cushion(player, partialTicks));
		long[] change = CHANGES.get(player.getId());
		// game time, not the player's own age: a respawned player's age starts over, which would hold a change
		// (the suit falling away at death) part-done until it caught up
		long tick = player.level().getGameTime();
		if (change == null) {
			// first seen: as it is, no growing
			CHANGES.put(player.getId(), new long[] {now, now, tick - 1000, 0});
			state.setData(KEY, null);
			state.setData(PULL, null);
			state.setData(FACE, null);
			return;
		}
		boolean seized = WebSymbioteClient.seized(player);
		if (change[0] != now) {
			change[1] = change[0];
			change[0] = now;
			change[2] = tick;
			change[3] = 0;
		}
		if (seized && change[0] > change[1]) {
			// it's seizing you (not being put on): it engulfs you SELF_TAKEOVER_SPEED times as fast
			change[3] = 1;
		}
		// torn off (WebSymbiote.TORN set as it came off): the Spider-Man 3 tear-off instead of the plain withdraw
		Long torn = player.getAttached(WebSymbiote.TORN);
		boolean tear = change[0] == 0 && change[1] >= 1 && torn != null && Math.abs(torn - change[2]) <= WebSuitTear.TORN_MATCH_TICKS;
		float span = tear ? WebSuitTear.TEAR_TICKS : change[0] == 2 && change[1] < 2 ? BULK_TICKS : WebSymbiote.roaring(player) ? ROAR_TICKS : TICKS;
		if (change[3] == 1 && !tear) {
			span /= WebSymbiote.SELF_TAKEOVER_SPEED;
		}
		float t = (tick - change[2] + partialTicks) / span;
		float share = seized ? WebSymbioteClient.struggleShare(player) : 0.0F;
		if (share > 0.0F) {
			// back and forth: it never quite lets you go, nor you it
			float age = player.tickCount + partialTicks;
			float tug = (Mth.sin(age * TUG_SPEED) * 0.6F + Mth.sin(age * TUG_SPEED * 2.7F + 1.3F) * 0.4F) * TUG_WOBBLE * (0.4F + share);
			state.setData(PULL, Mth.clamp(share + tug, 0.0F, 1.0F));
		} else {
			state.setData(PULL, null);
		}
		state.setData(KEY, t < 1.0F && change[1] != change[0] ? new Growth((int)change[1], (int)change[0], Math.max(0.0F, t), tear) : null);
		if (now == 1 && com.jaymospowers.power.web.WebSymbioteBond.fighting(player)) {
			// it fights you for it: the tear-off, driven back and forth by the struggle bar (see FIGHT_TEAR_MIN)
			Float pull = state.getData(PULL);
			Settle fight = FIGHTS.computeIfAbsent(player.getId(), id -> new Settle());
			fight.t = Mth.lerp(pull == null ? 0.0F : pull, FIGHT_TEAR_MIN, FIGHT_TEAR_MAX);
			fight.ended = -1L;
			state.setData(KEY, new Growth(1, 0, fight.t, true));
		} else {
			Settle fight = FIGHTS.get(player.getId());
			if (fight != null) {
				if (fight.ended < 0L) {
					fight.ended = tick;
				}
				float left = 1.0F - (tick - fight.ended + partialTicks) / FIGHT_SETTLE_TICKS;
				if (now != 1 || state.getData(KEY) != null || left <= 0.0F) {
					// settled (or a real change has it now: the tear-off, a takeover's big body)
					FIGHTS.remove(player.getId());
				} else {
					// the fight's over and the suit stays: it flows back over the chest and arms, no pop
					state.setData(KEY, new Growth(1, 0, fight.t * ease(left), true));
				}
			}
		}
		state.setData(FACE, face(player, now, tick, partialTicks));
		if (CHANGES.size() > 256) {
			CHANGES.keySet().removeIf(id -> Minecraft.getInstance().level == null || Minecraft.getInstance().level.getEntity(id) == null);
		}
		if (FIGHTS.size() > 64) {
			FIGHTS.clear();
		}
	}

	/** How far the goo has pooled up the legs (see {@link #CUSHION}): up over the pool, back down as it drains. */
	private static @org.jspecify.annotations.Nullable Float cushion(Player player, float partialTicks) {
		Long mark = player.getAttached(com.jaymospowers.power.web.WebSymbioteCushion.CUSHION);
		if (mark == null || level(player) != 0) {
			return null;
		}
		float age = player.level().getGameTime() + partialTicks;
		float cover = mark > 0L
			? ease((age - mark) / com.jaymospowers.power.web.WebSymbioteCushion.POOL_TICKS)
			: 1.0F - ease((age + mark) / com.jaymospowers.power.web.WebSymbioteCushion.DRAIN_TICKS);
		return cover > 0.01F ? cover : null;
	}

	/** How bare this player's face is (1 bare, falling as the suit flows back over it), or null (see {@link #FACE}). */
	private static @org.jspecify.annotations.Nullable Float face(Player player, int level, long tick, float partialTicks) {
		int id = player.getId();
		if (level != 1) {
			FACES.remove(id);
			return null;
		}
		// (0.13.23) or its mask taken off from the menu (WebSymbiote.UNMASKED): the face shows till it's put back
		boolean bare = com.jaymospowers.power.web.WebSymbioteBond.faceBare(player) || WebSymbiote.unmasked(player);
		Peel peel = FACES.get(id);
		if (peel == null) {
			if (!bare) {
				return null;
			}
			// the mask starts peeling off
			peel = new Peel();
			peel.bare = true;
			peel.turned = tick;
			peel.from = 0.0F;
			FACES.put(id, peel);
		} else if (peel.bare != bare) {
			// turned the other way, from wherever it had got to
			peel.from = bareness(peel, tick, partialTicks);
			peel.bare = bare;
			peel.turned = tick;
		}
		float face = bareness(peel, tick, partialTicks);
		if (!bare && face <= 0.0F) {
			FACES.remove(id);
			return null;
		}
		return face;
	}

	/** How bare the face is now along its peel (off over {@code FACE_PEEL_TICKS}, back over {@code FACE_FLOW_TICKS}). */
	private static float bareness(Peel peel, long tick, float partialTicks) {
		float since = tick - peel.turned + partialTicks;
		return peel.bare ? Math.min(1.0F, peel.from + since / FACE_PEEL_TICKS) : Math.max(0.0F, peel.from - since / FACE_FLOW_TICKS);
	}

	/**
	 * After a model is posed: every model drawn for the big body moves out to its frame ({@link WebHulk#shift}); the suit
	 * layer's own models are laid on as far as they've got (and no head on your own, in first person).
	 */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar)) {
			return;
		}
		if (model.getClass().getName().startsWith(MCA_MODELS)) {
			// (MCA's body goes under the suit whole, so it must come back whenever the suit doesn't cover it all: torn at,
			// fought, peeled in a takeover's hold. That was the missing body under the fight: Andy's pack draws players
			// with MCA's model, and all of it was hidden while the suit was being torn off it.)
			mcaUnder(model, covered(avatar) && !bared(avatar), faceShown(avatar));
			return;
		}
		float k = hulkness(avatar);
		if (model instanceof net.minecraft.client.model.HumanoidModel<?> humanoid) {
			WebHulk.shift(humanoid, k);
		}
		if (!(model instanceof PlayerModel player)) {
			return;
		}
		Role role = MODELS.get(model);
		if (role == null) {
			Float pooled = avatar.getData(CUSHION);
			if (pooled != null && pooled > 0.3F) {
				// (the trousers' outer layer would poke through the goo pooled over the legs)
				player.leftPants.visible = false;
				player.rightPants.visible = false;
			}
			// the wearer's own body: the skin's outer layer (hat, jacket, sleeves, trousers) sits further out than the
			// suit, and would poke through it in patches, so it goes under the suit while the suit covers it all
			if (covered(avatar)) {
				// (the face bare: its outer layer shows with it)
				Float face = avatar.getData(FACE);
				if (face == null || face < 0.5F) {
					player.hat.visible = false;
				}
				if (!bared(avatar)) {
					// (torn at or peeled, the chest shows, its jacket with it)
					player.jacket.visible = false;
				}
				player.leftSleeve.visible = false;
				player.rightSleeve.visible = false;
				player.leftPants.visible = false;
				player.rightPants.visible = false;
			}
			return;
		}
		if (role == Role.HULK) {
			WebHulk.Model hulk = (WebHulk.Model)model;
			hulk.build(hulkBuilt(avatar), peeledHulk(avatar), k, avatar.ageInTicks, WebHulk.teeth(avatar));
			hulk.breathe(avatar);
			// elbows and knees bending with the stride (after the limbs are posed above)
			hulk.limbs(avatar);
		} else {
			lay(player, avatar, role);
			// (laid under the big body as it's built or peeled: stretched to its longer frame)
			WebHulk.stretch(player, k, avatar.ageInTicks);
		}
		if (WebSymbioteClient.ownFirstPerson(avatar)) {
			player.head.visible = false;
		}
	}

	/** Moves a model drawn outside the usual passes (a layer reading the body's parts) out to the big body's frame. */
	static void shift(net.minecraft.client.model.HumanoidModel<?> model, AvatarRenderState state) {
		WebHulk.shift(model, hulkness(state));
	}

	/** How far into the big body's frame the body is spread (0 not at all, 1 the whole way): it grows as the goo swells. */
	static float hulkness(AvatarRenderState avatar) {
		Growth growth = avatar.getData(KEY);
		if (growth != null && Math.max(growth.from(), growth.to()) == 2) {
			return WebHulk.frame(built(growth));
		}
		return bigBody(avatar) ? 1.0F : 0.0F;
	}

	/**
	 * (0.15.152) The big body is on this player: with its tendrils out ({@link WebSymbioteClient.Tendrils#armor}) or
	 * drawn in ({@link WebSymbioteClient#TENDRILS_IN}, worn alone, upright).
	 */
	public static boolean bigBody(AvatarRenderState avatar) {
		WebSymbioteClient.Tendrils tendrils = avatar.getData(WebSymbioteClient.TENDRILS);
		return tendrils != null && tendrils.armor() || Boolean.TRUE.equals(avatar.getData(WebSymbioteClient.TENDRILS_IN));
	}

	/** How far the big body is built (0-1). */
	static float hulkBuilt(AvatarRenderState avatar) {
		Growth growth = avatar.getData(KEY);
		return growth != null && Math.max(growth.from(), growth.to()) == 2 ? built(growth) : 1.0F;
	}

	/** How much of the big body still holds you as you fight its hold (1 all of it; see {@link #HULK_PEEL}). */
	static float peeledHulk(AvatarRenderState avatar) {
		Float pull = avatar.getData(PULL);
		return pull == null ? 1.0F : 1.0F - HULK_PEEL * ease(pull);
	}

	/** How far you've torn a part out of the suit (0 none, up to its {@link #PEEL_MAX}), for a pull (0-1). Parts as {@link #wrapped}. */
	static float peel(float pull, int part) {
		return PEEL_MAX[part] * ease((pull - PEEL_START[part]) / PEEL_SPAN);
	}

	/** How far toward the higher of the change's two levels it is (putting on reads 0 → 1, taking off 1 → 0). */
	static float built(Growth growth) {
		return growth.to() > growth.from() ? growth.t() : 1.0F - growth.t();
	}

	/** Each part's start (share of the change): body, arms, head, legs; for the big body ({@code big}) the head last. */
	static float start(int part, boolean big) {
		return switch (part) {
			case 1, 2 -> big ? BIG_ARMS : ARMS;
			case 3 -> big ? BIG_HEAD : HEAD;
			case 4, 5 -> big ? BIG_LEGS : LEGS;
			default -> big ? BIG_BODY : BODY;
		};
	}

	/** How far a part's tendrils have wrapped it (0-1). Parts: 0 body, 1 right arm, 2 left arm, 3 head, 4 right leg, 5 left leg. */
	static float wrapped(float built, int part) {
		return wrapped(built, part, false);
	}

	/** The same, building the big body ({@code big}: its order, at {@link #BIG_PACE}) or the suit. */
	static float wrapped(float built, int part, boolean big) {
		return ease((built - start(part, big)) / (WRAP_SPAN * (big ? BIG_PACE : 1.0F)));
	}

	/** How far a part's goo (or its suit, {@code suit}) has folded over it, from its joint outward (0-1). */
	static float layered(float built, int part, boolean suit) {
		return layered(built, part, suit, false);
	}

	/** The same, under the big body as it's built ({@code big}) or for the suit alone. */
	static float layered(float built, int part, boolean suit, boolean big) {
		float pace = big ? BIG_PACE : 1.0F;
		return ease((built - start(part, big) - (GOO_AFTER + (suit ? SUIT_AFTER : 0.0F)) * pace) / (LAYER_SPAN * pace));
	}

	/** How far the big body's thick tendrils round a part have sunk into the goo flowing over them (0-1). */
	static float sunk(float built, int part) {
		return ease((built - start(part, true) - BIG_SINK_AFTER) / BIG_SINK_SPAN);
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** The suit (or the goo under it) laid over each part from its joint out, as far as it's got. */
	private static void lay(PlayerModel player, AvatarRenderState avatar, Role role) {
		// (the tear-off hides the goo whole; it's back for the next time the suit goes on over bare skin)
		player.root().visible = true;
		Growth growth = avatar.getData(KEY);
		if (growth != null && growth.tear()) {
			// torn off: WebSuitTear lays it (after the poses)
			return;
		}
		ModelPart[] parts = {player.body, player.rightArm, player.leftArm, player.head, player.rightLeg, player.leftLeg};
		Float pooled = avatar.getData(CUSHION);
		if (growth == null && pooled != null && role == Role.GOO) {
			// (0.13.4) bracing for a landing: only the legs, the goo welling up them from the feet (anchored at the foot,
			// so it rises toward the hip), wider at the bottom, its surface rippling as it climbs
			for (ModelPart part : parts) {
				part.visible = false;
			}
			for (int i = 4; i <= 5; i++) {
				ModelPart leg = parts[i];
				float ripple = Mth.sin(avatar.ageInTicks * 0.9F + i * 1.7F) * CUSHION_RIPPLE * (1.0F - pooled * 0.5F);
				leg.visible = pooled > 0.02F;
				leg.yScale = pooled;
				// (a leg hangs 12 pixels from its hip: drop the pivot so the scaled part still ends at the foot)
				leg.y += 12.0F * (1.0F - pooled);
				leg.xScale = 1.0F + CUSHION_SPREAD * (1.0F - pooled) + ripple;
				leg.zScale = 1.0F + CUSHION_SPREAD * (1.0F - pooled) + ripple;
			}
			return;
		}
		Float pull = avatar.getData(PULL);
		if (growth == null && pull != null && role == Role.SUIT) {
			// fighting its hold: each part torn partway out of it (back toward its joint), the edge rippling as it heaves
			for (int i = 0; i < parts.length; i++) {
				float cover = 1.0F - peel(pull, i);
				ModelPart part = parts[i];
				float ripple = cover < 1.0F ? Mth.sin(cover * Mth.PI * 3.0F + avatar.ageInTicks * 0.9F + i) * 0.05F : 0.0F;
				part.yScale = cover;
				part.xScale = Mth.lerp(cover, 0.92F, 1.0F) + ripple;
				part.zScale = Mth.lerp(cover, 0.92F, 1.0F) + ripple;
				part.visible = cover > 0.02F;
			}
			return;
		}
		if (growth == null || Math.min(growth.from(), growth.to()) >= 1) {
			// nothing changing (or the suit under the big body as it comes or goes): whole, as it is
			for (ModelPart part : parts) {
				part.xScale = 1.0F;
				part.yScale = 1.0F;
				part.zScale = 1.0F;
				part.visible = true;
			}
			Float face = avatar.getData(FACE);
			if (face != null && role != Role.HULK) {
				// only the face bare: the head drawn down into the neck (and flowing back up over it)
				float cover = 1.0F - ease(face);
				float ripple = cover > 0.0F && cover < 1.0F ? Mth.sin(cover * Mth.PI * 2.0F + avatar.ageInTicks * 0.8F) * 0.05F : 0.0F;
				player.head.yScale = cover;
				player.head.xScale = Mth.lerp(cover, 0.9F, 1.0F) + ripple;
				player.head.zScale = Mth.lerp(cover, 0.9F, 1.0F) + ripple;
				player.head.visible = cover > 0.02F;
			}
			return;
		}
		float built = built(growth);
		boolean big = Math.max(growth.from(), growth.to()) == 2;
		// (the face bare, its mask off: the head is never laid over it, so it doesn't pop off when the change ends)
		Float face = avatar.getData(FACE);
		float headCover = face == null || big ? 1.0F : 1.0F - ease(face);
		for (int i = 0; i < parts.length; i++) {
			float cover = layered(built, i, role == Role.SUIT, big);
			if (i == 3) {
				cover = Math.min(cover, headCover);
			}
			// it folds down the part from the joint (each part's pivot), thickening as it settles
			ModelPart part = parts[i];
			float ripple = cover > 0.0F && cover < 1.0F ? Mth.sin(cover * Mth.PI * 2.0F + avatar.ageInTicks * 0.6F) * 0.04F : 0.0F;
			part.yScale = cover;
			part.xScale = Mth.lerp(cover, 0.9F, 1.0F) + ripple;
			part.zScale = Mth.lerp(cover, 0.9F, 1.0F) + ripple;
			part.visible = cover > 0.02F;
		}
	}

	/**
	 * The suit doesn't cover the body right now though it's on: it's being torn off (or fought over, or settling back
	 * after the fight), or you're tearing out of a takeover's hold. The body under it must show.
	 */
	private static boolean bared(AvatarRenderState avatar) {
		Growth growth = avatar.getData(KEY);
		return growth != null && growth.tear() || avatar.getData(PULL) != null;
	}

	/** The suit (or Living Armor's bulk) is all over them, not growing or drawing back. */
	/** (0.12.5) Also what BodyCover asks: whether the suit covers the wearer now. */
	public static boolean covered(AvatarRenderState avatar) {
		if (avatar.isInvisible) {
			return false;
		}
		Growth growth = avatar.getData(KEY);
		if (growth != null) {
			// most of the way there (or only just starting to draw back)
			if (Math.min(growth.from(), growth.to()) >= 1) {
				return true;
			}
			return growth.to() > growth.from() ? growth.t() >= COVERED_AT : growth.t() <= 1.0F - COVERED_AT;
		}
		return bigBody(avatar) || avatar.getData(WebSymbioteClient.SUIT) != null;
	}

	/**
	 * Players drawn by MCA (its "Player" look) wear MCA's own body, clothes and hair, sized by its sliders, some of it
	 * further out than the suit: all of it goes under the suit while the suit covers them, and comes back after (only
	 * the parts that were showing).
	 */
	private static final String MCA_MODELS = "net.conczin.mca.client.model.";
	private static final Map<Model<?>, java.util.List<ModelPart>> MCA_HIDDEN = new WeakHashMap<>();

	private static void mcaUnder(Model<?> model, boolean covered, boolean face) {
		java.util.List<ModelPart> hidden = MCA_HIDDEN.get(model);
		if (covered) {
			if (hidden == null) {
				hidden = new java.util.ArrayList<>();
				MCA_HIDDEN.put(model, hidden);
			}
			// (0.13.23) the mask off: the villager's own head (skin, face, hair) stays out from under the suit
			Set<ModelPart> head = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
			if (face && model instanceof net.minecraft.client.model.HumanoidModel<?> humanoid) {
				head.addAll(humanoid.head.getAllParts());
			}
			for (ModelPart part : hidden) {
				if (head.contains(part)) {
					part.visible = true;
				}
			}
			hidden.removeIf(head::contains);
			for (ModelPart part : model.allParts()) {
				if (part.visible && !head.contains(part)) {
					part.visible = false;
					hidden.add(part);
				}
			}
		} else if (hidden != null) {
			for (ModelPart part : hidden) {
				part.visible = true;
			}
			MCA_HIDDEN.remove(model);
		}
	}

	/** (0.13.23) The face shows through the suit now: its mask off, or bared in a fight (at least half bare). */
	public static boolean faceShown(AvatarRenderState avatar) {
		Float face = avatar.getData(FACE);
		return face != null && face >= 0.5F;
	}

	/** How far through putting the suit on (0-1) the skin's outer layer goes under it. */
	private static final float COVERED_AT = 0.55F;
}
