package com.hmc.zenkai.feature.skilltree;

import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Carga los nodos del árbol ({@code data/<ns>/zenkai_skill_tree/*.json}, ver SkillTreeData) y los
 * sincroniza al cliente en el login y tras /reload. Espeja FormManager.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class SkillTreeDataManager {
    private SkillTreeDataManager() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-SkillTree");
    private static final FileToIdConverter FILES = FileToIdConverter.json("zenkai_skill_tree");

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        SkillTreeDataSyncPacket pkt = new SkillTreeDataSyncPacket(new LinkedHashMap<>(SkillTreeData.raw()));
        if (event.getPlayer() != null) {
            PacketDistributor.sendToPlayer(event.getPlayer(), pkt);
        } else {
            PacketDistributor.sendToAllPlayers(pkt);
        }
    }

    private static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, String>> {
        @Override
        protected @NotNull Map<ResourceLocation, String> prepare(@NotNull ResourceManager rm, @NotNull ProfilerFiller profiler) {
            Map<ResourceLocation, String> out = new LinkedHashMap<>();
            for (var e : FILES.listMatchingResources(rm).entrySet()) {
                try (BufferedReader reader = e.getValue().openAsReader()) {
                    out.put(FILES.fileToId(e.getKey()), reader.lines().collect(Collectors.joining("\n")));
                } catch (Exception ex) {
                    LOGGER.error("[Zenkai] No se pudo leer {}: {}", e.getKey(), ex.toString());
                }
            }
            return out;
        }

        @Override
        protected void apply(@NotNull Map<ResourceLocation, String> raw, @NotNull ResourceManager rm,
                             @NotNull ProfilerFiller profiler) {
            SkillTreeData.replaceAllFromRaw(raw);
            LOGGER.info("[Zenkai] Nodos del árbol con datos: {}.", SkillTreeData.all().size());
        }
    }
}
