package com.hmc.zenkai.feature.capsule;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data component {@code zenkai:capsule_remote} del control remoto de cápsulas (pedido 2026-11-25). Guarda el DUEÑO (quien
 * lo vinculó por primera vez: nadie más puede usarlo), la lista de cápsulas vinculadas y cuál está seleccionada.
 *
 * Cada vínculo apunta a una cápsula por su id de vínculo (componente
 * {@code zenkai:capsule_link} del stack de la cápsula), no solo por posición: al abrir a distancia la cápsula sube un
 * bloque (queda sobre el suelo pegado) y al recoger baja uno, así que la posición guardada es solo «la última conocida» y
 * se corrige cada vez que el control la encuentra. {@code name} y {@code tier} son una copia para el tooltip y la barra de
 * acción (el cliente no ve el stack de una cápsula colocada); se refrescan igual.
 */
public record CapsuleRemoteLinks(Optional<UUID> owner, List<Link> links, int selected) {

    /** Máximo de cápsulas por control (decisión de Claude; la rueda las recorre en orden). */
    public static final int MAX_LINKS = 8;

    public static final CapsuleRemoteLinks EMPTY = new CapsuleRemoteLinks(Optional.empty(), List.of(), 0);

    public record Link(UUID id, GlobalPos pos, String name, int tier) {
        public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Link::id),
                GlobalPos.CODEC.fieldOf("pos").forGetter(Link::pos),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Link::name),
                Codec.INT.optionalFieldOf("tier", 0).forGetter(Link::tier)
        ).apply(i, Link::new));

        public static final StreamCodec<ByteBuf, Link> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Link::id,
                GlobalPos.STREAM_CODEC, Link::pos,
                ByteBufCodecs.STRING_UTF8, Link::name,
                ByteBufCodecs.VAR_INT, Link::tier,
                Link::new);
    }

    public static final Codec<CapsuleRemoteLinks> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(CapsuleRemoteLinks::owner),
            Link.CODEC.listOf().optionalFieldOf("links", List.of()).forGetter(CapsuleRemoteLinks::links),
            Codec.INT.optionalFieldOf("selected", 0).forGetter(CapsuleRemoteLinks::selected)
    ).apply(i, CapsuleRemoteLinks::new));

    public static final StreamCodec<ByteBuf, CapsuleRemoteLinks> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), CapsuleRemoteLinks::owner,
            Link.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINKS)), CapsuleRemoteLinks::links,
            ByteBufCodecs.VAR_INT, CapsuleRemoteLinks::selected,
            CapsuleRemoteLinks::new);

    /** Índice seleccionado siempre dentro de la lista (o 0 si está vacía). */
    public int selectedIndex() {
        return links.isEmpty() ? 0 : Math.floorMod(selected, links.size());
    }

    public Optional<Link> current() {
        return links.isEmpty() ? Optional.empty() : Optional.of(links.get(selectedIndex()));
    }

    public int indexOf(UUID id) {
        for (int i = 0; i < links.size(); i++) if (links.get(i).id().equals(id)) return i;
        return -1;
    }

    public CapsuleRemoteLinks withOwner(UUID owner) {
        return new CapsuleRemoteLinks(Optional.of(owner), links, selected);
    }

    /** Añade al final y lo deja seleccionado. */
    public CapsuleRemoteLinks add(Link link) {
        List<Link> l = new ArrayList<>(links);
        l.add(link);
        return new CapsuleRemoteLinks(owner, List.copyOf(l), l.size() - 1);
    }

    public CapsuleRemoteLinks remove(int index) {
        List<Link> l = new ArrayList<>(links);
        l.remove(index);
        int sel = selectedIndex();
        if (index < sel || sel >= l.size()) sel = Math.max(0, sel - 1);
        return new CapsuleRemoteLinks(owner, List.copyOf(l), sel);
    }

    public CapsuleRemoteLinks replace(int index, Link link) {
        List<Link> l = new ArrayList<>(links);
        l.set(index, link);
        return new CapsuleRemoteLinks(owner, List.copyOf(l), selected);
    }

    public CapsuleRemoteLinks cycle(int dir) {
        if (links.isEmpty()) return this;
        return new CapsuleRemoteLinks(owner, links, Math.floorMod(selectedIndex() + dir, links.size()));
    }
}
