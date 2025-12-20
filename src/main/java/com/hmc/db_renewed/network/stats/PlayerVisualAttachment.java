package com.hmc.db_renewed.network.stats;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Attachment SOLO para datos visuales/cosméticos del jugador.
 * No debe afectar directamente el cálculo de daño, TP, etc.
 *
 * Uso:
 *  - “Race Skin” tipo armadura GeckoLib (slot virtual, NO ocupa slots reales).
 *  - Colores (pelo/ojos/aura).
 *  - IDs de estilos (pelo/aura/outfit).
 *  - formStage (para futuro, sin lógica de stats aquí).
 */
public class PlayerVisualAttachment {

    // =========================================================
    //  Race Skin (armadura GeckoLib virtual)
    // =========================================================

    /** Item id del “skin racial” (armadura geckolib que renderizas encima). Ej: "db_renewed:namekian_race_skin" */
    private String raceSkinItemId = "";

    /** Renderizar el skin racial (layer). */
    private boolean renderRaceSkin = true;

    /** Ocultar el cuerpo vanilla (piel/modelo base), dejando raceSkin + armadura real. */
    private boolean hideVanillaBody = true;

    // =========================================================
    //  Colores básicos (RGB)
    // =========================================================
    private int hairColorRgb = 0xF4D03F;  // rubio tipo SSJ por defecto
    private int eyeColorRgb  = 0x2E86C1;  // azul
    private int auraColorRgb = 0x33CCFF;  // mismo default que usas en stats

    // =========================================================
    //  IDs de estilos/modelos
    // =========================================================
    private String hairStyleId = "base";       // "base", "saiyan_spiky_1", etc.
    private String auraStyleId = "none";       // "none", "basic", "flame", etc.
    private String outfitId    = "gi_default"; // futura ropa

    // =========================================================
    //  Forma / transformación (futuro)
    // =========================================================
    private int formStage = 0; // 0=base

    public PlayerVisualAttachment() {}

    // ---------- Acceso estático (igual que PlayerStatsAttachment) ----------
    public static PlayerVisualAttachment get(@NotNull Player player) {
        return player.getData(DataAttachments.PLAYER_VISUAL.get());
    }

    // =========================================================
    //  Race Skin API
    // =========================================================

    public String getRaceSkinItemId() { return raceSkinItemId; }

    public void setRaceSkinItemId(String id) {
        this.raceSkinItemId = (id == null) ? "" : id;
    }

    public boolean shouldRenderRaceSkin() { return renderRaceSkin; }
    public void setRenderRaceSkin(boolean v) { this.renderRaceSkin = v; }

    public boolean shouldHideVanillaBody() { return hideVanillaBody; }
    public void setHideVanillaBody(boolean v) { this.hideVanillaBody = v; }

    public boolean hasRaceSkin() {
        return raceSkinItemId != null && !raceSkinItemId.isEmpty();
    }

    /** Stack virtual SOLO para render (NO se equipa en slots reales). */
    public ItemStack getRaceSkinStack() {
        if (!hasRaceSkin()) return ItemStack.EMPTY;

        ResourceLocation rl = ResourceLocation.tryParse(raceSkinItemId);
        if (rl == null) return ItemStack.EMPTY;

        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null) return ItemStack.EMPTY;

        return new ItemStack(item);
    }

    /** Helper opcional: defaults por raza (ajusta ids a los reales). */
    public void applyDefaultsForRace(Race race) {
        switch (race) {
            case NAMEKIAN -> setRaceSkinItemId("db_renewed:namekian_race_skin");
            case SAIYAN   -> setRaceSkinItemId("db_renewed:saiyan_race_skin");
            case ARCOSIAN -> setRaceSkinItemId("db_renewed:arcosian_race_skin");
            case MAJIN    -> setRaceSkinItemId("db_renewed:majin_race_skin");
            case HUMAN    -> setRaceSkinItemId("");
        }
    }

    // =========================================================
    //  Cosméticos API
    // =========================================================

    public int getHairColorRgb() { return hairColorRgb; }
    public void setHairColorRgb(int rgb) { this.hairColorRgb = rgb; }

    public int getEyeColorRgb() { return eyeColorRgb; }
    public void setEyeColorRgb(int rgb) { this.eyeColorRgb = rgb; }

    public int getAuraColorRgb() { return auraColorRgb; }
    public void setAuraColorRgb(int rgb) { this.auraColorRgb = rgb; }

    public String getHairStyleId() { return hairStyleId; }
    public void setHairStyleId(String id) {
        if (id != null && !id.isEmpty()) this.hairStyleId = id;
    }

    public String getAuraStyleId() { return auraStyleId; }
    public void setAuraStyleId(String id) {
        if (id != null && !id.isEmpty()) this.auraStyleId = id;
    }

    public String getOutfitId() { return outfitId; }
    public void setOutfitId(String id) {
        if (id != null && !id.isEmpty()) this.outfitId = id;
    }

    public int getFormStage() { return formStage; }
    public void setFormStage(int formStage) {
        this.formStage = Math.max(0, formStage);
    }

    // =========================================================
    //  NBT (para AttachmentType.serialize con tu Codec)
    // =========================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        // Race skin
        tag.putString("raceSkinItemId", raceSkinItemId == null ? "" : raceSkinItemId);
        tag.putBoolean("renderRaceSkin", renderRaceSkin);
        tag.putBoolean("hideVanillaBody", hideVanillaBody);

        // Colores
        tag.putInt("hairColor", hairColorRgb);
        tag.putInt("eyeColor", eyeColorRgb);
        tag.putInt("auraColor", auraColorRgb);

        // IDs
        tag.putString("hairStyleId", hairStyleId == null ? "base" : hairStyleId);
        tag.putString("auraStyleId", auraStyleId == null ? "none" : auraStyleId);
        tag.putString("outfitId", outfitId == null ? "gi_default" : outfitId);

        // Forma
        tag.putInt("formStage", formStage);

        return tag;
    }

    public void load(CompoundTag tag) {
        // Race skin
        this.raceSkinItemId = tag.getString("raceSkinItemId");
        this.renderRaceSkin = tag.getBoolean("renderRaceSkin");
        this.hideVanillaBody = tag.getBoolean("hideVanillaBody");

        // Colores (si no existen, se quedan en defaults)
        if (tag.contains("hairColor")) this.hairColorRgb = tag.getInt("hairColor");
        if (tag.contains("eyeColor"))  this.eyeColorRgb  = tag.getInt("eyeColor");
        if (tag.contains("auraColor")) this.auraColorRgb = tag.getInt("auraColor");

        // IDs
        if (tag.contains("hairStyleId")) this.hairStyleId = tag.getString("hairStyleId");
        if (tag.contains("auraStyleId")) this.auraStyleId = tag.getString("auraStyleId");
        if (tag.contains("outfitId"))    this.outfitId    = tag.getString("outfitId");

        // Forma
        this.formStage = Math.max(0, tag.getInt("formStage"));
    }
}
