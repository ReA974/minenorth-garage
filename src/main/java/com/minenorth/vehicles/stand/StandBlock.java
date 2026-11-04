package com.minenorth.vehicles.stand;

import com.minenorth.vehicles.fourriere.Impound;
import com.minenorth.vehicles.garage.Garage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Borne interactive, façon serveurs GTA RP : posée par le staff dans un parking (garage) ou devant la fourrière, on s'en approche et on
 * fait clic droit pour ouvrir le menu du garage ou de la fourrière (les mêmes menus que les PNJ et /garagemenu, /fourrieremenu).
 * Écran lumineux et panneau en haut ; en la regardant, le joueur voit « Clic droit : … » (client.StandHint). Incassable en survie.
 */
public class StandBlock extends HorizontalDirectionalBlock {
    public enum Kind { GARAGE, FOURRIERE }

    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 24, 13);
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();
    public final Kind kind;

    public StandBlock(Kind kind) {
        super(Properties.of().strength(-1f, 3600000f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 7));
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) return InteractionResult.CONSUME;
        long now = System.currentTimeMillis();
        Long last = LAST.put(sp.getUUID(), now);
        if (last != null && now - last < 500) return InteractionResult.CONSUME;   // double clic
        if (kind == Kind.GARAGE) Garage.openMenu(sp); else Impound.openRecoverMenu(sp);
        return InteractionResult.CONSUME;
    }
}
