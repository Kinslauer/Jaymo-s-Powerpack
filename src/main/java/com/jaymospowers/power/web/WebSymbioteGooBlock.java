package com.jaymospowers.power.web;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The black goo at the heart of a meteor crater. It can't be broken or pushed; the only way it leaves is crawling up
 * onto a web slinger who reaches for it (see {@link WebSymbiote#bond}). It breathes out faint dark motes, and the
 * crater still smokes a little around it.
 * <p>
 * The red one ({@code WebPower.RED_GOO}, "Red Ooze", from the rarer red craters) is the same, red, and bonds the red
 * strain ({@link WebSymbioteStrain#RED}).
 */
public class WebSymbioteGooBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 7.0, 14.0);
	/** Chance per animate tick of a mote, and of a wisp of smoke or ash. */
	private static final float MOTE_CHANCE = 0.6F;
	private static final float SMOKE_CHANCE = 0.12F;

	/** Which symbiote it is. */
	private final WebSymbioteStrain strain;

	public WebSymbioteGooBlock(final Properties properties) {
		this(properties, WebSymbioteStrain.BLACK);
	}

	public WebSymbioteGooBlock(final Properties properties, final WebSymbioteStrain strain) {
		super(properties);
		this.strain = strain;
	}

	/** The symbiote that bonds from this goo. */
	public WebSymbioteStrain strain() {
		return this.strain;
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		if (random.nextFloat() < MOTE_CHANCE) {
			double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
			double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
			level.addParticle(new DustParticleOptions(random.nextInt(4) == 0 ? WebSymbioteStrains.sheen(this.strain) : WebSymbioteStrains.color(this.strain), 0.7F),
				x, pos.getY() + 0.45, z, 0.0, 0.02, 0.0);
		}
		if (random.nextFloat() < SMOKE_CHANCE) {
			double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 6.0;
			double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 6.0;
			level.addParticle(random.nextBoolean() ? ParticleTypes.SMOKE : ParticleTypes.ASH, x, pos.getY() + 0.3, z, 0.0, 0.03, 0.0);
		}
	}
}
