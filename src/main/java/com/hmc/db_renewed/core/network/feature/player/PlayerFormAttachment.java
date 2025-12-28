package com.hmc.db_renewed.core.network.feature.player;

import com.hmc.db_renewed.core.network.feature.Race;
import com.hmc.db_renewed.core.network.feature.race.forms.FormIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public class PlayerFormAttachment {

    // Input hold (ALT+C)
    private boolean transformHeld = false;

    // Estado del “proceso” (lock / anim charge)
    private boolean transforming = false;

    // Progreso del hold
    private int holdTicks = 0;

    // Forma actual (ResourceLocation)
    private ResourceLocation formId = FormIds.BASE;

    // Anti-spam
    private int cooldownTicks = 0;

    public static final int HOLD_REQUIRED_TICKS = 100; // 5 segundos

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
            // si suelta, se corta el proceso
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
     * - Carga hold 100 ticks
     * - Completa BASE->SSJ1 para Saiyan
     * - (Luego aquí metes drain configurable por forma)
     */
    public boolean serverTick(Player p, PlayerStatsAttachment stats, PlayerVisualAttachment visual) {
        boolean dirty = false;

        // cooldown
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

        // Decidir target de transformación según raza + forma actual
        ResourceLocation target = getTargetForm(stats.getRace(), formId);
        if (target == null) {
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

        // (Opcional) si quieres UI/anim más fluida puedes sync cada X ticks:
        // if (holdTicks % 5 == 0) dirty = true;

        if (holdTicks >= HOLD_REQUIRED_TICKS) {
            // Aplicar forma
            setFormId(target);
            dirty = true; // <-- CLAVE: avisar al caller para sync inmediato

            // Reset proceso: cortar anim aunque siga holdeando teclas
            transforming = false;
            holdTicks = 0;

            // Obligarlo a soltar para evitar encadenar
            transformHeld = false;

            // Cooldown pequeño por lag
            cooldownTicks = 10;
        }

        return dirty;
    }

    /**
     * MVP: Solo Saiyan BASE -> SSJ1.
     * Luego aquí metes ssj1->ssj2, freezer forms, kaioken, rutas, etc.
     */
    private ResourceLocation getTargetForm(Race race, ResourceLocation current) {
        if (race == Race.SAIYAN && FormIds.BASE.equals(current)) {
            return FormIds.SSJ1;
        }
        return null;
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
        cooldownTicks = 10; // opcional para evitar spam
    }

}