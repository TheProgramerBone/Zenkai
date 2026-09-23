package com.hmc.zenkai.worldgen.piecegraph;

import com.hmc.zenkai.content.block.PieceConnectorBlock;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Un NBT ya "leído": la lista de conectores que ofrece, escaneados directamente del bloque
 * {@link PieceConnectorBlock} — NO hay que declarar en JSON qué socket ofrece cada pieza, el
 * propio NBT lo dice. Añadir una pieza nueva al catálogo ({@link PieceCatalog}) es registrar
 * su ResourceLocation y ya; el motor descubre sus conectores solo, así que un typo en el
 * socket rompe el emparejamiento de forma visible (la pieza simplemente no encaja en nada,
 * y queda registrado en el log — no un JSON con un string suelto que nadie valida).
 */
public record PieceTemplate(ResourceLocation nbt, List<PieceSocket> sockets) {

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-PieceGraph");

    /** Carga el NBT y escanea sus conectores. Vacío si el NBT no existe. */
    public static Optional<PieceTemplate> scan(StructureTemplateManager mgr, ResourceLocation nbt) {
        Optional<StructureTemplate> opt = mgr.get(nbt);
        if (opt.isEmpty()) {
            LOGGER.error("[Zenkai] Pieza no encontrada: {}", nbt);
            return Optional.empty();
        }
        StructureTemplate tpl = opt.get();

        // relativePosition=false + settings por defecto (rotación NONE, sin reflejar):
        // posiciones tal cual están en el NBT, en bruto — exactamente el espacio local que
        // necesita PieceGraphPlacer para su propia matemática de rotación.
        List<PieceSocket> sockets = tpl.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(),
                        ModBlocks.PIECE_CONNECTOR.get(), false)
                .stream()
                .map(PieceTemplate::toSocket)
                .filter(Objects::nonNull)
                .toList();

        if (sockets.isEmpty()) {
            LOGGER.warn("[Zenkai] {} no tiene ningún piece_connector — nunca podrá engancharse a nada.", nbt);
        }
        return Optional.of(new PieceTemplate(nbt, sockets));
    }

    private static PieceSocket toSocket(StructureTemplate.StructureBlockInfo info) {
        if (info.nbt() == null || !info.nbt().contains("Socket")) {
            LOGGER.warn("[Zenkai] piece_connector en {} sin dato Socket — se ignora.", info.pos());
            return null;
        }
        ResourceLocation socket = ResourceLocation.tryParse(info.nbt().getString("Socket"));
        if (socket == null) {
            LOGGER.warn("[Zenkai] piece_connector en {} con Socket inválido: '{}' — se ignora.",
                    info.pos(), info.nbt().getString("Socket"));
            return null;
        }
        return new PieceSocket(socket, info.pos(), info.state().getValue(PieceConnectorBlock.FACING));
    }
}
