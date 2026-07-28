package com.piotrek.peterwolfsboatsandships.block;

import com.mojang.serialization.MapCodec;
import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Lighthouse lamp. Right-click cycles: rotating spot ray → blinking point light → off.
 */
public final class LighthouseLightBlock extends BaseEntityBlock {
	public static final MapCodec<LighthouseLightBlock> CODEC = simpleCodec(LighthouseLightBlock::new);
	public static final EnumProperty<LighthouseLightMode> MODE = EnumProperty.create("mode", LighthouseLightMode.class);
	/** Flash phase only: bright when true, dark when false. Spot keeps lit; off keeps unlit. */
	public static final BooleanProperty LIT = BooleanProperty.create("lit");
	private static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);

	public LighthouseLightBlock(final BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any()
			.setValue(MODE, LighthouseLightMode.SPOT)
			.setValue(LIT, true));
	}

	public static int lightEmission(final BlockState state) {
		return switch (state.getValue(MODE)) {
			case SPOT -> 15;
			case FLASH -> state.getValue(LIT) ? 15 : 0;
			case OFF -> 0;
		};
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(MODE, LIT);
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
		return this.cycleMode(state, level, pos, player);
	}

	private InteractionResult cycleMode(
		final BlockState state,
		final Level level,
		final BlockPos pos,
		final Player player
	) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		LighthouseLightMode next = state.getValue(MODE).next();
		boolean lit = next != LighthouseLightMode.OFF;
		level.setBlock(pos, state.setValue(MODE, next).setValue(LIT, lit), Block.UPDATE_ALL);
		float pitch = switch (next) {
			case SPOT -> 1.05F;
			case FLASH -> 0.85F;
			case OFF -> 0.55F;
		};
		level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, pitch);
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(
				Component.translatable("message.peterwolfs_boats_and_ships.lighthouse_" + next.getSerializedName()),
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

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(final Level level, final BlockState state, final BlockEntityType<T> type) {
		return level.isClientSide()
			? null
			: createTickerHelper(type, PeterwolfsBoatsAndShipsMod.LIGHTHOUSE_LIGHT_BLOCK_ENTITY, LighthouseLightBlockEntity::serverTick);
	}
}
