package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.master.KaioEntity;
import com.hmc.zenkai.content.entity.master.KamiEntity;
import com.hmc.zenkai.content.entity.master.KorinEntity;
import com.hmc.zenkai.content.entity.master.ToriBotEntity;
import com.hmc.zenkai.content.entity.misc.IsaacEntity;
import com.hmc.zenkai.content.entity.misc.ShadowCloneEntity;
import com.hmc.zenkai.content.entity.misc.ShadowKintounEntity;
import com.hmc.zenkai.content.entity.namek.NamekianEntity;
import com.hmc.zenkai.content.entity.namek.NamekianWarriorEntity;
import com.hmc.zenkai.content.entity.misc.KintounEntity;
import com.hmc.zenkai.content.entity.otherworld.OgreEntity;
import com.hmc.zenkai.content.entity.otherworld.YemmaEntity;
import com.hmc.zenkai.content.entity.misc.SpacePodEntity;
import com.hmc.zenkai.content.entity.overworld.SaibamanEntity;
import com.hmc.zenkai.content.entity.overworld.ShenLongEntity;
import com.hmc.zenkai.content.entity.technique.ClashOrbEntity;
import com.hmc.zenkai.content.entity.technique.KiArrowEntity;
import com.hmc.zenkai.content.entity.technique.KiLingeringZoneEntity;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, Zenkai.MOD_ID);

    public static final Supplier<EntityType<SpacePodEntity>> SPACE_POD =
            ENTITY_TYPES.register("space_pod",() -> EntityType.Builder.of(SpacePodEntity::new, MobCategory.CREATURE)
                    .sized(2.45f,2.5f).build("space_pod"));

    public static final Supplier<EntityType<KintounEntity>> KINTOUN =
            ENTITY_TYPES.register("kintoun",() -> EntityType.Builder.of(KintounEntity::new, MobCategory.CREATURE)
                    .sized(2.3f,0.8f).build("kintoun"));

    public static final Supplier<EntityType<ShadowKintounEntity>> SHADOW_KINTOUN =
            ENTITY_TYPES.register("kintoun_shadow",() -> EntityType.Builder.of(ShadowKintounEntity::new, MobCategory.CREATURE)
                    .sized(2.3f,0.8f).build("kintoun_shadow"));

    public static final Supplier<EntityType<ShenLongEntity>> SHENLONG =
            ENTITY_TYPES.register("shenlong",() -> EntityType.Builder.of(ShenLongEntity::new, MobCategory.MISC)
                    .sized(5f,15f).build("shenlong"));

    public static final Supplier<EntityType<NamekianWarriorEntity>> NAMEKIAN_WARRIOR =
            ENTITY_TYPES.register("namekian_warrior",() -> EntityType.Builder.of(NamekianWarriorEntity::new, MobCategory.CREATURE)
                    .sized(1,2).build("namekian_warrior"));

    public static final Supplier<EntityType<SaibamanEntity>> SAIBAMAN =
            ENTITY_TYPES.register("saibaman",() -> EntityType.Builder.of(SaibamanEntity::new, MobCategory.CREATURE)
                    .sized(1,2f).build("saibaman"));

    public static final Supplier<EntityType<NamekianEntity>> NAMEKIAN =
            ENTITY_TYPES.register("namekian",() -> EntityType.Builder.of(NamekianEntity::new, MobCategory.CREATURE)
                    .sized(1,2).build("namekian"));

    public static final Supplier<EntityType<IsaacEntity>> ISAAC =
            ENTITY_TYPES.register("isaac", () -> EntityType.Builder.of(IsaacEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).build("isaac"));

    public static final Supplier<EntityType<KiProjectileEntity>> KI_PROJECTILE =
            ENTITY_TYPES.register("ki_projectile", () -> EntityType.Builder
                    .of(KiProjectileEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("ki_projectile"));

    /** LINGERING (TechniqueEffect): zona de daño periódico, nace donde impacta la técnica.
     *  clientTrackingRange(0): nunca se manda al cliente, así que no hace falta renderer — ver
     *  el javadoc de KiLingeringZoneEntity. */
    public static final Supplier<EntityType<KiLingeringZoneEntity>> KI_LINGERING_ZONE =
            ENTITY_TYPES.register("ki_lingering_zone", () -> EntityType.Builder
                    .of(KiLingeringZoneEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(0)
                    .build("ki_lingering_zone"));

    /** Esfera del choque de haces (ClashManager). Visible a cualquiera que la siga: SÍ va al
     *  cliente, a diferencia de KI_LINGERING_ZONE. updateInterval alto porque no se mueve. */
    public static final Supplier<EntityType<ClashOrbEntity>> CLASH_ORB =
            ENTITY_TYPES.register("clash_orb", () -> EntityType.Builder
                    .<ClashOrbEntity>of(ClashOrbEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSave()
                    .build("clash_orb"));

    public static final Supplier<EntityType<KiArrowEntity>> KI_ARROW =
            ENTITY_TYPES.register("ki_arrow", () -> EntityType.Builder
                    .of(KiArrowEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(4)
                    .updateInterval(20)
                    .build("ki_arrow"));

    /** Hoja de luz de la técnica LIGHT_BLADE. updateInterval alto: el cliente la mueve por su cuenta
     *  (ver SwordWaveEntity), el servidor solo la corrige de vez en cuando. */
    public static final Supplier<EntityType<com.hmc.zenkai.content.entity.technique.SwordWaveEntity>> SWORD_WAVE =
            ENTITY_TYPES.register("sword_wave", () -> EntityType.Builder
                    .<com.hmc.zenkai.content.entity.technique.SwordWaveEntity>of(
                            com.hmc.zenkai.content.entity.technique.SwordWaveEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSave()
                    .build("sword_wave"));

    /** Masa de la técnica ABSORB del Majin (ver AbsorbMassEntity): como la hoja de luz, la mueve el cliente por su cuenta. */
    public static final Supplier<EntityType<com.hmc.zenkai.content.entity.technique.AbsorbMassEntity>> ABSORB_MASS =
            ENTITY_TYPES.register("absorb_mass", () -> EntityType.Builder
                    .<com.hmc.zenkai.content.entity.technique.AbsorbMassEntity>of(
                            com.hmc.zenkai.content.entity.technique.AbsorbMassEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSave()
                    .build("absorb_mass"));

    /** Dron de entrenamiento de la cámara de gravedad (Fase 4): lo genera la consola en cada ronda; nunca se guarda. */
    public static final Supplier<EntityType<com.hmc.zenkai.content.entity.misc.TrainingDroneEntity>> TRAINING_DRONE =
            ENTITY_TYPES.register("training_drone", () -> EntityType.Builder
                    .<com.hmc.zenkai.content.entity.misc.TrainingDroneEntity>of(
                            com.hmc.zenkai.content.entity.misc.TrainingDroneEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(8)
                    .noSave()
                    .build("training_drone"));

    public static final Supplier<EntityType<YemmaEntity>> YEMMA =
            ENTITY_TYPES.register("yemma", () -> EntityType.Builder.of(YemmaEntity::new, MobCategory.MISC)
                    .sized(4, 10.0f).build("yemma"));

    /** Uranai Baba: visita a la Tierra de los muertos (muerte permanente). Alto hasta la punta del sombrero (tools/gen_uranai_baba.py). */
    public static final Supplier<EntityType<com.hmc.zenkai.content.entity.otherworld.UranaiBabaEntity>> URANAI_BABA =
            ENTITY_TYPES.register("uranai_baba", () -> EntityType.Builder
                    .of(com.hmc.zenkai.content.entity.otherworld.UranaiBabaEntity::new, MobCategory.MISC)
                    .sized(0.8f, 2.0f).build("uranai_baba"));

    public static final Supplier<EntityType<KamiEntity>> KAMI =
            ENTITY_TYPES.register("kami", () -> EntityType.Builder.of(KamiEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.9f).build("kami"));

    public static final Supplier<EntityType<KaioEntity>> KAIO =
            ENTITY_TYPES.register("kaio", () -> EntityType.Builder.of(KaioEntity::new, MobCategory.MISC)
                    .sized(0.8f, 1.4f).build("kaio"));

    public static final Supplier<EntityType<KorinEntity>> KORIN =
            ENTITY_TYPES.register("korin", () -> EntityType.Builder.of(KorinEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.2f).build("korin"));

    public static final Supplier<EntityType<ToriBotEntity>> TORIBOT =
            ENTITY_TYPES.register("toribot", () -> EntityType.Builder.of(ToriBotEntity::new, MobCategory.MISC)
                    // Alto hasta la gorra con la flotación del idle (tools/gen_toribot.py); el halo queda fuera.
                    .sized(0.8f, 2.1f).build("toribot"));

    /** "Train with your shadow" (ShadowTrainingManager): MISC = nunca spawnea sola de forma
     *  natural, solo la coloca el manager al empezar una sesión — mismo criterio que
     *  Kami/Kaio/Korin/Yemma (NPCs colocados a mano, no mobs de bioma). */
    public static final Supplier<EntityType<ShadowCloneEntity>> SHADOW_CLONE =
            ENTITY_TYPES.register("shadow_clone", () -> EntityType.Builder.of(ShadowCloneEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).build("shadow_clone"));

    /** Ogros del HFIL (propuesta I): misma clase, dos texturas (Goz rojo y Mez azul).
     *  AMBIENT (decisión del usuario, 2026-11-15; antes CREATURE y luego MONSTER). La categoría solo decide
     *  CÓMO los spawnea el servidor, no la IA: CREATURE solo los generaba con el chunk y no los reponía
     *  (el HFIL explorado quedaba vacío); MONSTER los repone cada tick, pero en dificultad pacífica el
     *  servidor apaga el spawn de TODA la categoría (probado: no salían). AMBIENT se intenta cada tick, se
     *  repone y NO se apaga en pacífico (es la de los murciélagos). Cupo ~15 alrededor del jugador, por eso
     *  los grupos de ModBiomeGen.hfilBuild son de 2 a 4. Los AMBIENT también se borran al alejarse el
     *  jugador, y se reponen. Tamaño de jugador: el atributo SCALE (1.2) los agranda. */
    public static final Supplier<EntityType<OgreEntity>> RED_OGRE =
            ENTITY_TYPES.register("red_ogre", () -> EntityType.Builder.of(OgreEntity::new, MobCategory.AMBIENT)
                    .sized(0.6f, 1.8f).build("red_ogre"));

    public static final Supplier<EntityType<OgreEntity>> BLUE_OGRE =
            ENTITY_TYPES.register("blue_ogre", () -> EntityType.Builder.of(OgreEntity::new, MobCategory.AMBIENT)
                    .sized(0.6f, 1.8f).build("blue_ogre"));

    /** Ángeles del capullo de Freezer: tamaño de jugador en el tipo, el atributo SCALE (0.45) los
     *  deja del tamaño de un Vex. No aparecen solos: los pone la estructura frieza_cocoon. */
    public static final Supplier<EntityType<com.hmc.zenkai.content.entity.otherworld.AngelEntity>> ANGEL =
            ENTITY_TYPES.register("angel", () -> EntityType.Builder.of(
                            com.hmc.zenkai.content.entity.otherworld.AngelEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.8f).build("angel"));

    /** Cuerpo de un jugador muerto (inventario + rage forms). Tumbado: caja ancha y baja. Se guarda con el
     *  chunk (lleva los objetos) y se sigue a poca distancia: es un objeto del mundo, no un combatiente. */
    public static final Supplier<EntityType<com.hmc.zenkai.content.entity.misc.PlayerBodyEntity>> PLAYER_BODY =
            ENTITY_TYPES.register("player_body", () -> EntityType.Builder
                    .<com.hmc.zenkai.content.entity.misc.PlayerBodyEntity>of(
                            com.hmc.zenkai.content.entity.misc.PlayerBodyEntity::new, MobCategory.MISC)
                    .sized(1.0f, 0.4f)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .build("player_body"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}