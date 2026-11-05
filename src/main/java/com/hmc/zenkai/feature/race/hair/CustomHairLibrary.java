package com.hmc.zenkai.feature.race.hair;

import com.hmc.zenkai.feature.race.HairCode;
import com.hmc.zenkai.feature.race.hair.strand.HairCodeException;
import com.hmc.zenkai.feature.race.hair.strand.HairCodes;
import com.hmc.zenkai.feature.race.hair.strand.StrandSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Biblioteca personal de peinados importados (códigos de DragonMineZ y cadenas de Dragon Block C)
 * desde la pestaña Pelo. Detalle: .claude/docs/customizacion-pelo.md §10.1.
 *
 * Se guarda el CÓDIGO ORIGINAL, no la geometría: es la forma más compacta que existe (un juego de
 * 4 formas de DMZ ocupa 3-6 KB; su .geo.json, decenas) y cada cliente lo convierte al vuelo
 * (CustomHairModels). La biblioteca entera solo viaja a su dueño; a los demás jugadores les llega
 * únicamente la entrada que lleva puesta (PlayerVisualAttachment.saveForTracker).
 *
 * El peinado puesto se guarda como hairStyleId = "custom:&lt;id&gt;", con id derivado del código (el
 * mismo código importado dos veces es la misma entrada).
 */
public final class CustomHairLibrary {
    private CustomHairLibrary() {}

    /** 16 desde la biblioteca por carpetas (2026-10-12; antes 8). Solo el dueño recibe la lista entera. */
    public static final int MAX_ENTRIES = 16;
    /**
     * Tope por código. Medidos: un juego de 4 de DMZ 2.1 hasta 5,7 KB, uno de DMZ 2.2 (DMZF22)
     * 10-13 KB y hasta 17,4 KB uno cargado de mechones (2026-10-12: con el tope antiguo de 16 KB
     * la casilla de la pantalla lo cortaba en silencio y salía "código dañado"), una cadena DBC
     * 786. El paquete cliente-&gt;servidor no pasa de 32.767 bytes, así que un código va siempre en
     * su propio paquete (CustomHairLibraryPacket) y el tope deja sitio al nombre y las cabeceras.
     */
    public static final int MAX_CODE_LENGTH = 30_000;
    public static final int MAX_NAME_LENGTH = 32;

    public record Entry(String id, String name, String code) {
        public String styleId() {
            return HairCode.PREFIX + id;
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("id", id);
            t.putString("name", name);
            t.putString("code", code);
            return t;
        }

        /** null si el tag no es una entrada válida (save corrupto o enviado por un cliente modificado). */
        @Nullable
        public static Entry load(CompoundTag t) {
            String code = t.getString("code");
            if (code.isEmpty() || code.length() > MAX_CODE_LENGTH) return null;
            String name = cleanName(t.getString("name"));
            return new Entry(idOf(code), name.isEmpty() ? "Custom" : name, code);
        }
    }

    /** Id estable de 12 hex derivado del código (sin espacios): mismo código, misma entrada. */
    public static String idOf(String code) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(strip(code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** El id de una entrada a partir del hairStyleId ("custom:&lt;id&gt;"), o null si no es uno. */
    @Nullable
    public static String idFromStyle(@Nullable String hairStyleId) {
        if (!HairCode.isCustom(hairStyleId)) return null;
        String id = hairStyleId.substring(HairCode.PREFIX.length());
        return id.matches("[0-9a-f]{12}") ? id : null;
    }

    public static String cleanName(@Nullable String raw) {
        String s = raw == null ? "" : raw.replaceAll("\\p{Cntrl}", "").strip();
        if (s.length() > MAX_NAME_LENGTH) s = s.substring(0, MAX_NAME_LENGTH).strip();
        return s;
    }

    static String strip(String code) {
        StringBuilder sb = new StringBuilder(code.length());
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (!Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Valida un código para la biblioteca (la usan la pantalla, para avisar al momento, y el
     * servidor, que nunca se fía de la pantalla) y arma la entrada. El nombre: el que se pida, o
     * el que trae el código si no es el genérico "Custom", o "Custom N".
     */
    public static Entry prepare(List<Entry> library, @Nullable String wantedName, String rawCode)
            throws HairCodeException {
        String code = strip(rawCode == null ? "" : rawCode);
        if (code.isEmpty()) throw new HairCodeException("empty");
        if (code.length() > MAX_CODE_LENGTH) throw new HairCodeException("too_long", code.length(), MAX_CODE_LENGTH);
        String id = idOf(code);
        for (Entry e : library) if (e.id().equals(id)) throw new HairCodeException("duplicate", e.name());
        if (library.size() >= MAX_ENTRIES) throw new HairCodeException("library_full", MAX_ENTRIES);
        StrandSet base = HairCodes.decode(code).variants().base();

        String name = cleanName(wantedName);
        if (name.isEmpty()) {
            String fromCode = cleanName(base.name());
            name = fromCode.isEmpty() || fromCode.equalsIgnoreCase("custom") ? nextGenericName(library) : fromCode;
        }
        return new Entry(id, name, code);
    }

    private static String nextGenericName(List<Entry> library) {
        for (int n = 1; ; n++) {
            String candidate = "Custom " + n;
            boolean used = false;
            for (Entry e : library) used |= e.name().equalsIgnoreCase(candidate);
            if (!used) return candidate;
        }
    }

    // ── NBT de la lista ──────────────────────────────────────────────────────

    public static ListTag save(List<Entry> entries) {
        ListTag list = new ListTag();
        for (Entry e : entries) list.add(e.save());
        return list;
    }

    /** Lista acotada y sin repetidos; lo que no se entiende se descarta. */
    public static List<Entry> load(ListTag list) {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < list.size() && out.size() < MAX_ENTRIES; i++) {
            if (list.get(i).getId() != Tag.TAG_COMPOUND) continue;
            Entry e = Entry.load(list.getCompound(i));
            if (e == null) continue;
            boolean dup = false;
            for (Entry o : out) dup |= o.id().equals(e.id());
            if (!dup) out.add(e);
        }
        return out;
    }
}
