package com.hmc.zenkai.feature.teleport;

import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Estado servidor de la Transmisión Instantánea. Attachment PROPIO, deliberadamente separado
 * de PlayerStatsAttachment (pedido explícito del usuario al diseñar la Fase 1 — ver
 * .claude/pendiente/instant-transmission-pendiente.md) para que toda esta feature crezca en un
 * único sitio a lo largo de sus fases: hoy solo el hold/cooldown del blink de nivel 1; la Fase 2
 * añadirá aquí mismo el set de ubicaciones visitadas ("aparece donde estabas la última vez"),
 * no un segundo attachment.
 *
 * Dos gestos en la MISMA tecla (TAB), resueltos por InstantTransmissionSystem:
 *  - soltar antes de armar el menú -> blink instantáneo a lo que esté en la mira;
 *  - mantener pulsado y QUIETO 5s seguidos -> arma la apertura del menú de planetas (Fase 2)
 *    en vez del blink. Mantener pulsado NO congela el movimiento (a propósito: el jugador debe
 *    poder seguir esquivando mientras decide), así que stillTicks es un contador de QUIETUD,
 *    no de "tecla pulsada" — se resetea al moverse sin cancelar el hold en sí.
 */
public class InstantTransmissionAttachment {

    /** Ticks de "quieto" para armar el menú (5s). Mismo criterio que otros holds del mod
     *  (KAIOKEN_HOLD_TICKS en PlayerFormAttachment): la constante vive junto al estado que
     *  gobierna, no en el sistema de tick. */
    public static final int MENU_ARM_TICKS = 100;

    /** ¿Tiene TAB pulsado ahora mismo? Lo pone InstantTransmissionHoldPacket en el flanco de
     *  pulsar/soltar, no cada tick. */
    private boolean holding = false;

    /** Ticks CONSECUTIVOS con TAB pulsado y el jugador quieto (calculado en servidor, nunca
     *  por un flag del cliente). Se resetea a 0 en cuanto se mueve o suelta. */
    private int stillTicks = 0;

    /** true en cuanto stillTicks llega a MENU_ARM_TICKS. Mientras esté activo, soltar TAB NO
     *  dispara el blink — es el gancho donde la Fase 2 abrirá el menú de planetas de verdad. */
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

    /** `holding` del tick ANTERIOR, para que InstantTransmissionSystem detecte el flanco de
     *  bajada (soltar TAB) sin depender de un packet aparte. Transitorio. */
    private boolean wasHolding = false;

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

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        // "holding" NO se guarda: es intención de tecla en vivo, no estado persistente — se
        // reconstruye solo del primer packet que llegue tras reconectar (mismo criterio que
        // PlayerFormAttachment con transformHeld, que tampoco sobrevive a un reload de NBT).
        tag.putInt("cooldownTicks", cooldownTicks);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.cooldownTicks = tag.getInt("cooldownTicks");
        this.holding = false;
        this.stillTicks = 0;
        this.menuArmed = false;
    }
}
