package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.race.face.FaceCategory;
import com.hmc.zenkai.feature.race.face.LegacyFaceIds;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.feature.race.hair.CustomHairLibrary;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public class PlayerVisualAttachment {

    // ── Race Skin ─────────────────────────────────────────────────────────────
    // (raceSkinItemId, auraStyleId, outfitId y formStage se eliminaron el 2026-10-01: se
    // guardaban y sincronizaban pero ningún código los leía. Los saves viejos que los traigan
    // simplemente los ignoran.)
    private boolean renderRaceSkin  = true;
    private boolean hideVanillaBody = true;

    // ── Colores ───────────────────────────────────────────────────────────────
    private int skinColorRgb   = 0xD5A07A; // piel humana por defecto
    private int hairColorRgb   = 0x000000; // negro por defecto
    /**
     * Colores propios de los mechones de un pelo por código (StrandColors): true = como los trae
     * el código (p. ej. blanco con dos mechones negros), false = todo del color de pelo. Por
     * defecto true: es el aspecto que eligió el autor del peinado. Sin efecto si no trae colores.
     */
    private boolean hairStrandColors = true;
    private int eyeColorRgb    = 0x2E86C1; // azul, el mismo que propone la GUI (negro hacía invisible el iris)
    /** Iris derecho (heterocromía). -1 = igual que el izquierdo (eyeColorRgb), el caso normal. */
    private int eyeColorRightRgb = -1;
    private int auraColorRgb   = 0x33CCFF; // ki azul
    // ── Colores por layer numerado (escalable: canal "layer" + índice en el JSON) ──
    private final java.util.List<Integer> layerColors = new java.util.ArrayList<>();

    // ── Rasgos faciales: id del catálogo por categoría (FaceCatalog) ─────────
    // Sin entrada = "ninguno". El id es la identidad del rasgo; los antiguos eyeIndex/mouthIndex/
    // noseIndex solo se leen al cargar un save viejo (LegacyFaceIds), nunca se escriben.
    // Ojo por defecto: "minimal" desde que se retiraron los eyes_1..5 (2026-10-01). Debe coincidir
    // con el "default_for" del catálogo; si no, FaceBans.reconcile lo corrige igualmente.
    private static final ResourceLocation DEFAULT_EYES  =
            ResourceLocation.fromNamespaceAndPath("zenkai", "eyes/minimal");
    private static final ResourceLocation DEFAULT_MOUTH =
            ResourceLocation.fromNamespaceAndPath("zenkai", "mouth/neutral");
    private final EnumMap<FaceCategory, ResourceLocation> faceParts = new EnumMap<>(FaceCategory.class);
    {
        faceParts.put(FaceCategory.EYES, DEFAULT_EYES);
        faceParts.put(FaceCategory.MOUTH, DEFAULT_MOUTH);
    }

    // ── Peinado (S5): id del catálogo HairCatalog, "none" = calvo, "custom:..." = HairCode ──
    // Antes había DOS campos para lo mismo (hairIndex, índice en la lista de PNG del cliente, y
    // hairStyleId = "hair"+índice). Ahora solo este, con ids; los valores viejos se migran en
    // normalizeHairStyle ("hair1" -> zenkai:classic, "hair0"/"bald" -> none).
    public static final String HAIR_NONE = "none";
    private static final String DEFAULT_HAIR = "zenkai:classic";

    // ── IDs de estilo ────────────────────────────────────────────────────────
    private String hairStyleId = DEFAULT_HAIR;
    /** Estilo de la cola de Saiyan ("loose"/"waist"). Capricho cosmético SIN costo (rueda,
     *  mantener X) — quien decide si HAY cola es PlayerStatsAttachment.hasTail(), servicio de
     *  Kami. Sin efecto para nada que no sea Saiyan+hasTail (ver TailResolver). */
    private String tailStyleId = "loose";

    // ── Piel: modo ───────────────────────────────────────────────────
    private boolean customSkinColor = false; // Human/Saiyan/Majin: false=natural, true=color custom (tinte)

    // ── Aura: modo ────────────────────────────────────────────────────────────
    private boolean customAuraColor = false; // false=tinte por AlignmentTier (ver AuraColors), true=color fijado por el jugador

    // ── Género (Human/Saiyan/Majin): cambia modelo + textura ───────────────────
    public enum Gender { MALE, FEMALE }
    private Gender gender = Gender.MALE;

    public PlayerVisualAttachment() {}

    // ── Acceso estático ───────────────────────────────────────────────────────
    public static PlayerVisualAttachment get(@NotNull Player player) {
        return player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
    }

    // ── Race Skin API ─────────────────────────────────────────────────────────
    public boolean shouldRenderRaceSkin()        { return renderRaceSkin; }
    public boolean shouldHideVanillaBody()       { return hideVanillaBody; }
    public void setRenderRaceSkin(boolean v)     { this.renderRaceSkin  = v; }
    public void setHideVanillaBody(boolean v)    { this.hideVanillaBody = v; }


    public static int defaultSkinColorFor(Race race) {
        return switch (race) {
            case NAMEKIAN -> 0x2DC31E;
            // Base de la piel lavanda del arte de Kirbro (geo nueva, 2026-10-01): las capas
            // _layer_N guardan cada color RELATIVO a este tono, así que con él sale el arte exacto.
            case ARCOSIAN -> 0xDDD8EE;
            case MAJIN    -> 0xF2A0C4;
            default        -> 0xD5A07A;
        };
    }


    // ── Colores API ───────────────────────────────────────────────────────────
    public int  getSkinColorRgb()              { return skinColorRgb; }
    public void setSkinColorRgb(int rgb)       { this.skinColorRgb   = rgb & 0xFFFFFF; }

    public int  getHairColorRgb()              { return hairColorRgb; }
    public boolean isHairStrandColors()        { return hairStrandColors; }
    public void setHairStrandColors(boolean v) { this.hairStrandColors = v; }
    public void setHairColorRgb(int rgb)       { this.hairColorRgb   = rgb & 0xFFFFFF; }

    public int  getEyeColorRgb()               { return eyeColorRgb; }
    public void setEyeColorRgb(int rgb)        { this.eyeColorRgb    = rgb & 0xFFFFFF; }

    /** Color fijado para el iris derecho, o -1 si sigue al izquierdo. */
    public int  getEyeColorRightRgb()          { return eyeColorRightRgb; }
    /** -1 (o cualquier negativo) = sin heterocromía. */
    public void setEyeColorRightRgb(int rgb)   { this.eyeColorRightRgb = rgb < 0 ? -1 : (rgb & 0xFFFFFF); }
    /** Color efectivo del iris derecho: el propio si hay heterocromía, si no el izquierdo. */
    public int  resolvedEyeColorRightRgb()     { return eyeColorRightRgb >= 0 ? eyeColorRightRgb : eyeColorRgb; }

    public int  getAuraColorRgb()              { return auraColorRgb; }
    public void setAuraColorRgb(int rgb)       { this.auraColorRgb   = rgb & 0xFFFFFF; }

    public boolean isCustomAuraColor()           { return customAuraColor; }
    public void    setCustomAuraColor(boolean v) { this.customAuraColor = v; }

    // ── Piel: modo API ───────────────────────────────────────────────
    public boolean isCustomSkinColor()         { return customSkinColor; }
    public void    setCustomSkinColor(boolean v) { this.customSkinColor = v; }


    public Gender getGender()                  { return gender; }
    public void   setGender(Gender g)          { if (g != null) this.gender = g; }

    // ── Rasgos faciales API ───────────────────────────────────────────────────
    /** Id del rasgo elegido en esa categoría, o null = ninguno. */
    @Nullable
    public ResourceLocation getFacePart(FaceCategory category) { return faceParts.get(category); }

    public void setFacePart(FaceCategory category, @Nullable ResourceLocation id) {
        if (id == null) faceParts.remove(category); else faceParts.put(category, id);
    }


    // ── IDs de estilo (legacy) ────────────────────────────────────────────────
    /** Valor crudo: un id del catálogo, "none" o un "custom:..." (HairCode). */
    public String getHairStyleId()             { return hairStyleId; }
    public void   setHairStyleId(String id)    { if (id != null && !id.isEmpty()) this.hairStyleId = normalizeHairStyle(id); }

    /** Peinado del catálogo, o null si es calvo o un código procedimental (HairCode). */
    @Nullable
    public ResourceLocation getHairStyle() {
        if (HAIR_NONE.equals(hairStyleId) || com.hmc.zenkai.feature.race.HairCode.isCustom(hairStyleId)) return null;
        return ResourceLocation.tryParse(hairStyleId);
    }

    /** null = calvo. */
    public void setHairStyle(@Nullable ResourceLocation id) {
        this.hairStyleId = id == null ? HAIR_NONE : id.toString();
    }

    /**
     * Traduce los valores de antes del catálogo de pelo (S5): "hair0"/"bald"/"" -> "none",
     * "hairN" -> zenkai:classic (el único peinado que existió con número; hair_2+ se pintaban
     * calvos igualmente) y los ids intermedios zenkai:legacy_1 / legacy_1_ssj, ya renombrados a
     * zenkai:classic / classic_ssj (0.3.1: nada de la personalización lleva número).
     * Cualquier otro valor se deja tal cual.
     */
    static String normalizeHairStyle(String raw) {
        if (raw == null || raw.isEmpty() || "bald".equalsIgnoreCase(raw) || "hair0".equalsIgnoreCase(raw)) return HAIR_NONE;
        if (raw.matches("hair\\d{1,3}")) return DEFAULT_HAIR;
        if ("zenkai:legacy_1".equals(raw)) return DEFAULT_HAIR;
        if ("zenkai:legacy_1_ssj".equals(raw)) return "zenkai:classic_ssj";
        return raw;
    }

    // ── Biblioteca de peinados importados (pestaña Pelo, CustomHairLibrary) ──
    /**
     * Fuera de {@link #save()} A PROPÓSITO: save() es lo que copian las pantallas como snapshot
     * (y restauran con load() al cancelar) y lo que manda el cliente en UpdatePlayerVisualPacket.
     * Si la biblioteca fuera dentro, cancelar la pantalla borraría un peinado recién importado y el
     * paquete del cliente arrastraría hasta 64 KB de códigos. Va en {@link #saveFull()} (disco y
     * sync al propio jugador) y, recortada a la entrada puesta, en {@link #saveForTracker()}.
     */
    private final List<CustomHairLibrary.Entry> customHairs = new ArrayList<>();
    private static final String CUSTOM_HAIRS_KEY = "customHairs";

    public List<CustomHairLibrary.Entry> getCustomHairs() { return List.copyOf(customHairs); }

    public void setCustomHairs(List<CustomHairLibrary.Entry> entries) {
        customHairs.clear();
        for (CustomHairLibrary.Entry e : entries) {
            if (customHairs.size() >= CustomHairLibrary.MAX_ENTRIES) break;
            if (findCustomHair(e.id()) == null) customHairs.add(e);
        }
    }

    @Nullable
    public CustomHairLibrary.Entry findCustomHair(@Nullable String id) {
        if (id == null) return null;
        for (CustomHairLibrary.Entry e : customHairs) if (e.id().equals(id)) return e;
        return null;
    }

    /** La entrada que lleva puesta (hairStyleId = "custom:&lt;id&gt;"), o null. */
    @Nullable
    public CustomHairLibrary.Entry activeCustomHair() {
        return findCustomHair(CustomHairLibrary.idFromStyle(hairStyleId));
    }

    public boolean addCustomHair(CustomHairLibrary.Entry e) {
        if (customHairs.size() >= CustomHairLibrary.MAX_ENTRIES || findCustomHair(e.id()) != null) return false;
        customHairs.add(e);
        return true;
    }

    public boolean removeCustomHair(String id) {
        return customHairs.removeIf(e -> e.id().equals(id));
    }

    public String getTailStyleId()             { return tailStyleId; }
    public void   setTailStyleId(String id)    { if (id != null && !id.isEmpty()) this.tailStyleId = id; }

    // ── Marca Majin (controlado por Babidi): aura roja + badge. La fija el efecto. ──
    private boolean majinControlled = false;

    public boolean isMajinControlled()        { return majinControlled; }
    public void    setMajinControlled(boolean v) { this.majinControlled = v; }

    // ── NBT ──────────────────────────────────────────────────────────────────
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        tag.putBoolean("renderRaceSkin",  renderRaceSkin);
        tag.putBoolean("hideVanillaBody", hideVanillaBody);

        tag.putInt("skinColor",   skinColorRgb);
        tag.putInt("hairColor",   hairColorRgb);
        tag.putBoolean("hairStrandColors", hairStrandColors);
        tag.putInt("eyeColor",    eyeColorRgb);
        tag.putInt("eyeColorRight", eyeColorRightRgb);
        tag.putInt("auraColor",   auraColorRgb);

        tag.put(FACE_PARTS_KEY, saveFaceParts());

        tag.putString("hairStyleId", hairStyleId == null ? HAIR_NONE    : hairStyleId);
        tag.putString("tailStyleId", tailStyleId == null ? "loose"      : tailStyleId);

        tag.putBoolean("customSkinColor", customSkinColor);
        tag.putBoolean("customAuraColor", customAuraColor);
        tag.putString("gender", gender.name());
        tag.putBoolean("majinControlled", majinControlled);

        int[] lc = new int[layerColors.size()];
        for (int i = 0; i < lc.length; i++) lc[i] = layerColors.get(i);
        tag.putIntArray("layerColors", lc);

        return tag;
    }

    /** save() + la biblioteca entera: disco (codec del attachment) y sync al propio jugador. */
    public CompoundTag saveFull() {
        CompoundTag tag = save();
        tag.put(CUSTOM_HAIRS_KEY, CustomHairLibrary.save(customHairs));
        return tag;
    }

    /** Lo que ven los DEMÁS jugadores: de la biblioteca, solo el peinado puesto (si lo hay). */
    public CompoundTag saveForTracker() {
        CompoundTag tag = save();
        CustomHairLibrary.Entry active = activeCustomHair();
        tag.put(CUSTOM_HAIRS_KEY, CustomHairLibrary.save(active == null ? List.of() : List.of(active)));
        return tag;
    }

    public void load(CompoundTag tag) {
        if (tag.contains("renderRaceSkin"))  this.renderRaceSkin  = tag.getBoolean("renderRaceSkin");
        if (tag.contains("hideVanillaBody")) this.hideVanillaBody = tag.getBoolean("hideVanillaBody");

        if (tag.contains("skinColor"))   this.skinColorRgb   = tag.getInt("skinColor");
        if (tag.contains("hairColor"))   this.hairColorRgb   = tag.getInt("hairColor");
        if (tag.contains("hairStrandColors")) this.hairStrandColors = tag.getBoolean("hairStrandColors");
        if (tag.contains("eyeColor"))    this.eyeColorRgb    = tag.getInt("eyeColor");
        if (tag.contains("eyeColorRight")) setEyeColorRightRgb(tag.getInt("eyeColorRight"));
        if (tag.contains("auraColor"))   this.auraColorRgb   = tag.getInt("auraColor");

        if (tag.contains(FACE_PARTS_KEY, Tag.TAG_COMPOUND)) {
            loadFaceParts(tag.getCompound(FACE_PARTS_KEY));
        } else if (tag.contains("eyeIndex") || tag.contains("mouthIndex") || tag.contains("noseIndex")) {
            // Save anterior al catálogo: traducir los índices viejos a ids (única vez que existen).
            migrateLegacyIndex(tag, "eyeIndex",   FaceCategory.EYES);
            migrateLegacyIndex(tag, "mouthIndex", FaceCategory.MOUTH);
            migrateLegacyIndex(tag, "noseIndex",  FaceCategory.NOSE);
        }

        if (tag.contains("hairStyleId")) this.hairStyleId = normalizeHairStyle(tag.getString("hairStyleId"));
        if (tag.contains("tailStyleId")) this.tailStyleId = tag.getString("tailStyleId");

        this.majinControlled = tag.contains("majinControlled") && tag.getBoolean("majinControlled");

        if (tag.contains("customSkinColor")) this.customSkinColor = tag.getBoolean("customSkinColor");
        if (tag.contains("customAuraColor")) this.customAuraColor = tag.getBoolean("customAuraColor");
        if (tag.contains("gender")) {
            try { this.gender = Gender.valueOf(tag.getString("gender")); } catch (IllegalArgumentException ignored) {}
        }

        layerColors.clear();
        if (tag.contains("layerColors")) {
            for (int v : tag.getIntArray("layerColors")) layerColors.add(v < 0 ? -1 : (v & 0xFFFFFF));
        }
        // Solo si el tag la trae (saveFull / saveForTracker): un snapshot de save() no la toca.
        if (tag.contains(CUSTOM_HAIRS_KEY, Tag.TAG_LIST)) {
            setCustomHairs(CustomHairLibrary.load(tag.getList(CUSTOM_HAIRS_KEY, Tag.TAG_COMPOUND)));
        }

    }

    // ── Carga desde el CLIENTE (UpdatePlayerVisualPacket) ─────────────────────
    /** Topes de lo que el cliente puede pedir. Holgados respecto al contenido real: solo
     *  existen para que un cliente modificado no meta valores absurdos o listas enormes. */
    private static final int MAX_CLIENT_LAYER_COLORS  = 16;

    /**
     * Versión saneada de {@link #load} para lo que manda el cliente: copia SOLO la lista blanca
     * de campos que las pantallas de apariencia permiten elegir (colores, rasgos faciales, pelo,
     * peinado, modo de piel, género, skin de raza on/off) y con rangos acotados. El resto
     * del tag se ignora y conserva el valor del servidor.
     *
     * Por qué no {@code load()}: antes el servidor hacía {@code load(pkt.data)} con el NBT
     * completo, y eso dejaba al cliente escribir campos que fija SOLO el servidor —
     * majinControlled (PersistentEffectsSystem añade/quita el efecto MAJIN según ese flag, así
     * que un cliente modificado podía quitarse la maldición de Babidi o ponérsela) y
     * tailStyleId (lo cambia WheelSelectPacket).
     * Un campo cosmético nuevo que el cliente deba poder elegir hay que añadirlo AQUÍ a mano.
     */
    public void loadCosmeticFromClient(CompoundTag tag) {
        if (tag.contains("skinColor")) setSkinColorRgb(tag.getInt("skinColor"));
        if (tag.contains("hairColor")) setHairColorRgb(tag.getInt("hairColor"));
        if (tag.contains("hairStrandColors")) setHairStrandColors(tag.getBoolean("hairStrandColors"));
        if (tag.contains("eyeColor"))  setEyeColorRgb(tag.getInt("eyeColor"));
        if (tag.contains("eyeColorRight")) setEyeColorRightRgb(tag.getInt("eyeColorRight"));
        if (tag.contains("auraColor")) setAuraColorRgb(tag.getInt("auraColor"));

        // Ids de rasgo: solo se comprueba que sean ids bien formados de una categoría conocida;
        // si existen en el catálogo y los admite la raza lo decide FaceBans.reconcile, que el
        // packet llama justo después.
        if (tag.contains(FACE_PARTS_KEY, Tag.TAG_COMPOUND)) loadFaceParts(tag.getCompound(FACE_PARTS_KEY));

        if (tag.contains("hairStyleId")) {
            String id = tag.getString("hairStyleId");
            if (isAcceptableHairStyleId(id)) setHairStyleId(id);
            // Un peinado importado solo si está en la biblioteca que guarda el SERVIDOR (la del
            // cliente no viaja en este paquete); si el mundo no los permite lo corrige reconcile.
            else if (findCustomHair(CustomHairLibrary.idFromStyle(id)) != null) this.hairStyleId = id;
        }

        if (tag.contains("customSkinColor")) setCustomSkinColor(tag.getBoolean("customSkinColor"));
        if (tag.contains("customAuraColor")) setCustomAuraColor(tag.getBoolean("customAuraColor"));
        if (tag.contains("gender")) {
            try { setGender(Gender.valueOf(tag.getString("gender"))); } catch (IllegalArgumentException ignored) {}
        }
        if (tag.contains("renderRaceSkin"))  setRenderRaceSkin(tag.getBoolean("renderRaceSkin"));
        if (tag.contains("hideVanillaBody")) setHideVanillaBody(tag.getBoolean("hideVanillaBody"));

        if (tag.contains("layerColors")) {
            int[] lc = tag.getIntArray("layerColors");
            layerColors.clear();
            for (int i = 0; i < Math.min(lc.length, MAX_CLIENT_LAYER_COLORS); i++)
                layerColors.add(lc[i] < 0 ? -1 : (lc[i] & 0xFFFFFF));
        }
    }

    // ── NBT de rasgos faciales ────────────────────────────────────────────────
    private static final String FACE_PARTS_KEY = "faceParts";
    /** Un ResourceLocation real ronda las decenas de caracteres; esto solo frena basura. */
    private static final int MAX_FACE_ID_LENGTH = 128;

    private CompoundTag saveFaceParts() {
        CompoundTag t = new CompoundTag();
        for (var e : faceParts.entrySet()) t.putString(e.getKey().key(), e.getValue().toString());
        return t;
    }

    /** Reemplaza los rasgos por los del tag: una categoría ausente queda en "ninguno". */
    private void loadFaceParts(CompoundTag t) {
        faceParts.clear();
        for (FaceCategory c : FaceCategory.values()) {
            if (!t.contains(c.key(), Tag.TAG_STRING)) continue;
            String raw = t.getString(c.key());
            if (raw.length() > MAX_FACE_ID_LENGTH) continue;
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id != null) faceParts.put(c, LegacyFaceIds.renamed(id));
        }
    }

    private void migrateLegacyIndex(CompoundTag tag, String key, FaceCategory category) {
        if (tag.contains(key)) setFacePart(category, LegacyFaceIds.fromIndex(category, tag.getInt(key)));
    }

    /**
     * "none"/"bald"/"hairN" (formato viejo, se normaliza), un id de catálogo bien formado, o un
     * código procedimental con el sobre bien formado. Si el id EXISTE y lo admite la raza lo
     * decide HairCatalog.reconcile, que el packet llama justo después.
     */
    private static boolean isAcceptableHairStyleId(String id) {
        if (id == null || id.isEmpty()) return false;
        // Un código procedimental mide cientos de caracteres: su tope es el de HairCode, no el de un
        // id de catálogo. Con el tope de 128 delante, un "custom:" real se descartaba en silencio.
        // "custom:" solo vale para la biblioteca (ver loadCosmeticFromClient); el sobre HairCode
        // (zh1, procedimental) quedó sustituido por los códigos importados.
        if (com.hmc.zenkai.feature.race.HairCode.isCustom(id)) return false;
        if (id.length() > MAX_FACE_ID_LENGTH) return false;
        if (HAIR_NONE.equals(id) || "bald".equalsIgnoreCase(id) || id.matches("hair\\d{1,3}")) return true;
        return ResourceLocation.tryParse(id) != null;
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

    /** Olvida el conjunto de overrides de capas (vuelven a los defaults del JSON). Usar al cambiar de raza. */
    public void clearLayerColors() {
        layerColors.clear();
    }
}
