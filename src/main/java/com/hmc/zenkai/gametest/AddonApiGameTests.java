package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.api.event.AlignmentChangeEvent;
import com.hmc.zenkai.api.event.GodKiGrantedEvent;
import com.hmc.zenkai.api.event.RageResolvedEvent;
import com.hmc.zenkai.api.event.TPGainedEvent;
import com.hmc.zenkai.api.master.MasterServiceRegistry;
import com.hmc.zenkai.api.rage.RageApi;
import com.hmc.zenkai.api.training.TrainingApi;
import com.hmc.zenkai.api.wish.CustomWish;
import com.hmc.zenkai.api.wish.DragonKind;
import com.hmc.zenkai.api.wish.WishRegistry;
import com.hmc.zenkai.feature.alignment.AlignmentManager;
import com.hmc.zenkai.feature.master.MasterService;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.rage.RageIntensity;
import com.hmc.zenkai.feature.skilltree.GodKi;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * API de addons (2026-11-12): los eventos mutables o cancelables nuevos, el registro de deseos por dragón, los servicios
 * de maestro y las puertas de entrada de Rage, entrenamiento y God Ki. Los jugadores simulados no tienen conexión: el
 * sync final lanza y se tolera (los eventos se publican antes de aplicar).
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AddonApiGameTests {
    private AddonApiGameTests() {}

    private static void quiet(Runnable r) {
        try { r.run(); } catch (UnsupportedOperationException noConnection) { /* sync sin conexión */ }
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void alignmentChangeCanBeMutatedAndCancelled(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setAlignment(0);
        List<String> reasons = new ArrayList<>();
        Consumer<AlignmentChangeEvent> double_ = e -> { reasons.add(e.reason()); e.setDelta(e.delta() * 2); };
        NeoForge.EVENT_BUS.addListener(AlignmentChangeEvent.class, double_);
        try {
            quiet(() -> AlignmentManager.change(sp, 10, "test"));
            helper.assertTrue(att.getAlignment() == 20, "El delta mutado (10 -> 20) se aplica; alineamiento=" + att.getAlignment());
            helper.assertTrue(reasons.size() == 1 && reasons.get(0).equals("test"), "La razón llega al oyente: " + reasons);
        } finally {
            NeoForge.EVENT_BUS.unregister(double_);
        }
        Consumer<AlignmentChangeEvent> cancel = e -> e.setCanceled(true);
        NeoForge.EVENT_BUS.addListener(AlignmentChangeEvent.class, cancel);
        try {
            quiet(() -> AlignmentManager.change(sp, 10, "test"));
            helper.assertTrue(att.getAlignment() == 20, "Cancelado no cambia nada; alineamiento=" + att.getAlignment());
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void tpGainedEventMutatesAndCancels(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        List<String> cats = new ArrayList<>();
        Consumer<TPGainedEvent> seven = e -> { cats.add(e.category()); e.setAmount(7); };
        NeoForge.EVENT_BUS.addListener(TPGainedEvent.class, seven);
        int before = att.getTP();
        try {
            quiet(() -> TrainingApi.award(sp, 100_000));
            helper.assertTrue(att.getTP() - before == 7, "El importe mutado (7) se suma; delta=" + (att.getTP() - before));
            helper.assertTrue(cats.size() == 1 && cats.get(0).equals("addon"), "Categoría 'addon': " + cats);
        } finally {
            NeoForge.EVENT_BUS.unregister(seven);
        }
        Consumer<TPGainedEvent> cancel = e -> e.setCanceled(true);
        NeoForge.EVENT_BUS.addListener(TPGainedEvent.class, cancel);
        int mid = att.getTP();
        try {
            quiet(() -> TrainingApi.award(sp, 100_000));
            helper.assertTrue(att.getTP() == mid, "Cancelado no suma TP; delta=" + (att.getTP() - mid));
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
        }
        helper.succeed();
    }

    private record Svc(String id) implements MasterService {
        @Override public Component label(ServerPlayer sp) { return Component.literal(id); }
        @Override public boolean claim(ServerPlayer sp) { return true; }
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void masterServicesRegisterAndIgnoreDuplicates(GameTestHelper helper) {
        String master = "gametest_master_" + helper.getLevel().getGameTime();
        MasterServiceRegistry.register(master, new Svc("a:uno"));
        MasterServiceRegistry.register(master, new Svc("a:dos"));
        MasterServiceRegistry.register(master, new Svc("a:uno"));   // mismo id: se ignora
        var list = MasterServiceRegistry.forMaster(master);
        helper.assertTrue(list.size() == 2, "Dos servicios (el repetido se ignora); hay " + list.size());
        helper.assertTrue(list.get(0).id().equals("a:uno") && list.get(1).id().equals("a:dos"), "Orden de registro");
        helper.assertTrue(MasterServiceRegistry.forMaster("nadie_" + master).isEmpty(), "Un maestro sin servicios da lista vacía");
        helper.succeed();
    }

    private record Wish(String path, Set<DragonKind> dragons, boolean available) implements CustomWish {
        @Override public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("gametest", path); }
        @Override public Component name() { return Component.literal(path); }
        @Override public ResourceLocation iconItem() { return ResourceLocation.withDefaultNamespace("emerald"); }
        @Override public Set<DragonKind> dragons() { return dragons; }
        @Override public boolean availableTo(ServerPlayer p, DragonKind d) { return available; }
        @Override public Optional<Component> grant(ServerPlayer p, DragonKind d) { return Optional.empty(); }
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void customWishesFilterByDragonAndAvailability(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        WishRegistry.register(new Wish("solo_shenlong", EnumSet.of(DragonKind.SHENLONG), true));
        WishRegistry.register(new Wish("solo_porunga", EnumSet.of(DragonKind.PORUNGA), true));
        WishRegistry.register(new Wish("ambos", EnumSet.allOf(DragonKind.class), true));
        WishRegistry.register(new Wish("oculto", EnumSet.allOf(DragonKind.class), false));
        List<String> shenlong = WishRegistry.availableFor(sp, DragonKind.SHENLONG).stream().map(w -> w.id().getPath()).toList();
        List<String> porunga = WishRegistry.availableFor(sp, DragonKind.PORUNGA).stream().map(w -> w.id().getPath()).toList();
        helper.assertTrue(shenlong.contains("solo_shenlong") && shenlong.contains("ambos")
                && !shenlong.contains("solo_porunga") && !shenlong.contains("oculto"), "Shenlong ve: " + shenlong);
        helper.assertTrue(porunga.contains("solo_porunga") && porunga.contains("ambos")
                && !porunga.contains("solo_shenlong") && !porunga.contains("oculto"), "Porunga ve: " + porunga);
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void rageApiFiresResolvedEvent(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        List<RageResolvedEvent> seen = new ArrayList<>();
        Consumer<RageResolvedEvent> l = seen::add;
        NeoForge.EVENT_BUS.addListener(RageResolvedEvent.class, l);
        try {
            quiet(() -> RageApi.trigger(sp, RageIntensity.MEDIUM, "gametest:hecho:1", "Alguien"));
            helper.assertTrue(seen.size() == 1, "Un evento por hecho; se vieron " + seen.size());
            RageResolvedEvent e = seen.get(0);
            helper.assertTrue(e.trigger().equals("ADDON") && e.intensity().equals("MEDIUM") && "gametest:hecho:1".equals(e.eventId()),
                    "Trigger ADDON con su intensidad e id: " + e.trigger() + "/" + e.intensity() + "/" + e.eventId());
        } finally {
            NeoForge.EVENT_BUS.unregister(l);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void godKiFromAddonFiresOnce(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        List<String> vias = new ArrayList<>();
        Consumer<GodKiGrantedEvent> l = e -> vias.add(e.via());
        NeoForge.EVENT_BUS.addListener(GodKiGrantedEvent.class, l);
        try {
            quiet(() -> GodKi.grantFromAddon(sp));
            quiet(() -> GodKi.grantFromAddon(sp));   // ya lo tiene: no repite
            helper.assertTrue(vias.size() == 1 && vias.get(0).equals("event"), "Un solo evento por la vía 'event': " + vias);
        } finally {
            NeoForge.EVENT_BUS.unregister(l);
        }
        helper.succeed();
    }
}
