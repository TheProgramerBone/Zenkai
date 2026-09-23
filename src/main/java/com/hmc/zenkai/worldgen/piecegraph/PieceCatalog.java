package com.hmc.zenkai.worldgen.piecegraph;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El catálogo de piezas disponibles para una generación de {@link PieceGraphPlacer}. A
 * diferencia de los {@code template_pool} del jigsaw vainilla, NO hay un pool por nombre de
 * socket que mantener sincronizado a mano: cada pieza se auto-describe (ver
 * {@link PieceTemplate#scan}), así que el catálogo solo necesita saber QUÉ NBTs existen y con
 * qué peso relativo — el emparejamiento pieza↔hueco lo hace el motor comparando IDs de socket
 * directamente contra lo que cada NBT declara tener.
 */
public final class PieceCatalog {

    /** Una pieza del catálogo con su peso de selección aleatoria (más alto = más probable). */
    public record Entry(PieceTemplate piece, int weight) {}

    private final List<Entry> entries = new ArrayList<>();
    /** Índice socket → piezas que lo ofrecen, para no recorrer todo el catálogo por cada hueco. */
    private final Map<ResourceLocation, List<Entry>> bySocket = new HashMap<>();

    private PieceCatalog() {}

    /** Mismo peso para todas — para catálogos pequeños de prueba donde no importa el balance. */
    public static PieceCatalog scan(StructureTemplateManager mgr, List<ResourceLocation> nbts) {
        Map<ResourceLocation, Integer> weighted = nbts.stream()
                .collect(Collectors.toMap(id -> id, id -> 1, (a, b) -> a, LinkedHashMap::new));
        return scanWeighted(mgr, weighted);
    }

    public static PieceCatalog scanWeighted(StructureTemplateManager mgr, Map<ResourceLocation, Integer> weightedNbts) {
        PieceCatalog catalog = new PieceCatalog();
        weightedNbts.forEach((nbt, weight) ->
                PieceTemplate.scan(mgr, nbt).ifPresent(tpl -> catalog.add(tpl, weight)));
        return catalog;
    }

    private void add(PieceTemplate piece, int weight) {
        Entry entry = new Entry(piece, weight);
        entries.add(entry);
        // Un socket por pieza (deduplicado): si la pieza tiene dos conectores del mismo tipo
        // no queremos que su peso cuente doble solo por aparecer dos veces en la lista.
        Set<ResourceLocation> distinctSockets = piece.sockets().stream()
                .map(PieceSocket::socket).collect(Collectors.toSet());
        for (ResourceLocation socket : distinctSockets) {
            bySocket.computeIfAbsent(socket, k -> new ArrayList<>()).add(entry);
        }
    }

    /** Piezas que tienen al menos un conector de este tipo de socket. Vacía si ninguna encaja
     *  — el motor lo registra como "hueco cerrado sin candidata", no como un error. */
    public List<Entry> candidatesFor(ResourceLocation socket) {
        return bySocket.getOrDefault(socket, List.of());
    }

    public List<Entry> all() { return entries; }
}
