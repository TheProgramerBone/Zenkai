package com.hmc.zenkai.worldgen.piecegraph;

import com.hmc.zenkai.registry.ModBlocks;
import com.hmc.zenkai.worldgen.PersistentLeavesProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * El "jigsaw" de Zenkai: recorre el catálogo a partir de una pieza inicial, encajando piezas
 * en cada hueco abierto hasta agotar profundidad/presupuesto. Todo en Java plano — sin pools
 * JSON, sin joints con nombres mágicos, y CADA rechazo se explica en el log (colisión, sin
 * candidata, profundidad agotada...), a diferencia del jigsaw vainilla que simplemente no
 * coloca la pieza y no dice por qué.
 *
 * MATEMÁTICA DE ROTACIÓN — verificada contra el fuente descompilado de
 * StructureTemplate#transform y JigsawPlacement (no adivinada): dado un conector local
 * (sin rotar) en {@code localPos} y una rotación {@code R}, su posición mundial tras rotar la
 * pieza en torno a su propio origen (0,0,0) es
 * {@code StructureTemplate.transform(localPos, Mirror.NONE, R, BlockPos.ZERO).offset(templatePosition)}.
 * Por tanto, para que ese conector aterrice exactamente en un {@code targetWorldPos} dado:
 * {@code templatePosition = targetWorldPos - rotatedLocal(localPos, R)}. Es la misma fórmula
 * que usa JigsawPlacement (allí {@code blockpos4 = blockpos2.subtract(blockpos3)}).
 */
public final class PieceGraphPlacer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-PieceGraph");

    private PieceGraphPlacer() {}

    /** Ajustes de una generación: números planos y fáciles de razonar, sin fórmulas acopladas
     *  como el {@code max_distance_from_center} del jigsaw vainilla. */
    public record Config(int maxPieces, int maxDepth, float terminateChance, int placementAttemptsPerSocket) {
        public static Config defaults() { return new Config(24, 6, 0.15f, 6); }
    }

    private record OpenSocket(BlockPos worldPos, Direction facing, ResourceLocation socket, int depthLeft) {}

    /**
     * Decide QUÉ piezas van y DÓNDE a partir de una pieza inicial ya "colocada" (mentalmente)
     * en {@code startPos} con {@code startRotation}. No toca el mundo — usar {@link #placeAll}
     * con el resultado para materializarlo.
     */
    public static List<PlacedPiece> generate(StructureTemplateManager mgr, RandomSource random,
                                              PieceTemplate start, BlockPos startPos, Rotation startRotation,
                                              PieceCatalog catalog, Config cfg) {
        List<PlacedPiece> result = new ArrayList<>();
        List<BoundingBox> placedBoxes = new ArrayList<>();
        Deque<OpenSocket> queue = new ArrayDeque<>();

        StructureTemplate startTpl = mgr.get(start.nbt()).orElse(null);
        if (startTpl == null) {
            LOGGER.error("[PieceGraph] Pieza inicial no encontrada: {}", start.nbt());
            return result;
        }

        result.add(new PlacedPiece(start.nbt(), startPos, startRotation));
        placedBoxes.add(startTpl.getBoundingBox(settingsFor(startRotation), startPos));
        enqueueOpenSockets(queue, start, startPos, startRotation, cfg.maxDepth(), null);

        int placedCount = 1;
        while (!queue.isEmpty() && placedCount < cfg.maxPieces()) {
            OpenSocket open = queue.poll();

            if (open.depthLeft() <= 0) {
                LOGGER.debug("[PieceGraph] Hueco en {} cerrado: profundidad agotada", open.worldPos());
                continue;
            }
            if (random.nextFloat() < cfg.terminateChance()) {
                LOGGER.debug("[PieceGraph] Hueco en {} cerrado: parada aleatoria", open.worldPos());
                continue;
            }

            List<PieceCatalog.Entry> candidates = catalog.candidatesFor(open.socket());
            if (candidates.isEmpty()) {
                LOGGER.debug("[PieceGraph] Hueco en {} ({}) cerrado: ninguna pieza del catálogo ofrece ese socket",
                        open.worldPos(), open.socket());
                continue;
            }

            boolean placed = false;
            for (int attempt = 0; attempt < cfg.placementAttemptsPerSocket() && !placed; attempt++) {
                PieceCatalog.Entry candidate = weightedPick(candidates, random);
                PieceSocket entry = pickEntryConnector(candidate.piece(), open, random);
                if (entry == null) continue;

                Rotation rotation = rotationToFace(entry.localFacing(), open.facing().getOpposite(), random);
                if (rotation == null) continue;

                BlockPos rotatedLocal = StructureTemplate.transform(entry.localPos(), Mirror.NONE, rotation, BlockPos.ZERO);
                BlockPos templatePos = open.worldPos().subtract(rotatedLocal);

                StructureTemplate tpl = mgr.get(candidate.piece().nbt()).orElse(null);
                if (tpl == null) continue; // ya se avisó en PieceTemplate.scan

                BoundingBox box = tpl.getBoundingBox(settingsFor(rotation), templatePos);
                if (collides(box, placedBoxes)) {
                    LOGGER.debug("[PieceGraph] {} rechazada en {} (rotación {}): choca con una pieza ya colocada",
                            candidate.piece().nbt(), templatePos, rotation);
                    continue;
                }

                result.add(new PlacedPiece(candidate.piece().nbt(), templatePos, rotation));
                placedBoxes.add(box);
                enqueueOpenSockets(queue, candidate.piece(), templatePos, rotation, open.depthLeft() - 1, entry);
                placedCount++;
                placed = true;
            }
            if (!placed) {
                LOGGER.debug("[PieceGraph] Hueco en {} ({}) cerrado: sin candidata que encajara tras {} intentos",
                        open.worldPos(), open.socket(), cfg.placementAttemptsPerSocket());
            }
        }
        return result;
    }

    /** Materializa el resultado de {@link #generate} en el mundo. Los piece_connector nunca
     *  se llegan a colocar de verdad (mismo patrón que STRUCTURE_VOID/JIGSAW en
     *  StaticStructurePlacer): se ignoran al colocar, así que la aldea terminada no tiene
     *  bloques invisibles sueltos por todas partes. */
    public static void placeAll(ServerLevel level, List<PlacedPiece> pieces) {
        StructureTemplateManager mgr = level.getStructureManager();
        BlockIgnoreProcessor ignore = new BlockIgnoreProcessor(List.of(
                Blocks.STRUCTURE_VOID, Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW, ModBlocks.PIECE_CONNECTOR.get()));

        for (PlacedPiece piece : pieces) {
            var opt = mgr.get(piece.nbt());
            if (opt.isEmpty()) {
                LOGGER.error("[PieceGraph] No se encontró el NBT al colocar: {}", piece.nbt());
                continue;
            }
            StructurePlaceSettings settings = settingsFor(piece.rotation())
                    .addProcessor(ignore)
                    .addProcessor(PersistentLeavesProcessor.INSTANCE)
                    .setKnownShape(true);
            opt.get().placeInWorld(level, piece.templatePosition(), piece.templatePosition(), settings,
                    level.getRandom(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    private static StructurePlaceSettings settingsFor(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE).setRotationPivot(BlockPos.ZERO);
    }

    /** Encola como huecos abiertos todos los conectores de una pieza recién colocada, salvo
     *  el que se acaba de usar para engancharla (si lo hay — null para la pieza inicial). */
    private static void enqueueOpenSockets(Deque<OpenSocket> queue, PieceTemplate piece, BlockPos templatePos,
                                            Rotation rotation, int depthLeft, PieceSocket usedEntry) {
        for (PieceSocket socket : piece.sockets()) {
            if (socket.equals(usedEntry)) continue;
            BlockPos worldPos = StructureTemplate.transform(socket.localPos(), Mirror.NONE, rotation, BlockPos.ZERO)
                    .offset(templatePos);
            Direction worldFacing = rotation.rotate(socket.localFacing());
            queue.add(new OpenSocket(worldPos.relative(worldFacing), worldFacing, socket.socket(), depthLeft));
        }
    }

    /** Qué rotación hace que {@code localFacing} termine apuntando a {@code wanted}. Para un
     *  conector horizontal hay exactamente una (Rotation es una biyección entre las 4
     *  direcciones horizontales); para uno vertical (arriba/abajo), Rotation no lo toca —
     *  cualquiera de las 4 vale si ya apuntaba donde tocaba, así que se elige al azar para dar
     *  variedad de orientación al resto de la pieza. Null si es imposible (conector vertical
     *  que no coincide con lo pedido). */
    private static Rotation rotationToFace(Direction localFacing, Direction wanted, RandomSource random) {
        if (localFacing.getAxis() == Direction.Axis.Y) {
            return localFacing == wanted ? Rotation.getRandom(random) : null;
        }
        for (Rotation r : Rotation.values()) {
            if (r.rotate(localFacing) == wanted) return r;
        }
        return null; // inalcanzable: las 4 rotaciones cubren las 4 direcciones horizontales
    }

    private static PieceSocket pickEntryConnector(PieceTemplate piece, OpenSocket open, RandomSource random) {
        List<PieceSocket> matching = piece.sockets().stream()
                .filter(s -> s.socket().equals(open.socket()))
                .toList();
        if (matching.isEmpty()) return null; // no debería pasar: candidatesFor ya filtró por socket
        return matching.get(random.nextInt(matching.size()));
    }

    private static PieceCatalog.Entry weightedPick(List<PieceCatalog.Entry> candidates, RandomSource random) {
        int totalWeight = candidates.stream().mapToInt(PieceCatalog.Entry::weight).sum();
        int roll = random.nextInt(Math.max(1, totalWeight));
        int acc = 0;
        for (PieceCatalog.Entry c : candidates) {
            acc += c.weight();
            if (roll < acc) return c;
        }
        return candidates.get(candidates.size() - 1);
    }

    private static boolean collides(BoundingBox box, List<BoundingBox> placed) {
        for (BoundingBox other : placed) {
            if (box.intersects(other)) return true;
        }
        return false;
    }
}
