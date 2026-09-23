package com.hmc.zenkai.content.blockentity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Guarda el único dato que le importa al motor de piezas: qué socket ofrece este conector
 * (ver {@code worldgen.piecegraph.PieceGraphPlacer}). La dirección NO se guarda aquí — ya
 * vive en el blockstate ({@link com.hmc.zenkai.content.block.PieceConnectorBlock#FACING}),
 * no hay que duplicarla.
 */
public class PieceConnectorBlockEntity extends BlockEntity {

    private static final ResourceLocation UNSET = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "unset");

    private ResourceLocation socket = UNSET;

    public PieceConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PIECE_CONNECTOR.get(), pos, state);
    }

    public ResourceLocation getSocket() { return socket; }

    public void setSocket(ResourceLocation socket) {
        this.socket = socket;
        setChanged();
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Socket")) {
            ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("Socket"));
            if (parsed != null) socket = parsed;
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Socket", socket.toString());
    }
}
