package com.hmc.zenkai.core.network.feature.player;

import com.hmc.zenkai.core.network.feature.Race;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PlayerVisualAttachment {

    // ── Race Skin ─────────────────────────────────────────────────────────────
    private String  raceSkinItemId  = "";
    private boolean renderRaceSkin  = true;
    private boolean hideVanillaBody = true;

    // ── Colores ───────────────────────────────────────────────────────────────
    private int skinColorRgb   = 0xD5A07A; // piel humana por defecto
    private int hairColorRgb   = 0x000000; // negro por defecto
    private int eyeColorRgb    = 0x000000; // negro por defecto
    private int auraColorRgb   = 0x33CCFF; // ki azul
    private int detailColorRgb = 0x9B59B6; // detalles del cuerpo (Arcosian / Namek capa 2)
    private int lineColorRgb   = 0x2E7D32; // líneas de detalle del cuerpo (Namek capa 3)
    // ── Colores por layer numerado (escalable: canal "layer" + índice en el JSON) ──
    private final java.util.List<Integer> layerColors = new java.util.ArrayList<>();

    // ── Índices de forma (apuntan a CustomizationAssets) ─────────────────────
    private int eyeIndex   = 1;
    private int hairIndex  = 1;
    private int mouthIndex = 1;
    private int noseIndex  = 0;

    // ── IDs de estilo (legacy / futuro) ──────────────────────────────────────
    private String hairStyleId = "hair1";
    private String auraStyleId = "none";
    private String outfitId    = "gi_default";

    // ── Piel: modo y preset ───────────────────────────────────────────────────
    private boolean customSkinColor = false; // Human/Saiyan/Majin: false=natural, true=color custom (tinte)
    private int     skinPreset      = 0;     // Namekian/Arcosian: índice de textura preset

    // ── Género (Human/Saiyan/Majin): cambia modelo + textura ───────────────────
    public enum Gender { MALE, FEMALE }
    private Gender gender = Gender.MALE;

    // ── Transformación ───────────────────────────────────────────────────────
    private int formStage = 0;

    public PlayerVisualAttachment() {}

    // ── Acceso estático ───────────────────────────────────────────────────────
    public static PlayerVisualAttachment get(@NotNull Player player) {
        return player.getData(DataAttachments.PLAYER_VISUAL.get());
    }

    // ── Race Skin API ─────────────────────────────────────────────────────────
    public String  getRaceSkinItemId()           { return raceSkinItemId; }
    public boolean shouldRenderRaceSkin()        { return renderRaceSkin; }
    public boolean shouldHideVanillaBody()       { return hideVanillaBody; }
    public void setRaceSkinItemId(String id)     { this.raceSkinItemId  = (id == null) ? "" : id; }
    public void setRenderRaceSkin(boolean v)     { this.renderRaceSkin  = v; }
    public void setHideVanillaBody(boolean v)    { this.hideVanillaBody = v; }

    public boolean hasRaceSkin() {
        return raceSkinItemId != null && !raceSkinItemId.isEmpty();
    }


    public static int defaultSkinColorFor(Race race) {
        return switch (race) {
            case NAMEKIAN -> 0x2DC31E;
            case ARCOSIAN -> 0xEDEDED;
            default        -> 0xD5A07A;
        };
    }


    // ── Colores API ───────────────────────────────────────────────────────────
    public int  getSkinColorRgb()              { return skinColorRgb; }
    public void setSkinColorRgb(int rgb)       { this.skinColorRgb   = rgb & 0xFFFFFF; }

    public int  getHairColorRgb()              { return hairColorRgb; }
    public void setHairColorRgb(int rgb)       { this.hairColorRgb   = rgb & 0xFFFFFF; }

    public int  getEyeColorRgb()               { return eyeColorRgb; }
    public void setEyeColorRgb(int rgb)        { this.eyeColorRgb    = rgb & 0xFFFFFF; }

    public int  getAuraColorRgb()              { return auraColorRgb; }
    public void setAuraColorRgb(int rgb)       { this.auraColorRgb   = rgb & 0xFFFFFF; }

    // ── Piel: modo y preset API ───────────────────────────────────────────────
    public boolean isCustomSkinColor()         { return customSkinColor; }
    public void    setCustomSkinColor(boolean v) { this.customSkinColor = v; }

    public int  getSkinPreset()                { return skinPreset; }
    public void setSkinPreset(int i)           { this.skinPreset = Math.max(0, i); }

    public Gender getGender()                  { return gender; }
    public void   setGender(Gender g)          { if (g != null) this.gender = g; }

    // ── Índices de forma API ──────────────────────────────────────────────────
    public int  getEyeIndex()                  { return eyeIndex; }
    public void setEyeIndex(int i)             { this.eyeIndex   = Math.max(0, i); }

    public int  getHairIndex()                 { return hairIndex; }
    public void setHairIndex(int i)            { this.hairIndex  = Math.max(0, i); }

    public int  getMouthIndex()                { return mouthIndex; }
    public void setMouthIndex(int i)           { this.mouthIndex = Math.max(0, i); }

    public int  getNoseIndex()                 { return noseIndex; }
    public void setNoseIndex(int i)            { this.noseIndex  = Math.max(0, i); }

    // ── IDs de estilo (legacy) ────────────────────────────────────────────────
    public String getHairStyleId()             { return hairStyleId; }
    public void   setHairStyleId(String id)    { if (id != null && !id.isEmpty()) this.hairStyleId = id; }

    public String getAuraStyleId()             { return auraStyleId; }
    public void   setAuraStyleId(String id)    { if (id != null && !id.isEmpty()) this.auraStyleId = id; }

    public String getOutfitId()                { return outfitId; }
    public void   setOutfitId(String id)       { if (id != null && !id.isEmpty()) this.outfitId    = id; }

    // ── Transformación ───────────────────────────────────────────────────────
    public int  getFormStage()                 { return formStage; }
    public void setFormStage(int v)            { this.formStage = Math.max(0, v); }

    // ── NBT ──────────────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putString("raceSkinItemId",  raceSkinItemId == null ? "" : raceSkinItemId);
        tag.putBoolean("renderRaceSkin",  renderRaceSkin);
        tag.putBoolean("hideVanillaBody", hideVanillaBody);

        tag.putInt("skinColor",   skinColorRgb);
        tag.putInt("hairColor",   hairColorRgb);
        tag.putInt("eyeColor",    eyeColorRgb);
        tag.putInt("auraColor",   auraColorRgb);
        tag.putInt("detailColor", detailColorRgb);
        tag.putInt("lineColor",   lineColorRgb);

        tag.putInt("eyeIndex",   eyeIndex);
        tag.putInt("hairIndex",  hairIndex);
        tag.putInt("mouthIndex", mouthIndex);
        tag.putInt("noseIndex",  noseIndex);

        tag.putString("hairStyleId", hairStyleId == null ? "base"       : hairStyleId);
        tag.putString("auraStyleId", auraStyleId == null ? "none"       : auraStyleId);
        tag.putString("outfitId",    outfitId    == null ? "gi_default" : outfitId);

        tag.putInt("formStage", formStage);

        tag.putBoolean("customSkinColor", customSkinColor);
        tag.putInt("skinPreset", skinPreset);
        tag.putString("gender", gender.name());

        int[] lc = new int[layerColors.size()];
        for (int i = 0; i < lc.length; i++) lc[i] = layerColors.get(i);
        tag.putIntArray("layerColors", lc);

        return tag;
    }

    public void load(CompoundTag tag) {
        this.raceSkinItemId  = tag.getString("raceSkinItemId");

        if (tag.contains("renderRaceSkin"))  this.renderRaceSkin  = tag.getBoolean("renderRaceSkin");
        if (tag.contains("hideVanillaBody")) this.hideVanillaBody = tag.getBoolean("hideVanillaBody");

        if (tag.contains("skinColor"))   this.skinColorRgb   = tag.getInt("skinColor");
        if (tag.contains("hairColor"))   this.hairColorRgb   = tag.getInt("hairColor");
        if (tag.contains("eyeColor"))    this.eyeColorRgb    = tag.getInt("eyeColor");
        if (tag.contains("auraColor"))   this.auraColorRgb   = tag.getInt("auraColor");
        if (tag.contains("detailColor")) this.detailColorRgb = tag.getInt("detailColor");
        if (tag.contains("lineColor"))   this.lineColorRgb   = tag.getInt("lineColor");

        if (tag.contains("eyeIndex"))   this.eyeIndex   = Math.max(0, tag.getInt("eyeIndex"));
        if (tag.contains("hairIndex"))  this.hairIndex  = Math.max(0, tag.getInt("hairIndex"));
        if (tag.contains("mouthIndex")) this.mouthIndex = Math.max(0, tag.getInt("mouthIndex"));
        if (tag.contains("noseIndex"))  this.noseIndex  = Math.max(0, tag.getInt("noseIndex"));

        if (tag.contains("hairStyleId")) this.hairStyleId = tag.getString("hairStyleId");
        if (tag.contains("auraStyleId")) this.auraStyleId = tag.getString("auraStyleId");
        if (tag.contains("outfitId"))    this.outfitId    = tag.getString("outfitId");

        if (tag.contains("formStage")) this.formStage = Math.max(0, tag.getInt("formStage"));

        if (tag.contains("customSkinColor")) this.customSkinColor = tag.getBoolean("customSkinColor");
        if (tag.contains("skinPreset"))      this.skinPreset      = Math.max(0, tag.getInt("skinPreset"));
        if (tag.contains("gender")) {
            try { this.gender = Gender.valueOf(tag.getString("gender")); } catch (IllegalArgumentException ignored) {}
        }

        layerColors.clear();
        if (tag.contains("layerColors")) {
            for (int v : tag.getIntArray("layerColors")) layerColors.add(v < 0 ? -1 : (v & 0xFFFFFF));
        }

    }

    /** ¿El jugador ha FIJADO explícitamente el color del layer i? (si no, se usa el default del JSON). */
    public boolean hasLayerColor(int i) {
        return i >= 0 && i < layerColors.size() && layerColors.get(i) >= 0;
    }

    /** Color del layer numerado i, o -1 si no está fijado (usa el default del JSON en ese caso). */
    public int getLayerColorRgb(int i) {
        return (i >= 0 && i < layerColors.size()) ? layerColors.get(i) : -1;
    }

    /** Fija el color del layer i. Autoextiende con -1 (sin fijar) los huecos intermedios. */
    public void setLayerColorRgb(int i, int rgb) {
        if (i < 0) return;
        while (layerColors.size() <= i) layerColors.add(-1);
        layerColors.set(i, rgb & 0xFFFFFF);
    }

    public int layerColorCount() { return layerColors.size(); }

    /** Olvida TODOS los overrides de capas (vuelven a los defaults del JSON). Usar al cambiar de raza. */
    public void clearLayerColors() {
        layerColors.clear();
    }
}