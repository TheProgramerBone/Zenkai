package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Muñecos de AURA, SOLO CLIENTE (2026-11-01): {@code /zenkaiaura dummies <formas|preset>}. Pone en fila delante del
 * jugador un jugador remoto FALSO por forma, cada uno en esa forma y CARGANDO ki, para ver las auras (y el humo de
 * silueta) de varias formas a la vez y ajustar su {@code silhouette.thickness} sin transformarse una por una.
 *
 * POR QUÉ jugadores falsos y no las Sombras de {@code /zenkai debug dummy}: el aura y el contorno de silueta se
 * dibujan para los JUGADORES del nivel (y el contorno sale de su propio render), no para cualquier mob. Aquí no hay
 * servidor: los attachments se escriben solo en la copia local, nada se guarda ni se sincroniza, y se borran al
 * salir del mundo o con {@code clear}.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraDummies {
    private AuraDummies() {}

    private static final int FIRST_ID = -2_000_000;
    private static final double DIST = 7.0, SPACING = 2.6, ROW_GAP = 5.0;
    private static final int PER_ROW = 7;

    private record Dummy(RemotePlayer player, ResourceLocation form, Vec3 pos) {}

    private static final List<Dummy> LIST = new ArrayList<>();

    /** Presets por nombre; cualquier otro texto se toma como id de forma ({@code ssj2}, {@code zenkai:ssj_god}...). */
    public static final Map<String, List<String>> PRESETS = Map.of(
            "legendary", List.of("ikari", "ssj_green", "ssj_green_max", "human_legendary", "namek_legendary",
                    "arcosian_legendary", "majin_legendary"),
            "divine", List.of("ssj_god", "ssj_blue", "ssj_rose", "ssj_blue_evolution", "ssj_rose_evolution",
                    "human_god", "namek_god", "arcosian_god", "majin_god", "ultra_instinct_sign", "ultra_instinct",
                    "ultra_ego", "beast"),
            "ssj", List.of("ssj1", "ssj2", "ssj3", "ssj4", "oozaru", "super_oozaru"));

    /** @return nº de muñecos creados, o -1 si no hay mundo. Los inexistentes se anotan en {@code unknown}. */
    public static int spawn(List<String> tokens, List<String> unknown) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return -1;
        clear();

        List<ResourceLocation> forms = new ArrayList<>();
        for (String t : tokens) {
            for (String name : PRESETS.getOrDefault(t, List.of(t))) {
                ResourceLocation id = name.contains(":") ? ResourceLocation.parse(name)
                        : ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name);
                if (FormRegistry.get(id) == null) unknown.add(name);
                else if (!forms.contains(id)) forms.add(id);
            }
        }

        // Fila perpendicular a la mirada del jugador, a DIST bloques; si hay más de PER_ROW, otra fila más atrás.
        float yaw = mc.player.getYRot();
        Vec3 fwd = Vec3.directionFromRotation(0f, yaw).normalize();
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 origin = mc.player.position();
        for (int i = 0; i < forms.size(); i++) {
            int row = i / PER_ROW, col = i % PER_ROW;
            int inRow = Math.min(PER_ROW, forms.size() - row * PER_ROW);
            double lateral = (col - (inRow - 1) / 2.0) * SPACING;
            Vec3 pos = origin.add(fwd.scale(DIST + row * ROW_GAP)).add(right.scale(lateral));
            ResourceLocation form = forms.get(i);
            UUID uuid = UUID.nameUUIDFromBytes(("zenkai_aura_dummy_" + i).getBytes());
            RemotePlayer rp = new RemotePlayer(level, new GameProfile(uuid, "Dummy " + form.getPath()));
            rp.setId(FIRST_ID - i);
            rp.moveTo(pos.x, pos.y, pos.z, yaw + 180f, 0f);
            rp.setYHeadRot(yaw + 180f);
            rp.setYBodyRot(yaw + 180f);
            apply(rp, form);
            level.addEntity(rp);
            LIST.add(new Dummy(rp, form, pos));
        }
        return LIST.size();
    }

    /** Raza de la forma (la primera que admita; las universales, Humano) + forma puesta + cargando ki. */
    private static void apply(RemotePlayer rp, ResourceLocation form) {
        try {
            FormDef def = FormRegistry.get(form);
            Race race = def != null && !def.races().isEmpty() ? def.races().iterator().next() : Race.HUMAN;
            var stats = rp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
            if (stats.getRace() != race) stats.setRace(race);
            stats.setChargingKi(true);
            rp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).setFormId(form);
            // Escala del cuerpo de la forma (Oozaru x3...): el aura se dibuja a la escala real del jugador.
            var scale = rp.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
            if (scale != null && def != null && scale.getBaseValue() != def.scale()) scale.setBaseValue(def.scale());
        } catch (RuntimeException ex) {
            Zenkai.LOGGER.warn("[Zenkai] Muñeco de aura {}: no se pudo preparar ({})", form, ex.toString());
        }
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (Dummy d : LIST) mc.level.removeEntity(d.player().getId(), Entity.RemovalReason.DISCARDED);
        }
        LIST.clear();
    }

    /** Los muñecos siguen quietos, cargando y en su forma (algo del juego puede tocar sus attachments). */
    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        if (LIST.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIST.clear(); return; }
        for (Dummy d : new ArrayList<>(LIST)) {
            RemotePlayer rp = d.player();
            if (rp.level() != mc.level || rp.isRemoved()) { clear(); return; }
            rp.setPos(d.pos());
            rp.setDeltaMovement(Vec3.ZERO);
            apply(rp, d.form());
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        LIST.clear();
    }
}
