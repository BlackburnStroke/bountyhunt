package com.bountyhunt.block;

import com.bountyhunt.gui.GuiService;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.ShapeContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A 3x3 wooden town notice board. One item places nine linked parts (part_x 0..2 left to right as seen by
 * the viewer, part_y 0..2 bottom to top). Right-clicking any part opens the board; breaking any part removes all nine.
 */
public class BountyBoardBlock extends HorizontalFacingBlock {
	public static final IntProperty PART_X = IntProperty.of("part_x", 0, 2);
	public static final IntProperty PART_Y = IntProperty.of("part_y", 0, 2);

	private static final VoxelShape NORTH = Block.createCuboidShape(0, 0, 11, 16, 16, 16);
	private static final VoxelShape SOUTH = Block.createCuboidShape(0, 0, 0, 16, 16, 5);
	private static final VoxelShape WEST = Block.createCuboidShape(11, 0, 0, 16, 16, 16);
	private static final VoxelShape EAST = Block.createCuboidShape(0, 0, 0, 5, 16, 16);

	public BountyBoardBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(PART_X, 1).with(PART_Y, 0));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART_X, PART_Y);
	}

	/** Position of part (px, py), given the position of part (originPx, originPy). */
	private static BlockPos partPos(BlockPos origin, Direction facing, int originPx, int originPy, int px, int py) {
		Direction right = facing.rotateYCounterclockwise(); // viewer's right-hand side
		return origin.offset(right, px - originPx).up(py - originPy);
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		Direction facing = ctx.getHorizontalPlayerFacing().getOpposite(); // front faces the player
		BlockPos base = ctx.getBlockPos(); // the clicked spot becomes the bottom-centre part
		World world = ctx.getWorld();
		for (int px = 0; px < 3; px++) {
			for (int py = 0; py < 3; py++) {
				BlockPos p = partPos(base, facing, 1, 0, px, py);
				if (world.isOutOfHeightLimit(p)) {
					return null;
				}
				if (!(px == 1 && py == 0) && !world.getBlockState(p).isReplaceable()) {
					return null; // not enough free space for the whole board
				}
			}
		}
		return getDefaultState().with(FACING, facing).with(PART_X, 1).with(PART_Y, 0);
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		if (world.isClient) {
			return;
		}
		Direction facing = state.get(FACING);
		for (int px = 0; px < 3; px++) {
			for (int py = 0; py < 3; py++) {
				if (px == 1 && py == 0) {
					continue;
				}
				world.setBlockState(partPos(pos, facing, 1, 0, px, py), state.with(PART_X, px).with(PART_Y, py), Block.NOTIFY_ALL);
			}
		}
	}

	@Override
	public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		if (!world.isClient) {
			Direction facing = state.get(FACING);
			int ox = state.get(PART_X);
			int oy = state.get(PART_Y);
			for (int px = 0; px < 3; px++) {
				for (int py = 0; py < 3; py++) {
					if (px == ox && py == oy) {
						continue;
					}
					BlockPos p = partPos(pos, facing, ox, oy, px, py);
					if (world.getBlockState(p).isOf(this)) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
					}
				}
			}
			if (!player.isCreative()) {
				dropStack(world, pos, new ItemStack(this)); // exactly one board item, from whichever part was broken
			}
		}
		super.onBreak(world, pos, state, player);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
			GuiService.openBoard(serverPlayer, pos);
		}
		return ActionResult.success(world.isClient);
	}

	private static VoxelShape shapeFor(BlockState state) {
		return switch (state.get(FACING)) {
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			case EAST -> EAST;
			default -> NORTH;
		};
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapeFor(state);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shapeFor(state);
	}
}
