package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.advancement.ZenkaiTriggers;
import com.hmc.zenkai.registry.ModDamageTypes;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S: disparar la técnica del slot con la carga acumulada (R + click derecho; soltar
 * click dispara).
 * VALIDACIÓN 100% SERVIDOR, vía ActionRules (mismas reglas que evalúa el cliente para
 * predecir): raza, modo combate, manos vacías, no derribado, no bloqueando, técnica
 * desbloqueada y habilitada, cooldown (global + por slot), carga mínima y energía.
 * CARGA AUTORITATIVA: el campo chargeTicks del paquete NO se usa para decidir nada. La carga
 * real la deriva el servidor de gameTime - KiChargeServer.startTick, y se exige que el slot
 * que dispara sea el MISMO que registró la carga. Antes se aceptaba el valor del cliente y
 * solo se clampaba al máximo, así que un cliente modificado disparaba cualquier técnica al
 * 200% de sobrecarga de forma instantánea, y podía cargar un slot barato para disparar otro
 * caro. chargeTicks se conserva en el codec solo para que el cliente pinte su barra sin
 * round-trip.
 * Cambiar de técnica cancela la carga: la nueva instancia arranca con startTick nuevo y
 * ratio 0, así que un láser al 100% nunca se convierte en un Kamehameha instantáneo.
 * Ratio 0..2.0 (SOBRECARGA hasta el 200%) vía KiCombatServer.chargeRatio. El daño escala
 * LINEAL con la carga; el coste 1:1 hasta el 100% y con recargo por encima
 * (chargeCostFactor), así sobrecargar dobla el daño pero cuesta 2.5x y tarda 3.5x.
 * BARRIER también escala con la carga, pero repartida entre pool y duración en vez de
 * concentrada en un solo número (ver KiCombatServer.chargeSplitFactor). Fórmulas en
 * KiCombatServer, compartidas con las previews del editor.
 */
public record KiFirePacket(int slot, int chargeTicks) implements CustomPacketPayload {

    private static final float BURST_SPREAD_DEG = 6.0f;

    public static final Type<KiFirePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_fire"));

    public static final StreamCodec<FriendlyByteBuf, KiFirePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        buf.writeVarInt(pkt.slot());
                        buf.writeVarInt(pkt.chargeTicks());
                    },
                    buf -> new KiFirePacket(buf.readVarInt(), buf.readVarInt()));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KiFirePacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                com.hmc.zenkai.feature.action.ActionResolver.releaseKi(sp, pkt.slot());
            }
        });
    }

    /** Efecto del disparo. NO VALIDA: lo hizo ActionResolver, que además calculó ratio y
     *  coste con la carga autoritativa.
     *  El slot llega como parámetro A PROPÓSITO: es la clave del cooldown por slot, y
     *  deducirlo aquí del KiTechnique ya provocó que el conjunto de técnicas compartiera
     *  cooldown. La clave que usa tryFire tiene que ser la MISMA que consulta isReady. */
    public static void execute(ServerPlayer sp, PlayerStatsAttachment att, KiTechnique tech,
                               int slot, double ratio, double rawRatio, int cost,
                               TechniqueEffect effect) {
        KiTechniqueType type = tech.type();
        if (!KiCombatServer.tryFire(sp, slot,
                KiCombatServer.cooldownTicksFor(type, tech.size()))) return;

        att.addEnergy(-cost);
        att.addTechniqueMastery(type.name(), (float) ServerConfig.techMasteryPerUse());

        double kiPower = att.computeKiPowerFinal();
        if (type.defensive()) {
            KiCombatServer.activateBarrier(sp, tech, kiPower, ratio);
        } else if (type == KiTechniqueType.EXPLOSION) {
            // El sacrificio se cobra PRIMERO porque es parte de la munición: el daño no se
            // puede calcular hasta saber cuánta vida se ha quemado.
            int spent = selfDamage(sp, att, rawRatio);
            double damage = KiCombatServer.computeDamage(kiPower, type, tech.size()) * ratio
                    + KiCombatServer.explosionSacrificeDamage(spent);
            spawnAttached(sp, tech, damage, effect);
        } else {
            double damage = KiCombatServer.computeDamage(kiPower, type, tech.size()) * ratio;
            for (int i = 0; i < Math.max(1, type.count()); i++) {
                spawnProjectile(sp, tech, damage, effect, i);
            }
        }
        ZenkaiTriggers.TECHNIQUE_USED.get().trigger(sp,
                type.name().toLowerCase(java.util.Locale.ROOT), rawRatio);
        PlayerLifeCycle.syncIfServer(sp);
    }

    /**
     * Precio de la autodetonación. Se cobra sobre el CUERPO directamente y no por hurt(), para
     * que ignore la defensa: es daño verdadero, no un golpe recibido — si pasara por la
     * mitigación, un tanque se autodetonaría gratis.
     * A carga máxima la fracción vale 1.0 y el cuerpo queda a cero. Que eso signifique quedar
     * ABATIDO y no muerto lo decide DownedDeathGuard interceptando LivingDeathEvent, igual que
     * con cualquier otra muerte: aquí no se duplica esa regla.
     * ⚠ VERIFICAR EN JUEGO: mirrorHealth no toca la vida cuando queda <= 0, así que bajar el
     *   cuerpo a cero puede NO disparar el evento de muerte. Por eso se fuerza con un hurt
     *   masivo. Si el guardia no salta, dime y lo resuelvo por la vía que use tu pipeline.
    /** @return puntos de cuerpo realmente gastados, que alimentan el daño del estallido. */
    private static int selfDamage(ServerPlayer sp, PlayerStatsAttachment att, double rawRatio) {
        int max = att.getBodyMax();
        int loss = Math.min(att.getBody(),
                (int) Math.ceil(max * KiCombatServer.selfDamageFraction(rawRatio)));
        att.addBody(-loss);

        if (att.getBody() <= 0) {
            att.setBody(0);
            // Fuente propia (KI_SELF_DESTRUCT) en vez de magic() genérico: así, si esto es lo
            // que finalmente lo mata (ver DeathCauseTracker), el mensaje dice "se autodestruyó"
            // en vez del genérico "murió".
            sp.hurt(sp.damageSources().source(ModDamageTypes.KI_SELF_DESTRUCT, sp), Float.MAX_VALUE);
        }
        return loss;
    }

    /** Entidad pegada al dueño: la explosión no viaja. `life` es la MECHA, y coincide con el
     *  clip de disparo para que el estallido caiga cuando el cuerpo se abre. */
    private static void spawnAttached(ServerPlayer sp, KiTechnique tech, double damage,
                                      TechniqueEffect effect) {
        KiProjectileEntity proj = new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), sp.level());
        proj.configure(sp, tech.type(), tech.rgb(), tech.size(), damage,
                tech.type().animTicks(), effect);
        Vec3 c = sp.position().add(0, sp.getBbHeight() * 0.5, 0);
        proj.setPos(c.x, c.y - proj.getBbHeight() * 0.5, c.z);
        sp.level().addFreshEntity(proj);

        SoundEvent snd = TechniqueAssets.soundOf(tech.releaseSound());
        if (snd != null) {
            sp.level().playSound(null, c.x, c.y, c.z, snd, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }
    private static void spawnProjectile(ServerPlayer sp, KiTechnique tech, double damage,
                                        TechniqueEffect effect, int index) {
        KiTechniqueType type = tech.type();
        KiProjectileEntity proj = new KiProjectileEntity(ModEntities.KI_PROJECTILE.get(), sp.level());

        // Dispersión solo en ráfagas (el primer proyectil va recto).
        float yawJitter = index == 0 ? 0
                : (sp.getRandom().nextFloat() - 0.5f) * 2 * BURST_SPREAD_DEG;
        float pitchJitter = index == 0 ? 0
                : (sp.getRandom().nextFloat() - 0.5f) * 2 * BURST_SPREAD_DEG;

        Vec3 dir = Vec3.directionFromRotation(sp.getXRot() + pitchJitter, sp.getYRot() + yawJitter);

        // El punto de salida lo decide la técnica (mano, boca, frente...). El enum ya lo
        // orienta con la mirada y lo escala con el tamaño del jugador; aquí solo se empuja
        // un poco hacia delante para que no nazca dentro del propio modelo.
        Vec3 spawn = tech.position().origin(sp).add(dir.scale(0.45));

        proj.configure(sp, type, tech.rgb(), tech.size(), damage, 100, effect);
        proj.setPos(spawn.x, spawn.y, spawn.z);
        proj.setDeltaMovement(dir.scale(type.speed()));
        sp.level().addFreshEntity(proj);

        // Sonido de disparo: solo con el primer proyectil, o una ráfaga lo solaparía cinco
        // veces. Desde el servidor y con player=null, así lo oye cualquiera de alrededor.
        if (index == 0) {
            SoundEvent snd = TechniqueAssets.soundOf(tech.releaseSound());
            if (snd != null) {
                sp.level().playSound(null, spawn.x, spawn.y, spawn.z,
                        snd, SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }
    }
}