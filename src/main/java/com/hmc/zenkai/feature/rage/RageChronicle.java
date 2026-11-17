package com.hmc.zenkai.feature.rage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Crónica de Rage Forms: la historia de lo que le pasó al jugador (acontecimiento, forma, vía, resultado, fecha, maestría).
 * Es HISTORIA, no una fuente de poder: sobrevive a la muerte y a la reencarnación como registro (por eso el attachment va a
 * {@code CharacterReset.KEPT}) y no concede nada al personaje nuevo. Anillo de {@link #CAP} entradas: al pasar, cae la más
 * antigua, para no inflar el NBT del jugador. No se sincroniza al cliente por defecto: la pantalla la pide bajo demanda.
 */
public final class RageChronicle {
    public static final int CAP = 200;

    /** Qué clase de entrada es: un acontecimiento de {@link RageTrigger} o la primera transformación de una forma. */
    public static final String FORM_FIRST = "FORM_FIRST";

    /**
     * @param eventId        id del hecho (UUID del muerto + tiempo, etc.); vacío si no aplica
     * @param trigger        nombre del {@link RageTrigger} o {@link #FORM_FIRST}
     * @param targetForm     forma relacionada («namespace:path»); vacía si ninguna
     * @param result         nombre de {@link RageResult}
     * @param acquisition    «rage» si la dio este sistema; «other» para cualquier otra vía (entrenamiento, ritual, compra…)
     * @param related        a quién se refiere el hecho (nombre del aliado o de la mascota); vacío si nadie
     * @param worldTime      {@code gameTime} del servidor
     * @param masteryBefore  maestría de la forma (en %) antes; -1 si no aplica
     * @param masteryAfter   idem, después
     * @param cinematicPlayed se reprodujo (o se mandó reproducir) la secuencia larga de esa forma
     * @param interrupted    el jugador canceló la presentación (solo la capa visual; el estado ya estaba escrito)
     * @param ignoredByCooldown el hecho se anotó pero el enfriamiento impidió recompensarlo
     */
    public record Entry(String eventId, String trigger, String targetForm, String result, String acquisition, String related,
                        long worldTime, float masteryBefore, float masteryAfter, boolean cinematicPlayed,
                        boolean interrupted, boolean ignoredByCooldown) {

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("eventId", eventId);
            t.putString("trigger", trigger);
            t.putString("targetForm", targetForm);
            t.putString("result", result);
            t.putString("acquisition", acquisition);
            t.putString("related", related);
            t.putLong("worldTime", worldTime);
            t.putFloat("masteryBefore", masteryBefore);
            t.putFloat("masteryAfter", masteryAfter);
            t.putBoolean("cinematicPlayed", cinematicPlayed);
            t.putBoolean("interrupted", interrupted);
            t.putBoolean("ignoredByCooldown", ignoredByCooldown);
            return t;
        }

        static Entry load(CompoundTag t) {
            return new Entry(t.getString("eventId"), t.getString("trigger"), t.getString("targetForm"), t.getString("result"),
                    t.getString("acquisition"), t.getString("related"), t.getLong("worldTime"),
                    t.getFloat("masteryBefore"), t.getFloat("masteryAfter"), t.getBoolean("cinematicPlayed"),
                    t.getBoolean("interrupted"), t.getBoolean("ignoredByCooldown"));
        }
    }

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();

    public static RageChronicle get(Player p) {
        return p.getData(com.hmc.zenkai.registry.ZenkaiDataAttachments.RAGE_CHRONICLE.get());
    }

    /** Añade una entrada; el anillo descarta la más antigua al pasar de {@link #CAP}. */
    public void add(Entry e) {
        entries.addLast(e);
        while (entries.size() > CAP) entries.removeFirst();
    }

    /**
     * Marca como «interrumpida» la entrada MÁS RECIENTE con secuencia larga, si es de hace {@code windowTicks} como mucho y aún
     * no lo estaba. Devuelve false sin tocar nada si no hay ninguna así (la historia vieja no se reescribe).
     */
    public boolean markRecentInterrupted(long now, long windowTicks) {
        Entry[] all = entries.toArray(new Entry[0]);
        for (int i = all.length - 1; i >= 0; i--) {
            Entry e = all[i];
            if (now - e.worldTime() > windowTicks) return false;   // van en orden: las anteriores son aún más viejas
            if (!e.cinematicPlayed() || e.interrupted()) continue;
            all[i] = new Entry(e.eventId(), e.trigger(), e.targetForm(), e.result(), e.acquisition(), e.related(),
                    e.worldTime(), e.masteryBefore(), e.masteryAfter(), true, true, e.ignoredByCooldown());
            entries.clear();
            entries.addAll(java.util.Arrays.asList(all));
            return true;
        }
        return false;
    }

    /** Copia en orden cronológico (la más antigua primero). */
    public List<Entry> entries() { return Collections.unmodifiableList(new ArrayList<>(entries)); }

    public int size() { return entries.size(); }

    /** Vacía la crónica (herramienta de prueba/administración: la historia normal nunca se borra sola). */
    public void clear() { entries.clear(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Entry e : entries) list.add(e.save());
        tag.put("entries", list);
        return tag;
    }

    public void load(@Nullable CompoundTag tag) {
        entries.clear();
        if (tag == null) return;
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) add(Entry.load(list.getCompound(i)));
    }
}
