package com.hmc.zenkai.gametest;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Utilidades compartidas por los gametests de Zenkai. Vive fuera de las clases de test para que
 * cada una se quede solo con el flujo que verifica.
 */
final class ZenkaiGameTestHelpers {
    private ZenkaiGameTestHelpers() {}

    /**
     * Jugador de prueba con raza elegida (HUMAN) y TP en 0. Cada test añade el TP que necesite
     * con {@code att.addTP(...)} — no se fija aquí para que quede explícito en cada caso cuánto
     * hace falta.
     * NO usa GameTestHelper#makeMockServerPlayerInLevel(): ese método hace un join COMPLETO vía
     * PlayerList.placeNewPlayer, que dispara eventos de login ANTES de que el jugador quede
     * registrado en ninguna lista — en este modpack, un listener de Moonlight manda un payload
     * propio en ese punto, y el canal embebido del gametest no ha negociado NINGÚN payload (no
     * hay handshake real de cliente), así que NetworkRegistry.checkPacket lo rechaza y aborta
     * placeNewPlayer entero sin que el jugador llegue a existir en ningún sitio recuperable.
     * Aquí se monta la MISMA maquinaria (Connection + EmbeddedChannel + ServerGamePacketListenerImpl,
     * calcada de GameTestHelper.makeMockServerPlayerInLevel) pero SIN pasar por placeNewPlayer:
     * se registra directamente con ServerLevel#addNewPlayer. Ese método SÍ dispara su propio
     * evento de entidad-añadida (Curios, en la práctica, sincroniza su inventario ahí) y por
     * tanto puede tropezar con el MISMO NetworkRegistry.checkPacket que Moonlight — pero a estas
     * alturas el jugador YA está en el nivel (el evento se dispara después de añadirlo), así que
     * se ignora con el mismo criterio que invokeHandler(): ver su javadoc, es la misma limitación
     * de fondo (canal falso, sin handshake, cualquier payload de cualquier mod la dispara) y no
     * un fallo de lo que este test quiere comprobar.
     */
    static ServerPlayer raceChosenPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-mock-player");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);

        ServerPlayer sp = new ZenkaiMockPlayer(level.getServer(), level, profile, ClientInformation.createDefault());

        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(new ChannelHandler[]{connection});
        sp.connection = new ServerGamePacketListenerImpl(level.getServer(), connection, sp, cookie);

        invokeHandler(() -> level.addNewPlayer(sp));

        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setRace(Race.HUMAN);
        att.setRaceChosen(true);
        return sp;
    }

    /** Calco de la subclase anónima que usa vanilla en GameTestHelper.makeMockServerPlayerInLevel:
     *  un jugador fuera de creativo/espectador para que el resto de comprobaciones de juego
     *  normales (colisión, gravedad, etc.) se comporten como con cualquier jugador real. */
    private static final class ZenkaiMockPlayer extends ServerPlayer {
        ZenkaiMockPlayer(net.minecraft.server.MinecraftServer server, ServerLevel level,
                          GameProfile profile, ClientInformation clientInformation) {
            super(server, level, profile, clientInformation);
        }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
    }

    /**
     * Invoca un handler de paquete real (SkillBuyPacket.handle, etc.) ignorando el mismo fallo
     * de canal-sin-negociar que raceChosenPlayer() ya absorbe al registrar al jugador — misma
     * causa raíz: estos handlers MUTAN primero y SINCRONIZAN al final
     * (PlayerLifeCycle.syncIfServer/sync), así que para cuando el envío falla el estado que el
     * test quiere comprobar ya se aplicó de verdad. Sin esto, CUALQUIER paquete testeado aquí
     * (no solo los de mods ajenos) fallaría igual al llegar a su propio sync — no es un fallo
     * del handler, es una limitación del canal falso del framework de gametest, no de la lógica
     * que se está probando.
     */
    static void invokeHandler(Runnable call) {
        try {
            call.run();
        } catch (UnsupportedOperationException e) {
            if (e.getMessage() == null || !e.getMessage().contains("may not be sent to the client")) throw e;
        }
    }

    /**
     * IPayloadContext mínimo para invocar los handlers de paquete (C2S) directamente desde un
     * gametest, tal y como los invocaría de verdad el canal de red. SIN esto, un test que solo
     * llamara a PlayerSkills/PlayerStatsAttachment a pelo no ejercitaría el propio handler — que
     * es justo donde vivía el bug de grant() vs raise() que este paquete de tests quiere volver
     * a detectar solo. enqueueWork() se ejecuta SÍNCRONAMENTE: en el juego real se pasa al hilo
     * principal del server, pero aquí ya estamos en ese hilo (el propio gametest corre en él).
     * El resto de métodos del interfaz no los usa ninguno de los handlers testeados aquí; lanzan
     * si algún día alguno empieza a necesitarlos, en vez de fallar en silencio.
     */
    static IPayloadContext fakeCtx(ServerPlayer sp) {
        return new IPayloadContext() {
            @Override public ICommonPacketListener listener() {
                throw new UnsupportedOperationException("fakeCtx: listener() no soportado en gametest");
            }
            @Override public net.minecraft.world.entity.player.Player player() { return sp; }
            @Override public CompletableFuture<Void> enqueueWork(Runnable task) {
                task.run();
                return CompletableFuture.completedFuture(null);
            }
            @Override public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
                return CompletableFuture.completedFuture(task.get());
            }
            @Override public net.minecraft.network.protocol.PacketFlow flow() {
                throw new UnsupportedOperationException("fakeCtx: flow() no soportado en gametest");
            }
            @Override public void handle(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
                throw new UnsupportedOperationException("fakeCtx: handle() no soportado en gametest");
            }
            @Override public void finishCurrentTask(net.minecraft.server.network.ConfigurationTask.Type type) {
                throw new UnsupportedOperationException("fakeCtx: finishCurrentTask() no soportado en gametest");
            }
        };
    }
}
