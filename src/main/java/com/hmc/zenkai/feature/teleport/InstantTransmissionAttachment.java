package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Estado servidor de la Transmisión Instantánea. Attachment PROPIO, deliberadamente separado
 * de PlayerStatsAttachment (pedido explícito del usuario al diseñar la Fase 1 — ver
 * .claude/pendiente/instant-transmission-pendiente.md).
 *
 * Dos gestos en la MISMA tecla (TAB), resueltos por InstantTransmissionSystem:
 *  - mantener TAB pulsado y hacer CLIC DERECHO (sin soltar TAB) -> blink instantáneo a lo que
 *    esté en la mira. Soltar TAB SIN haber hecho clic derecho ya NO teletransporta — pedido
 *    explícito del usuario tras probar Dragon Block C: evita el blink accidental de quien solo
 *    toca TAB por error. El clic derecho viaja aparte, ver InstantTransmissionConfirmPacket.
 *  - mantener pulsado y QUIETO {@link #MENU_ARM_TICKS} seguidos -> arma la apertura del menú de
 *    planetas, que SÍ se abre con solo soltar TAB (sin clic derecho — pedido explícito: el menú
 *    se queda en "mantener y soltar"). Mantener pulsado NO congela el movimiento (a propósito:
 *    el jugador debe poder seguir esquivando mientras decide), así que stillTicks es un
 *    contador de QUIETUD, no de "tecla pulsada" — se resetea al moverse sin cancelar el hold.
 *
 * Fase 2, revisión tras feedback del usuario: las posiciones de destino son FIJAS
 * (TeleportAnchors), no "donde estuviste la última vez" — así que aquí solo hace falta
 * recordar DOS conjuntos de booleanos, sin coordenadas: qué destinos se han descubierto
 * (`discoveredIds`) y qué dimensiones se han visitado alguna vez (`visitedDimensionIds`, para
 * que el planeta/dimensión ni aparezca en el selector hasta haber estado ahí).
 */
public class InstantTransmissionAttachment {

    /** Ticks de "quieto" para armar el menú (2s — bajado desde 5s, luego 3s, a petición del
     *  usuario en rondas sucesivas). Mismo criterio que otros holds del mod (KAIOKEN_HOLD_TICKS
     *  en PlayerFormAttachment): la constante vive junto al estado que gobierna, no en el
     *  sistema de tick. */
    public static final int MENU_ARM_TICKS = 40;

    /** ¿Tiene TAB pulsado ahora mismo? Lo pone InstantTransmissionHoldPacket en el flanco de
     *  pulsar/soltar, no cada tick. */
    private boolean holding = false;

    /** Ticks CONSECUTIVOS con TAB pulsado y el jugador quieto (calculado en servidor, nunca
     *  por un flag del cliente). Se resetea a 0 en cuanto se mueve o suelta. */
    private int stillTicks = 0;

    /** true en cuanto stillTicks llega a MENU_ARM_TICKS. Mientras esté activo, soltar TAB NO
     *  dispara el blink — es el gancho donde el menú de planetas se abre de verdad. */
    private boolean menuArmed = false;

    /** Cooldown restante tras el último blink, en ticks. */
    private int cooldownTicks = 0;

    /** Posición del tick ANTERIOR, para que InstantTransmissionSystem decida "quieto" por
     *  desplazamiento real en vez de fiarse de un flag de cliente. NaN = sin dato todavía
     *  (primer tick tras entrar al mundo/comprar la skill): se trata como "quieto" ese tick.
     *  Transitorio: no se guarda en NBT, se reconstruye solo con la posición actual. */
    private double lastX = Double.NaN, lastY = Double.NaN, lastZ = Double.NaN;

    /** Último valor de cooldown mandado al cliente (para no repetir el mismo paquete cada
     *  tick sin necesidad). Transitorio: -1 fuerza un primer envío. */
    private int lastSyncedCooldown = -1;

    /** Último valor de "quietud" (0 si no está pulsado/quieto) mandado al cliente — lo usa
     *  InstantTransmissionCrosshairOverlay para saber si ya se cruzó MENU_ARM_TICKS y teñir el
     *  ícono de la mira. Mismo criterio que lastSyncedCooldown, evita repetir el paquete cuando
     *  el número no cambió (p. ej. dos ticks seguidos sin tecla pulsada). Transitorio: -1 fuerza
     *  un primer envío. */
    private int lastSyncedStillTicks = -1;

    /** `holding` del tick ANTERIOR, para que InstantTransmissionSystem detecte el flanco de
     *  bajada (soltar TAB) sin depender de un packet aparte. Transitorio. */
    private boolean wasHolding = false;

    /** IDs de TeleportDestination ya descubiertos (TeleportDiscoverySystem, vía
     *  ProtectedZones.protectorAt). Sin coordenadas: la posición de llegada es FIJA, ver
     *  TeleportAnchors — esto es solo el booleano "¿ya lo encontró?". */
    private final Set<String> discoveredIds = new HashSet<>();

    /** ResourceLocation.toString() de cada dimensión en la que el jugador ha estado alguna vez
     *  — gobierna si el "planeta" correspondiente aparece siquiera en el selector de nivel 1
     *  del menú (InstantTransmissionMenuScreen). El Overworld queda cubierto solo con estar
     *  vivo un tick ahí, que es lo normal desde el primer instante de cualquier partida. */
    private final Set<String> visitedDimensionIds = new HashSet<>();

    /** Posición de la ÚLTIMA llegada del jugador a cada dimensión GENÉRICA (cualquiera que no
     *  sea Overworld/Otherworld, las dos únicas con anclas fijas compartidas propias — ver
     *  TeleportAnchors), guardada por DimensionEntryTracker en PlayerChangedDimensionEvent.
     *  Generalización de lo que antes era `lastNetherPortalPos` (un solo BlockPos, solo para el
     *  Nether) a CUALQUIER dimensión de CUALQUIER mod — pedido explícito del usuario: "que el
     *  NetherPortalTracker se pueda hacer de manera universal para cada dimensión modeada".
     *  ÚNICA excepción real a "las posiciones de destino son fijas" (ver el comentario de clase
     *  de arriba): aquí no hay un punto compartido razonable para una dimensión arbitraria, así
     *  que el destino tiene que ser POR JUGADOR. Clave = ResourceKey&lt;Level&gt;.location().
     *  toString(), mismo criterio que discoveredIds/visitedDimensionIds. Persistido en NBT. */
    private final Map<String, BlockPos> lastEntryPos = new HashMap<>();

    public static InstantTransmissionAttachment get(Player p) {
        return p.getData(ZenkaiDataAttachments.INSTANT_TRANSMISSION.get());
    }

    public boolean isHolding() { return holding; }
    public void setHolding(boolean v) { this.holding = v; }

    public int getStillTicks() { return stillTicks; }
    public void setStillTicks(int v) { this.stillTicks = Math.max(0, v); }

    public boolean isMenuArmed() { return menuArmed; }
    public void setMenuArmed(boolean v) { this.menuArmed = v; }

    public int getCooldownTicks() { return cooldownTicks; }
    public void setCooldownTicks(int v) { this.cooldownTicks = Math.max(0, v); }

    public int getLastSyncedCooldown() { return lastSyncedCooldown; }
    public void setLastSyncedCooldown(int v) { this.lastSyncedCooldown = v; }

    public int getLastSyncedStillTicks() { return lastSyncedStillTicks; }
    public void setLastSyncedStillTicks(int v) { this.lastSyncedStillTicks = v; }

    public boolean wasHolding() { return wasHolding; }
    public void setWasHolding(boolean v) { this.wasHolding = v; }

    /** ¿Se movió (en horizontal+vertical) desde el último tick más allá de un umbral mínimo?
     *  Actualiza la posición guardada de paso, así que solo debe llamarse UNA vez por tick. */
    public boolean advanceStillnessAndCheckMoved(double x, double y, double z) {
        boolean moved;
        if (Double.isNaN(lastX)) {
            moved = false; // primer tick con dato: no penalizar como "se movió"
        } else {
            double dx = x - lastX, dy = y - lastY, dz = z - lastZ;
            moved = (dx * dx + dy * dy + dz * dz) > 0.0025; // ~0.05 bloques/tick
        }
        lastX = x; lastY = y; lastZ = z;
        return moved;
    }

    /** Cierre común de un gesto (soltar TAB, o cancelarlo a medio camino): vuelve al estado
     *  de reposo sin tocar el cooldown, que se gestiona aparte. */
    public void resetGesture() {
        stillTicks = 0;
        menuArmed = false;
    }

    // ── Fase 2: descubrimiento (booleano) y dimensiones visitadas ───────────

    public boolean isDiscovered(TeleportDestination dest) {
        return !dest.requiresDiscovery() || discoveredIds.contains(dest.id());
    }

    /** @return true si esto lo descubrió AHORA (primera vez) — el llamador lo usa para saber
     *  si hace falta fijar la posición compartida y/o resincronizar al cliente. */
    public boolean markDiscovered(TeleportDestination dest) {
        return discoveredIds.add(dest.id());
    }

    public boolean hasVisitedDimension(ResourceKey<Level> dimension) {
        return visitedDimensionIds.contains(dimension.location().toString());
    }

    /** @return true si esta dimensión se marca visitada AHORA por primera vez. */
    public boolean markDimensionVisited(ResourceKey<Level> dimension) {
        return visitedDimensionIds.add(dimension.location().toString());
    }

    public Set<String> discoveredIdsView() { return Set.copyOf(discoveredIds); }
    public Set<String> visitedDimensionIdsView() { return Set.copyOf(visitedDimensionIds); }

    public BlockPos getLastEntryPos(ResourceKey<Level> dim) { return lastEntryPos.get(dim.location().toString()); }
    public void setLastEntryPos(ResourceKey<Level> dim, BlockPos pos) {
        lastEntryPos.put(dim.location().toString(), pos);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        // "holding" NO se guarda: es intención de tecla en vivo, no estado persistente — se
        // reconstruye solo del primer packet que llegue tras reconectar (mismo criterio que
        // PlayerFormAttachment con transformHeld, que tampoco sobrevive a un reload de NBT).
        tag.putInt("cooldownTicks", cooldownTicks);
        tag.put("discovered", stringList(discoveredIds));
        tag.put("visitedDimensions", stringList(visitedDimensionIds));
        ListTag entries = new ListTag();
        for (Map.Entry<String, BlockPos> e : lastEntryPos.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("dim", e.getKey());
            entry.putLong("pos", e.getValue().asLong());
            entries.add(entry);
        }
        tag.put("lastEntryPos", entries);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.cooldownTicks = tag.getInt("cooldownTicks");
        this.holding = false;
        this.stillTicks = 0;
        this.menuArmed = false;

        discoveredIds.clear();
        readStringList(tag, "discovered", discoveredIds);
        visitedDimensionIds.clear();
        readStringList(tag, "visitedDimensions", visitedDimensionIds);

        lastEntryPos.clear();
        // Migración desde el campo viejo (un solo BlockPos, solo Nether) — sin esto, un mundo
        // ya generado antes de generalizar el sistema perdería el punto de portal ya grabado.
        if (tag.contains("lastNetherPortalPos")) {
            lastEntryPos.put(Level.NETHER.location().toString(), BlockPos.of(tag.getLong("lastNetherPortalPos")));
        }
        if (tag.contains("lastEntryPos")) {
            ListTag entries = tag.getList("lastEntryPos", Tag.TAG_COMPOUND);
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag entry = entries.getCompound(i);
                lastEntryPos.put(entry.getString("dim"), BlockPos.of(entry.getLong("pos")));
            }
        }
    }

    private static ListTag stringList(Set<String> values) {
        ListTag list = new ListTag();
        for (String v : values) list.add(StringTag.valueOf(v));
        return list;
    }

    private static void readStringList(CompoundTag tag, String key, Set<String> out) {
        if (!tag.contains(key)) return;
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
    }
}
