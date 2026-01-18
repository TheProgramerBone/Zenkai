package com.hmc.db_renewed.core.network.feature.player;

import com.hmc.db_renewed.core.network.feature.Race;
import com.hmc.db_renewed.core.network.feature.forms.FormDefinition;
import com.hmc.db_renewed.core.network.feature.forms.FormIds;
import com.hmc.db_renewed.core.network.feature.forms.FormRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class PlayerFormAttachment {

    private boolean transformHeld = false;
    private boolean transforming = false;
    private int holdTicks = 0;
    private ResourceLocation formId = FormIds.BASE;
    private int cooldownTicks = 0;

    // ---------------- Getters ----------------
    public boolean isTransformHeld() { return transformHeld; }
    public boolean isTransforming() { return transforming; }
    public int getHoldTicks() { return holdTicks; }
    public ResourceLocation getFormId() { return formId; }
    public int getCooldownTicks() { return cooldownTicks; }

    // ---------------- Setters ----------------
    public void setTransformHeld(boolean held) {
        this.transformHeld = held;
        if (!held) {
            this.transforming = false;
            this.holdTicks = 0;
        }
    }

    public void setFormId(ResourceLocation id) {
        this.formId = (id == null) ? FormIds.BASE : id;
    }

    public void resetAll() {
        transformHeld = false;
        transforming = false;
        holdTicks = 0;
        formId = FormIds.BASE;
        cooldownTicks = 0;
    }

    /**
     * Tick SOLO SERVIDOR.
     * @return dirty si cambió algo importante y conviene sync inmediato.
     */
    public boolean serverTick(Player p, PlayerStatsAttachment stats, PlayerVisualAttachment visual) {
        boolean dirty = false;

        if (cooldownTicks > 0) cooldownTicks--;

        // Gate: si no está holdeando o en cooldown, limpiar proceso
        if (!transformHeld || cooldownTicks > 0) {
            if (transforming || holdTicks != 0) {
                transforming = false;
                holdTicks = 0;
                dirty = true;
            }
            return dirty;
        }

        // Resolver target via registry (y validación por raza)
        ResourceLocation target = resolveNextForm(stats.getRace(), formId);
        if (target == null) {
            // NO hay transformación configurada para esta raza/forma
            if (transforming || holdTicks != 0) {
                transforming = false;
                holdTicks = 0;
                dirty = true;
            }
            return dirty;
        }

        // Hold requerido es el de la forma DESTINO
        int required = FormRegistry.get(target).holdTicksRequired();
        if (required <= 0) {
            // Si por error la forma destino no tiene hold válido, no hacemos nada.
            if (transforming || holdTicks != 0) {
                transforming = false;
                holdTicks = 0;
                dirty = true;
            }
            return dirty;
        }

        // Cargar
        transforming = true;
        holdTicks++;

        // Si quieres UI ultra fluida: cada X ticks puedes dirty=true para sync “progress”
        // if (holdTicks % 5 == 0) dirty = true;

        if (holdTicks >= required) {
            setFormId(target);
            dirty = true;

            transforming = false;
            holdTicks = 0;

            // obligar a soltar para evitar chain infinito
            transformHeld = false;

            cooldownTicks = 10;
        }

        return dirty;
    }

    /**
     * Devuelve la siguiente forma para esta raza/forma actual, o null si no aplica.
     */
    private static ResourceLocation resolveNextForm(Race race, ResourceLocation current) {
        FormDefinition curDef = FormRegistry.get(current);
        ResourceLocation next = curDef.nextFormId();
        if (next == null) return null;

        FormDefinition nextDef = FormRegistry.get(next);
        if (nextDef == null) return null;

        return nextDef.allowedRaces().contains(race) ? next : null;
    }

    /**
     * Helper útil para el CLIENTE: saber si existe transform posible desde el estado actual.
     */
    public static boolean canTransformFrom(Race race, ResourceLocation current) {
        return resolveNextForm(race, current) != null;
    }

    // ---------------- NBT ----------------
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("transformHeld", transformHeld);
        tag.putBoolean("transforming", transforming);
        tag.putInt("holdTicks", holdTicks);
        tag.putString("formId", formId.toString());
        tag.putInt("cooldownTicks", cooldownTicks);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.transformHeld = tag.getBoolean("transformHeld");
        this.transforming = tag.getBoolean("transforming");
        this.holdTicks = tag.getInt("holdTicks");

        if (tag.contains("formId")) {
            ResourceLocation rl = ResourceLocation.tryParse(tag.getString("formId"));
            this.formId = (rl == null) ? FormIds.BASE : rl;
        } else {
            this.formId = FormIds.BASE;
        }

        this.cooldownTicks = tag.getInt("cooldownTicks");
    }

    public void forceBase() {
        setFormId(FormIds.BASE);
        transformHeld = false;
        transforming = false;
        holdTicks = 0;
        cooldownTicks = 10;
    }
}
