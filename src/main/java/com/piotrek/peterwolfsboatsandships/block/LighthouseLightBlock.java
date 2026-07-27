package com.piotrek.peterwolfsboatsandships.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Powerful lighthouse lamp: local block light 15 plus a client-side rotating
 * spotlight (or blinking flash) beam visible far beyond normal block draw distance.
 * Right-click toggles between sweeping spot ray and blinking flash.
 */
public final class LighthouseLightBlock extends BaseEntityBlock {
	public static final MapCodec<LighthouseLightBlock> CODEC = simpleCodec(LighthouseLightBlock::new);
	/** false = rotating spot ray, true = blinking flash light. */
	public static final BooleanProperty FLASHING = BooleanProperty.create("flashing");
	private static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);

	public LighthouseLightBlock(final BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FLASHING, false));
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FLASHING);
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(final BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected InteractionResult useWithoutItem(
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Player player,
		final BlockHitResult hitResult
	) {
		return this.toggleMode(state, level, pos, player);
	}

	private InteractionResult toggleMode(
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Player player
	) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		boolean nextFlashing = !state.getValue(FLASHING);
		level.setBlock(pos, state.setValue(FLASHING, nextFlashing), Block.UPDATE_ALL);
		level.playSound(
			null,
			pos,
			SoundEvents.LEVER_CLICK,
			SoundSource.BLOCKS,
			0.4F,
			nextFlashing ? 0.7F : 1.05F
		);
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(
				Component.translatable(
					nextFlashing
						? "message.peterwolfs_boats_and_ships.lighthouse_flash"
						: "message.peterwolfs_boats_and_ships.lighthouse_spot"
				),
				true
			);
		}
		return InteractionResult.CONSUME;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
		return new LighthouseLightBlockEntity(pos, state);
	}
}
