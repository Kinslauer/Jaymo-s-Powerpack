package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.fuel.PodFuel;
import com.jaymospowers.power.zfighter.pod.PodGrades;
import com.jaymospowers.tinkering.Trait;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.56, SevTech picks: quench-glacia) Edges. A hot piece quenched in a water cauldron that has something from a far world
 * lying in it takes an edge for good: the Glacia ice-glass ({@link PodGrades}'s second world part) gives the <b>Cold Edge</b>
 * (a hit slows what it strikes), a Cinder Ember ({@link PodFuel#CINDER_EMBER}) the <b>Hot Edge</b> (a hit sets it alight). Drop the
 * item into the cauldron before the dip; it is used up by the quench. A piece takes one edge only: one that has an edge
 * already is dipped as plain water would, and the item stays. The edge rides from the head onto the tool or weapon made
 * from it ({@link Grades#CARRIERS}) and works from whatever is held in the main hand.
 */
public final class Edges {
	// ---- tuning ----
	/** Cold Edge: how long (ticks) and how hard (amplifier, 0 is Slowness I) a hit slows its target. */
	public static final int COLD_TICKS = 80, COLD_AMPLIFIER = 0;
	/** Hot Edge: seconds a hit sets its target alight. */
	public static final float HOT_SECONDS = 4.0F;

	/** The edge a piece has: {@code "cold"} or {@code "hot"}. */
	public static DataComponentType<String> EDGE;

	public static final String COLD = "cold", HOT = "hot";

	private Edges() {
	}

	static void register() {
		EDGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("edge"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		Grades.CARRIERS.add((head, made) -> {
			String edge = head.get(EDGE);
			if (edge != null) {
				made.set(EDGE, edge);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken > 0.0F && !blocked && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
				&& entity.level() instanceof ServerLevel level) {
				bite(level, entity, attacker.getMainHandItem());
			}
		});
	}

	/** What a thing lying in a cauldron gives, or null: the ice-glass its cold edge, the ember its hot one. */
	public static @Nullable String edgeOf(ItemStack lying) {
		if (lying.is(PodGrades.partFor(2))) {
			return COLD;
		}
		return lying.is(PodFuel.CINDER_EMBER) ? HOT : null;
	}

	/** The item that gives {@code edge} (for JEI). */
	public static Item giver(String edge) {
		return COLD.equals(edge) ? PodGrades.partFor(2) : PodFuel.CINDER_EMBER;
	}

	/** The trait an edge is. */
	public static Trait trait(String edge) {
		return COLD.equals(edge) ? Trait.COLD_EDGE : Trait.HOT_EDGE;
	}

	/**
	 * A hot {@code stack} is about to be quenched in the cauldron at {@code pos}: if an edge-giving thing lies in it and the piece has no
	 * edge yet, one of them is used up and the edge is returned (else null). Hot ingots take no edge.
	 */
	public static @Nullable String take(ServerLevel level, BlockPos pos, ItemStack stack) {
		if (stack.has(EDGE) || stack.is(Smithing.HOT_INGOT)) {
			return null;
		}
		for (ItemEntity lying : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos), item -> item.isAlive())) {
			String edge = edgeOf(lying.getItem());
			if (edge != null) {
				ItemStack rest = lying.getItem().copy();
				rest.shrink(1);
				if (rest.isEmpty()) {
					lying.discard();
				} else {
					lying.setItem(rest);
				}
				return edge;
			}
		}
		return null;
	}

	/** {@code quenched} (the piece after its dip) with {@code edge} set, if there is one. */
	public static ItemStack mark(ItemStack quenched, @Nullable String edge) {
		if (edge != null && !quenched.isEmpty()) {
			quenched.set(EDGE, edge);
		}
		return quenched;
	}

	/** A hit with {@code weapon} landed on {@code target}. */
	private static void bite(ServerLevel level, LivingEntity target, ItemStack weapon) {
		String edge = weapon.get(EDGE);
		if (edge == null || !target.isAlive()) {
			return;
		}
		if (COLD.equals(edge)) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, COLD_TICKS, COLD_AMPLIFIER));
			level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.25, 0.3, 0.25, 0.02);
		} else if (!target.fireImmune()) {
			target.igniteForSeconds(HOT_SECONDS);
			level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 5, 0.25, 0.3, 0.25, 0.02);
		}
	}

	/** Both edges, for the pages that list them. */
	public static List<String> all() {
		return List.of(COLD, HOT);
	}
}
