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
 * The suit doesn't just appear: it grows over you like flesh. Put on, it spreads out from the chest, the body first,
 * then the arms and head, then the legs, each part swelling out past its size and settling; taken off, it draws back
 * the other way. Living Armor swells up over the suit the same way (and shrinks back). Everyone sees it.
 */
public final class WebSuitGrowth {
	/** How long a change takes (ticks). */
	static final float TICKS = 16.0F;
	/** Body, arms, head, legs: when each starts growing (0-1 of the change), and how long each takes to. */
	private static final float BODY = 0.0F, ARMS = 0.2F, HEAD = 0.3F, LEGS = 0.4F, SPAN = 0.6F;
	/** How far past its size a part swells before settling, and how small the bulk is before Living Armor fills it out. */
	private static final float OVERSHOOT = 0.12F, BULK_FROM = 0.8F;

	/** On a player's render state while their suit is changing. {@code from}, {@code to}: 0 bare, 1 suit, 2 Living Armor. */
	record Growth(int from, int to, float t) {
	}

	static final RenderStateDataKey<Growth> KEY = RenderStateDataKey.create(() -> "jaymospowers:web_suit_growth");

	/** The suit layer's own models (only these are grown), and which of them are Living Armor's bulk. */
	private static final Set<Model<?>> MODELS = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Set<Model<?>> BULK = Collections.newSetFromMap(new WeakHashMap<>());

	/** entity id → {shown level, level before the change, tick it changed}. */
	private static final Map<Integer, long[]> CHANGES = new HashMap<>();

	private WebSuitGrowth() {
	}

	static void register(Model<?> model, boolean bulk) {
		MODELS.add(model);
		if (bulk) {
			BULK.add(model);
		}
	}

	/** 0 bare, 1 the suit, 2 the big body. */
	static int level(Player player) {
		return WebSymbiote.bulked(player) ? 2 : WebSymbiote.suitShown(player) ? 1 : 0;
	}

	/** While extracting a player's render state: notes a change, and puts the one under way on the state. */
	static void extract(Player player, AvatarRenderState state, float partialTicks) {
		int now = level(player);
		long[] change = CHANGES.get(player.getId());
		long tick = player.tickCount;
		if (change == null) {
			// first seen: as it is, no growing
			CHANGES.put(player.getId(), new long[] {now, now, tick - 1000});
			state.setData(KEY, null);
			return;
		}
		if (change[0] != now) {
			change[1] = change[0];
			change[0] = now;
			change[2] = tick;
		}
		float t = (tick - change[2] + partialTicks) / TICKS;
		state.setData(KEY, t < 1.0F && change[1] != change[0] ? new Growth((int)change[1], (int)change[0], Math.max(0.0F, t)) : null);
		if (CHANGES.size() > 256) {
			CHANGES.keySet().removeIf(id -> Minecraft.getInstance().level == null || Minecraft.getInstance().level.getEntity(id) == null);
		}
	}

	/** After the suit model is posed: each part scaled by how far it has grown. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof PlayerModel player) || !MODELS.contains(model)) {
			return;
		}
		Growth growth = avatar.getData(KEY);
		boolean bulk = BULK.contains(model);
		if (growth == null || !bulk && Math.max(growth.from(), growth.to()) == 2) {
			// nothing changing (or the plain suit under armour that's coming or going): whole, as it is
			for (ModelPart part : new ModelPart[] {player.body, player.rightArm, player.leftArm, player.head, player.rightLeg, player.leftLeg}) {
				part.xScale = 1.0F;
				part.yScale = 1.0F;
				part.zScale = 1.0F;
				part.visible = true;
			}
			return;
		}
		scale(player.body, growth, BODY, bulk);
		scale(player.rightArm, growth, ARMS, bulk);
		scale(player.leftArm, growth, ARMS, bulk);
		scale(player.head, growth, HEAD, bulk);
		scale(player.rightLeg, growth, LEGS, bulk);
		scale(player.leftLeg, growth, LEGS, bulk);
	}

	private static void scale(ModelPart part, Growth growth, float delay, boolean bulk) {
		boolean growing = growth.to() > growth.from();
		// growing out from the chest, or drawing back the other way (legs first)
		float start = growing ? delay : LEGS - delay;
		float g = Mth.clamp((growth.t() - start) / SPAN, 0.0F, 1.0F);
		// grown: swelling past its size, then settling (and a ripple of flesh as it creeps)
		float grown = growing ? swell(g) : 1.0F - g * g;
		float size;
		if (bulk) {
			// Living Armor: the bulk fills out from just over the suit (or from nothing, put on bare)
			float low = Math.min(growth.from(), growth.to()) >= 1 ? BULK_FROM : 0.0F;
			size = Mth.lerp(grown, low, 1.0F);
		} else {
			size = grown;
		}
		float ripple = growing && g > 0.0F && g < 1.0F ? Mth.sin(g * Mth.PI * 3.0F) * 0.05F : 0.0F;
		part.xScale = size + ripple;
		part.yScale = size;
		part.zScale = size + ripple;
		part.visible = size > 0.02F;
	}

	/** 0 → 1, overshooting a little first. */
	private static float swell(float g) {
		float eased = 1.0F - (1.0F - g) * (1.0F - g);
		return eased + Mth.sin(g * Mth.PI) * OVERSHOOT;
	}
}
