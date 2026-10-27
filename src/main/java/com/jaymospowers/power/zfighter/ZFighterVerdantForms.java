package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * (0.17.48, Andy's NOEA picks) More Verdant forms, past the Giant forms (Giant, Great Giant, Titan: {@link ZFighterLineage#GIANT_SCALE}).
 * Both are branch forms ({@link ZFighterBranchForms}: C from Titan; the numbers are there):
 * <ul>
 * <li><b>Gloom Giant</b> ({@link ZFighterBranchForms#GLOOM_GIANT}): a Titan who has trained it half-mastered, lived through
 * 200 hardship and found it on a night with 25000 TP to give. Bigger again ({@link #GLOOM_SCALE} more scale), his
 * aura gone to deep jade, thick-skinned ({@link #GLOOM_ARMOR} armor, {@link #GLOOM_KNOCKBACK} knockback
 * resistance) and slow-footed; dark smoke rolls off him.</li>
 * <li><b>Ascendant Verdant</b> ({@link ZFighterBranchForms#ASCENDANT}): after five minutes as the Gloom Giant the weight comes off
 * him. Back down to near Titan's size ({@link #ASCENDANT_SCALE} more), fast and fierce, pale lime light round him, and his
 * body mends {@link #ASCENDANT_MEND} health every second on top of a Verdant's own.</li>
 * </ul>
 */
final class ZFighterVerdantForms {
	/** The Gloom Giant: scale added on top of Titan's, armor, knockback resistance. */
	static final double GLOOM_SCALE = ZFighterBranchForms.SCALE[0];
	static final double GLOOM_ARMOR = 6.0;
	static final double GLOOM_KNOCKBACK = 0.6;
	/** The Ascendant Verdant: scale added on top of Titan's, health mended a second. */
	static final double ASCENDANT_SCALE = ZFighterBranchForms.SCALE[1];
	static final float ASCENDANT_MEND = 1.5F;
	static final int SMOKE_COLOR = 0x103828;

	private static final Identifier SCALE_ID = JaymosPowers.id("zfighter_verdant_branch_scale");
	private static final Identifier ARMOR_ID = JaymosPowers.id("zfighter_verdant_branch_armor");
	private static final Identifier KNOCKBACK_ID = JaymosPowers.id("zfighter_verdant_branch_knockback");

	private ZFighterVerdantForms() {
	}

	/** The Verdant branch forms' bulk and hide, refreshed with the rest of his attributes ({@code branch} is the form he is in, or 0). */
	static void attributes(ServerPlayer player, int branch) {
		boolean gloom = branch == ZFighterBranchForms.GLOOM_GIANT;
		boolean verdant = gloom || branch == ZFighterBranchForms.ASCENDANT;
		ZFighterPower.set(player, Attributes.SCALE, SCALE_ID, verdant ? ZFighterBranchForms.SCALE[branch - ZFighterBranchForms.FIRST] : 0.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		ZFighterPower.set(player, Attributes.ARMOR, ARMOR_ID, gloom ? GLOOM_ARMOR : 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, gloom ? GLOOM_KNOCKBACK : 0.0, AttributeModifier.Operation.ADD_VALUE);
	}

	/** Every tick of the power: smoke off the Gloom Giant, mending and motes on the Ascendant. */
	static void tick(ServerPlayer player, ZFighterPower.Live live, int branch) {
		if (branch != ZFighterBranchForms.GLOOM_GIANT && branch != ZFighterBranchForms.ASCENDANT) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		double width = player.getBbWidth() * 0.5;
		double height = player.getBbHeight();
		if (branch == ZFighterBranchForms.GLOOM_GIANT) {
			if (now % 3 == 0) {
				level.sendParticles(new DustParticleOptions(SMOKE_COLOR, 3.0F), true, false, player.getX(), player.getY() + height * 0.5, player.getZ(), 3, width,
					height * 0.4, width, 0.0);
			}
			if (now % 8 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, true, false, player.getX(), player.getY() + 0.2, player.getZ(), 2, width, 0.1, width, 0.02);
			}
		} else {
			if (now % 20 == 0 && player.getHealth() < player.getMaxHealth()) {
				player.heal(ASCENDANT_MEND);
			}
			if (now % 3 == 0) {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, true, false, player.getX(), player.getY() + height * 0.5, player.getZ(), 2, width, height * 0.4,
					width, 0.0);
			}
		}
	}
}
