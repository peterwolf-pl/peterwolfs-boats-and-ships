package com.piotrek.peterwolfsboatsandships.item;

import com.piotrek.peterwolfsboatsandships.entity.AbstractShipEntity;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class ShipItem extends Item {
	private final Function<Level, ? extends AbstractShipEntity> shipFactory;

	public ShipItem(Properties properties, Function<Level, ? extends AbstractShipEntity> shipFactory) {
		super(properties);
		this.shipFactory = shipFactory;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (!level.isClientSide()) {
			BlockPos clicked = context.getClickedPos();
			this.place(level, context.getPlayer(), context.getItemInHand(), clicked.getX() + 0.5D, clicked.getY() + 1.15D, clicked.getZ() + 0.5D);
		}
		return InteractionResult.SUCCESS;
	}

	/** Lets a player place a vessel by right-clicking open water, not just a block face. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.WATER);
		if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
		if (!level.isClientSide()) {
			this.place(level, player, player.getItemInHand(hand), hit.getLocation().x, hit.getLocation().y + 0.20D, hit.getLocation().z);
		}
		return InteractionResult.SUCCESS;
	}

	private void place(Level level, Player player, net.minecraft.world.item.ItemStack stack, double x, double y, double z) {
		AbstractShipEntity ship = this.shipFactory.apply(level);
		ship.setPos(x, y, z);
		ship.absSnapRotationTo(player == null ? 0.0F : player.getYRot(), 0.0F);
		level.addFreshEntity(ship);
		if (player != null && !player.getAbilities().instabuild) stack.shrink(1);
	}
}
