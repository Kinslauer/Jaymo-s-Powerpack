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
 * built the same way: thick tendrils wrap you, then its muscle is slapped on a slab at a time (core, back, lats, chest,
 * shoulders and arms, the forearms and hands, the claws, at last the head and its jaws; {@link WebHulk}), while you're
 * doubled over in pain (see WebFeral). Everyone sees it.
 */
public final class WebSuitGrowth {
	/** How long putting the suit on (or off) takes (ticks); during a roar it pieces itself together quicker. */
	static final float TICKS = 36.0F, ROAR_TICKS = 26.0F;
	/** Building the big body takes longer: it doubles over in pain as it grows (see WebFeral). */
	static final float BULK_TICKS = 60.0F;
	/**
	 * Body, arms, head, legs: when each part's tendrils start out (share of the change). They take {@code WRAP_SPAN} to wrap
	 * it, the goo layer starts {@code GOO_AFTER} after the tendrils do and takes {@code LAYER_SPAN}, the suit follows it
	 * {@code SUIT_AFTER} later.
	 */
	static final float BODY = 0.0F, ARMS = 0.1F, HEAD = 0.16F, LEGS = 0.22F;
	static final float WRAP_SPAN = 0.3F, GOO_AFTER = 0.28F, SUIT_AFTER = 0.14F, LAYER_SPAN = 0.26F;
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
	public static final RenderStateDataKey<Float> PULL = RenderStateDataKey.create(() -> "jaymospowers:web_suit_pull");

	/** The suit layer's own models (only these are grown), and what each one is. */
	private static final Map<Model<?>, Role> MODELS = new WeakHashMap<>();

	/** entity id → {shown level, level before the change, tick it changed, 1 if a takeover put it on (quicker)}. */
	private static final Map<Integer, long[]> CHANGES = new HashMap<>();

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
		long[] change = CHANGES.get(player.getId());
		// game time, not the player's own age: a respawned player's age starts over, which would hold a change
		// (the suit falling away at death) part-done until it caught up
		long tick = player.level().getGameTime();
		if (change == null) {
			// first seen: as it is, no growing
			CHANGES.put(player.getId(), new long[] {now, now, tick - 1000, 0});
			state.setData(KEY, null);
			state.setData(PULL, null);
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
		if (CHANGES.size() > 256) {
			CHANGES.keySet().removeIf(id -> Minecraft.getInstance().level == null || Minecraft.getInstance().level.getEntity(id) == null);
		}
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
			mcaUnder(model, covered(avatar));
			return;
		}
		if (model instanceof net.minecraft.client.model.HumanoidModel<?> humanoid) {
			WebHulk.shift(humanoid, hulkness(avatar));
		}
		if (!(model instanceof PlayerModel player)) {
			return;
		}
		Role role = MODELS.get(model);
		if (role == null) {
			// the wearer's own body: the skin's outer layer (hat, jacket, sleeves, trousers) sits further out than the
			// suit, and would poke through it in patches, so it goes under the suit while the suit covers it all
			if (covered(avatar)) {
				player.hat.visible = false;
				player.jacket.visible = false;
				player.leftSleeve.visible = false;
				player.rightSleeve.visible = false;
				player.leftPants.visible = false;
				player.rightPants.visible = false;
			}
			return;
		}
		if (role == Role.HULK) {
			WebHulk.Model hulk = (WebHulk.Model)model;
			hulk.build(Math.min(hulkBuilt(avatar), peeledHulk(avatar)));
			hulk.breathe(avatar);
			// elbows and knees bending with the stride (after the limbs are posed above)
			hulk.limbs(avatar);
		} else {
			lay(player, avatar, role);
		}
		if (WebSymbioteClient.ownFirstPerson(avatar)) {
			player.head.visible = false;
		}
	}

	/** Moves a model drawn outside the usual passes (a layer reading the body's parts) out to the big body's frame. */
	static void shift(net.minecraft.client.model.HumanoidModel<?> model, AvatarRenderState state) {
		WebHulk.shift(model, hulkness(state));
	}

	/** How far into the big body's frame the body is spread (0 not at all, 1 the whole way). */
	static float hulkness(AvatarRenderState avatar) {
		Growth growth = avatar.getData(KEY);
		if (growth != null && Math.max(growth.from(), growth.to()) == 2) {
			return ease((built(growth) - 0.25F) / 0.5F);
		}
		WebSymbioteClient.Tendrils tendrils = avatar.getData(WebSymbioteClient.TENDRILS);
		return tendrils != null && tendrils.armor() ? 1.0F : 0.0F;
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

	/** Each part's start (share of the change): body, arms, head, legs. */
	static float start(int part) {
		return switch (part) {
			case 1, 2 -> ARMS;
			case 3 -> HEAD;
			case 4, 5 -> LEGS;
			default -> BODY;
		};
	}

	/** How far a part's tendrils have wrapped it (0-1). Parts: 0 body, 1 right arm, 2 left arm, 3 head, 4 right leg, 5 left leg. */
	static float wrapped(float built, int part) {
		return ease((built - start(part)) / WRAP_SPAN);
	}

	/** How far a part's goo (or its suit, {@code suit}) has folded over it, from its joint outward (0-1). */
	static float layered(float built, int part, boolean suit) {
		return ease((built - start(part) - GOO_AFTER - (suit ? SUIT_AFTER : 0.0F)) / LAYER_SPAN);
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** The suit (or the goo under it) laid over each part from its joint out, as far as it's got. */
	private static void lay(PlayerModel player, AvatarRenderState avatar, Role role) {
		Growth growth = avatar.getData(KEY);
		if (growth != null && growth.tear()) {
			// torn off: WebSuitTear lays it (after the poses)
			return;
		}
		ModelPart[] parts = {player.body, player.rightArm, player.leftArm, player.head, player.rightLeg, player.leftLeg};
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
			return;
		}
		float built = built(growth);
		for (int i = 0; i < parts.length; i++) {
			float cover = layered(built, i, role == Role.SUIT);
			// it folds down the part from the joint (each part's pivot), thickening as it settles
			ModelPart part = parts[i];
			float ripple = cover > 0.0F && cover < 1.0F ? Mth.sin(cover * Mth.PI * 2.0F + avatar.ageInTicks * 0.6F) * 0.04F : 0.0F;
			part.yScale = cover;
			part.xScale = Mth.lerp(cover, 0.9F, 1.0F) + ripple;
			part.zScale = Mth.lerp(cover, 0.9F, 1.0F) + ripple;
			part.visible = cover > 0.02F;
		}
	}

	/** The suit (or Living Armor's bulk) is all over them, not growing or drawing back. */
	private static boolean covered(AvatarRenderState avatar) {
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
		WebSymbioteClient.Tendrils tendrils = avatar.getData(WebSymbioteClient.TENDRILS);
		return tendrils != null && tendrils.armor() || avatar.getData(WebSymbioteClient.SUIT) != null;
	}

	/**
	 * Players drawn by MCA (its "Player" look) wear MCA's own body, clothes and hair, sized by its sliders, some of it
	 * further out than the suit: all of it goes under the suit while the suit covers them, and comes back after (only
	 * the parts that were showing).
	 */
	private static final String MCA_MODELS = "net.conczin.mca.client.model.";
	private static final Map<Model<?>, java.util.List<ModelPart>> MCA_HIDDEN = new WeakHashMap<>();

	private static void mcaUnder(Model<?> model, boolean covered) {
		java.util.List<ModelPart> hidden = MCA_HIDDEN.get(model);
		if (covered) {
			if (hidden == null) {
				hidden = new java.util.ArrayList<>();
				MCA_HIDDEN.put(model, hidden);
			}
			for (ModelPart part : model.allParts()) {
				if (part.visible) {
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

	/** How far through putting the suit on (0-1) the skin's outer layer goes under it. */
	private static final float COVERED_AT = 0.55F;
}
