package plugin.gamemodes.crawlerarena;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.struct.*;
import arc.util.*;
import arc.util.pooling.Pools;
import mindustry.ai.types.CommandAI;
import mindustry.content.*;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.abilities.UnitSpawnAbility;
import mindustry.entities.bullet.SapBulletType;
import mindustry.entities.units.StatusEntry;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.net.Administration;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.liquid.Conduit;
import mindustry.world.blocks.payloads.BuildPayload;
import plugin.Bundle;

import static mindustry.Vars.*;
import static plugin.gamemodes.crawlerarena.CVars.*;

public class CrawlerArenaGamemode {
    public static CrawlerArenaGamemode instance = null;

    public static boolean gameIsOver = true, waveIsOver = false, firstWaveLaunched = false;

    public static int worldWidth, worldHeight, worldCenterX, worldCenterY;
    public static float statScaling = 1f;

    public static ObjectFloatMap<String> money = new ObjectFloatMap<>();
    public static ObjectMap<String, UnitType> units = new ObjectMap<>();
    public static ObjectIntMap<String> unitIDs = new ObjectIntMap<>();

    public static ObjectIntMap<UnitType> spawnsLeft = new ObjectIntMap<>();

    public static Seq<Building> toRespawn = new Seq<>();
    public static Interval respawnInterval = new Interval();
    public static Interval retargetInterval = new Interval();

    public static Seq<Timer.Task> timers = new Seq<>(); // for cleanup purposes

    public static long timer = Time.millis();
    public static long waveStartTime = Time.millis();

    public static void init() {
        UnitTypes.crawler.aiController = CommandAI::new;
        UnitTypes.atrax.aiController = CommandAI::new;
        UnitTypes.spiroct.aiController = CommandAI::new;
        UnitTypes.arkyid.aiController = CommandAI::new;
        UnitTypes.toxopid.aiController = CommandAI::new;

        UnitTypes.poly.controller = u -> new SwarmAI();
        UnitTypes.mega.controller = u -> u.team == CVars.reinforcementTeam ? new ReinforcementAI() : !u.type.playerControllable || (u.team.isAI() && !u.team.rules().rtsAi) ? u.type.aiController.get() : new CommandAI();

        UnitTypes.scepter.aiController = CommandAI::new;
        UnitTypes.reign.aiController = CommandAI::new;

        UnitTypes.risso.flying = true;
        UnitTypes.minke.flying = true;
        UnitTypes.bryde.flying = true;
        UnitTypes.sei.flying = true;
        UnitTypes.omura.flying = true;

        UnitTypes.retusa.flying = true;
        UnitTypes.oxynoe.flying = true;
        UnitTypes.cyerce.flying = true;
        UnitTypes.aegires.flying = true;
        UnitTypes.navanax.flying = true;

        UnitTypes.crawler.maxRange = 8000f;
        UnitTypes.atrax.maxRange = 8000f;
        UnitTypes.spiroct.maxRange = 8000f;
        UnitTypes.arkyid.maxRange = 8000f;
        UnitTypes.toxopid.maxRange = 8000f;
        UnitTypes.reign.maxRange = 8000f;

        UnitTypes.poly.maxRange = 2000f;

        UnitTypes.reign.speed = 2.5f;

        UnitTypes.poly.abilities.add(new UnitSpawnAbility(UnitTypes.poly, 480f, 0f, -32f));
        UnitTypes.poly.health = 125f;
        UnitTypes.poly.speed = 1.5f;

        UnitTypes.arkyid.weapons.each(w -> {
            if (w.bullet instanceof SapBulletType sap) sap.sapStrength = 0f;
        });

        Events.on(WorldLoadEvent.class, event -> {
            if (state.rules.defaultTeam.core() != null) {
                Core.app.post(() -> state.rules.defaultTeam.cores().each(c -> c.tile.setNet(Blocks.air)));
            }

            reset();
            Core.app.post(() -> {
                state.rules.canGameOver = false;
                state.rules.waveTimer = false;
                state.rules.waves = true;
                state.rules.unitCap = unitCap;
                state.rules.enemyCoreBuildRadius = 0f;
                state.rules.dropZoneRadius = 0f;
                state.rules.env = defaultEnv;
                // state.rules.hiddenBuildItems.clear();
                state.rules.planet = Planets.sun;
                Call.setRules(state.rules);
                newGame();
            });

            worldWidth = world.width() * tilesize;
            worldHeight = world.height() * tilesize;
            worldCenterX = worldWidth / 2;
            worldCenterY = worldHeight / 2;
            firstWaveLaunched = false;
            waveIsOver = true;
            timer = Time.millis();
        });

        Events.on(GameOverEvent.class, event -> gameIsOver = true);

        Events.on(PlayerJoin.class, event -> {
            if (!money.containsKey(event.player.uuid()) || !units.containsKey(event.player.uuid())) {
                //Bundle.sendMessage("events.join.welcome", event.player);
                Bundle.sendMessage("crawler.events.join.welcome", event.player);
                // let the "player gc" take care of this
            } else {
                //Bundle.sendMessage("events.join.already-played", event.player);
                Bundle.sendMessage("crawler.events.join.already-played", event.player);

                if (unitIDs.containsKey(event.player.uuid())) {
                    Unit swapTo = Groups.unit.getByID(unitIDs.get(event.player.uuid()));
                    if (swapTo != null) {
                        if (swapTo.getPlayer() != null && unitIDs.containsKey(swapTo.getPlayer().uuid())) {
                            Player intruder = swapTo.getPlayer();
                            Unit swapIntruderTo = Groups.unit.getByID(unitIDs.get(intruder.uuid()));
                            if (swapIntruderTo != null) {
                                intruder.unit(swapIntruderTo);
                            } else {
                                intruder.clearUnit();
                            }
                        }
                        timers.add(Timer.schedule(() -> {
                            if (!swapTo.dead && swapTo != null) {
                                event.player.unit(swapTo);
                            }
                        }, 1f));
                    }
                } else {
                    respawnPlayer(event.player);
                }
            }
        });

        Events.on(BlockDestroyEvent.class, (e) -> toRespawn.add(e.tile.build));

        Events.run(Trigger.update, () -> {
            if (gameIsOver) return;

            Groups.player.each(p -> {
                if (!money.containsKey(p.uuid())) {
                    money.put(p.uuid(), waveMoney(state.wave));
                }
                if (!units.containsKey(p.uuid())) {
                    units.put(p.uuid(), UnitTypes.dagger);
                    respawnPlayer(p);
                }
            }); // "player GC" for players with missing entries

            if (!Groups.unit.contains(u -> u.team == state.rules.defaultTeam)) {
                gameIsOver = true;
                if (state.wave > bossWave) {
                    // Bundle.sendMessage("events.gameover.win");
                    Bundle.sendMessage("crawler.events.gameover.win");
                    Timer.schedule(() -> Events.fire(new GameOverEvent(state.rules.defaultTeam)), 2f);
                } else {
                    //Bundle.sendMessage("events.gameover.lose");
                    Bundle.sendMessage("crawler.events.gameover.lose");
                    Timer.schedule(() -> Events.fire(new GameOverEvent(state.rules.waveTeam)), 2f);
                }
                return;
            }

            Groups.unit.each(u -> {
                if (!u.isFlying()) return;
                Tile t = u.tileOn();
                if (t != null && t.solid() && !t.synthetic()) {
                    float[] tpx = new float[]{-1f};
                    float[] tpy = new float[]{-1f};
                    int[] deltaXMin = new int[]{Integer.MAX_VALUE / 4};
                    int[] deltaYMin = new int[]{Integer.MAX_VALUE / 4};
                    Geometry.circle(u.tileX(), u.tileY(), 5, (x, y) -> {
                        Tile tile = world.tile(x, y);
                        if (tile != null && (!tile.solid() || t.synthetic()) && Math.abs(u.tileX() - x) + Math.abs(u.tileY() - y) < deltaXMin[0] + deltaYMin[0]) {
                            tpx[0] = (float) x * tilesize;
                            tpy[0] = (float) y * tilesize;
                            deltaXMin[0] = Math.abs(u.tileX() - x);
                            deltaYMin[0] = Math.abs(u.tileY() - y);
                        }
                    });
                    if (tpx[0] != -1f && tpy[0] != -1f) {
                        u.set(tpx[0], tpy[0]);
                        if (u.getPlayer() != null) {
                            Call.setPosition(u.getPlayer().con, tpx[0], tpy[0]);
                        }
                    }
                }
            });

            Groups.player.each(p -> Call.setHudText(p.con, Bundle.get("crawler.labels.money", p.locale, Mathf.round(money.get(p.uuid(), 0f)))));

            if (Mathf.chance(1f * tipChance * Time.delta)) Bundle.sendMessage("crawler.events.tip.info");
            if (Mathf.chance(1f * tipChance * Time.delta)) Bundle.sendMessage("crawler.events.tip.upgrades");

            if (!spawnsLeft.isEmpty()) {
                float timePassed = (Time.millis() - waveStartTime) * 0.001f;
                for (ObjectIntMap.Entry<UnitType> e : spawnsLeft.entries()) {
                    float maxSpawnTime = enemyMaxSpawnTimes.get(e.key, 5f) + enemySpawnTimeRamps.get(e.key, 0f) * state.wave;
                    float spawnRate = Mathf.sqrt(e.value) * Math.min(1f, timePassed / maxSpawnTime);
                    if (Mathf.random() < Time.delta / 60f * spawnRate) {
                        spawnEnemy(e.key);
                        int val = e.value;
                        spawnsLeft.put(e.key, e.value - 1);
                        if (val - 1 <= 0) {
                            spawnsLeft.remove(e.key);
                        }
                    }
                }
            } else if (!Groups.unit.contains(u -> u.team == state.rules.waveTeam) && !waveIsOver) {
                endWave();
            }
            if (retargetInterval.get(retargetDelay)) {
                Groups.unit.each(u -> u.team == state.rules.waveTeam && Mathf.chance(retargetChance * Mathf.sqrt(Groups.unit.size())), CrawlerArenaGamemode::makeAttack);
            }
            if (!waveIsOver) {
                enemyTypes.each(type -> type.speed += enemySpeedBoost * Time.delta * statScaling);
            } else if (respawnInterval.get(120f)) {
                for (int i = 0; i < toRespawn.size; i++) {
                    Building b = toRespawn.get(i);
                    Block block = b.block;
                    boolean valid = true;
                    for (int xi = b.tileX() - (block.size - 1) / 2; xi <= b.tileX() + block.size / 2; xi++) {
                        for (int yi = b.tileY() - (block.size - 1) / 2; yi <= b.tileY() + block.size / 2; yi++) {
                            if (world.tile(xi, yi).build != null) {
                                valid = false;
                                break;
                            }
                        }
                        if (!valid) {
                            break;
                        }
                    }
                    valid = valid && !Units.anyEntities(b.x - block.size * tilesize / 2f, b.y - block.size * tilesize / 2f, block.size * tilesize, block.size * tilesize);
                    if (valid) {
                        b.tile.setNet(block, b.team, b.rotation);
                        b.tile.build.configure(b.config());
                        toRespawn.remove(b);
                        i--;
                    } else {
                        Call.effect(Fx.unitCapKill, b.x, b.y, 1, Color.white);
                    }
                    Call.effect(Fx.placeBlock, b.x, b.y, (float) block.size, Color.white);
                }
            }
        });

        netServer.admins.addActionFilter(action -> action.type != Administration.ActionType.breakBlock && action.type != Administration.ActionType.placeBlock);

        Log.info("Crawler Arena loaded.");
    }

    public static void endWave() {
        if (state.wave < reinforcementMinWave || state.wave % reinforcementSpacing != 0) {
            Bundle.sendMessage("crawler.events.wave", (int) (waveDelay + state.wave * waveDelayRamp));
            timers.add(Timer.schedule(CrawlerArenaGamemode::nextWave, waveDelay + state.wave * waveDelayRamp));
        } else {
            Bundle.sendMessage("crawler.events.next-wave", (int) (Math.min(reinforcementWaveDelayBase + state.wave * reinforcementWaveDelayRamp, reinforcementWaveDelayMax)));
            timers.add(Timer.schedule(CrawlerArenaGamemode::spawnReinforcements, 2.5f));
            timers.add(Timer.schedule(CrawlerArenaGamemode::nextWave, Math.min(reinforcementWaveDelayBase + state.wave * reinforcementWaveDelayRamp, reinforcementWaveDelayMax)));
        }
        Groups.player.each(p -> {
            respawnPlayer(p);
            money.put(p.uuid(), waveMoney(state.wave));
        });
        waveIsOver = true;
    }

    public static float waveMoney(int wave) {
        return Mathf.pow(moneyExpBase, 1f + wave * (moneyRamp + extraMoneyRamp * wave)) * moneyMultiplier;
    }

    public static void makeAttack(Unit u) {
        if (u.controller() instanceof CommandAI c) {
            Teamc target = Units.closestTarget(u.team, u.x, u.y, u.range(), tgt -> {
                if (!tgt.checkTarget(u.type.targetAir, u.type.targetGround)) return false;
                Tile tile = world.tile(tgt.tileX(), tgt.tileY());
                return (tile != null && (!tile.solid() || tile.team() == state.rules.defaultTeam)) || u.isFlying();
            }, tgt -> u.type.targetGround && !(tgt.block instanceof Conveyor || tgt.block instanceof Conduit));
            if (target != null) {
                c.commandTarget(target);
            }
        }
    }

    public static void newGame() {
        if (firstWaveLaunched) return;
        if (Groups.player.isEmpty()) {
            Timer.schedule(CrawlerArenaGamemode::newGame, 5f);
            gameIsOver = true;
            return;
        }
        Timer.schedule(() -> gameIsOver = false, 5f);

        Bundle.sendMessage("crawler.events.first-wave", (int) firstWaveDelay);
        Timer.schedule(CrawlerArenaGamemode::nextWave, firstWaveDelay);
        firstWaveLaunched = true;
        waveIsOver = true;
    }

    public static class DeliverySpecifier {
        Block block;
        int amount;
        boolean skydrop;

        public DeliverySpecifier(Block block, int amount, boolean skydrop) {
            this.block = block;
            this.amount = amount;
            this.skydrop = skydrop;
        }
    }

    public static void spawnReinforcements() {
        spawnReinforcements(Mathf.round(Mathf.sqrt(Groups.player.size()) * state.wave * (1f + state.wave * reinforcementRamp) * reinforcementScaling * statScaling));
    }

    public static void spawnReinforcements(int deliveryAmount) {
        Bundle.sendMessage("crawler.events.aid");
        Seq<DeliverySpecifier> blocks = new Seq<>();
        DropSpecifier guaranteed = guaranteedDrops.get(state.wave);
        if (guaranteed != null) {
            for (int i = 0; i < guaranteed.size(); i++) {
                blocks.add(new DeliverySpecifier(guaranteed.blocks.get(i), guaranteed.amounts.get(i), true));
            }
        }
        for (int i = 0; i < deliveryAmount; i++) {
            DropSpecifier spec = randomDrop();
            for (int j = 0; j < spec.size(); j++) {
                Block block = spec.blocks.get(j);
                blocks.add(new DeliverySpecifier(block,
                        spec.amounts.get(j),
                        guaranteedAirdrops.contains(block) || Mathf.chance(blockDropChance)));
            }
        }

        IntSeq skydropPoints = new IntSeq();
        int skydropIndex = 0;
        for (DeliverySpecifier d : blocks) {
            Block block = d.block;
            int blockAmount = d.amount;
            if (d.skydrop) {
                int x = Mathf.round(worldCenterX / tilesize);
                int y = Mathf.round(worldCenterY / tilesize);
                Player at = Groups.player.find(pl -> !pl.dead());
                if (at != null && at.unit() != null) {
                    x = at.unit().tileX();
                    y = at.unit().tileY();
                }
                int j = 0;
                while ((j < maxAirdropSearches * blockAmount && skydropPoints.size - skydropIndex < blockAmount) || world.tile(x, y) == null) {
                    x += Mathf.random(-3, 3);
                    x = Mathf.clamp(x, block.size, world.width() - block.size);
                    y += Mathf.random(-3, 3);
                    y = Mathf.clamp(y, block.size, world.height() - block.size);
                    boolean valid = true;
                    for (int xi = x - (block.size - 1) / 2; xi <= x + block.size / 2; xi++) {
                        for (int yi = y - (block.size - 1) / 2; yi <= y + block.size / 2; yi++) {
                            if (world.tile(xi, yi).solid() || skydropPoints.contains(Point2.pack(xi, yi))) {
                                valid = false;
                                break;
                            }
                        }
                        if (!valid) {
                            break;
                        }
                    }
                    valid = valid && !Units.anyEntities(x * tilesize + block.offset - block.size * tilesize / 2f, y * tilesize + block.offset - block.size * tilesize / 2f, block.size * tilesize, block.size * tilesize);
                    if (valid) {
                        skydropPoints.add(Point2.pack(x, y));
                    }
                    j++;
                }
                for (int i = skydropIndex; i < skydropPoints.size; i++) {
                    int v = skydropPoints.get(i);
                    Point2 unpacked = Point2.unpack(v);
                    float xf = unpacked.x * tilesize;
                    float yf = unpacked.y * tilesize;
                    Call.effect(Fx.blockCrash, xf, yf, 0, Color.white, block);
                    Time.run(100f, () -> {
                        Call.soundAt(Sounds.explosion, xf, yf, 1, 1);
                        Call.effect(Fx.spawnShockwave, xf, yf, block.size * 60f, Color.white);
                        world.tileWorld(xf, yf).setNet(block, state.rules.defaultTeam, 0);
                    });
                }
                skydropIndex += blockAmount;
            } else {
                float rot = Mathf.random(360f * Mathf.degRad);
                float x = worldCenterX * Mathf.cos(rot);
                float y = worldCenterY * Mathf.sin(rot);
                Unit u = UnitTypes.mega.spawn(reinforcementTeam, worldCenterX + x, worldCenterY + y);
                u.health = Integer.MAX_VALUE;
                if (u instanceof Payloadc pay) {
                    for (int i = 0; i < blockAmount; i++) {
                        pay.addPayload(new BuildPayload(block, state.rules.defaultTeam));
                    }
                }
            }
        }
    }

    public static void respawnPlayer(Player p) {
        int resX = Mathf.round(worldCenterX / tilesize);
        int resY = Mathf.round(worldCenterY / tilesize);
        Player at = Groups.player.find(pl -> !pl.dead());
        if (at != null && at.unit() != null) {
            resX = at.unit().tileX();
            resY = at.unit().tileY();
        }
        if (p.dead() || p.unit().id != unitIDs.get(p.uuid())) {
            Unit oldUnit = Groups.unit.getByID(unitIDs.get(p.uuid()));
            if (oldUnit != null && oldUnit != p.unit()) {
                oldUnit.kill();
            }
            int randomMag = 6;
            int x = Mathf.clamp(resX + Mathf.random(-randomMag, randomMag), 0, world.width() - 1);
            int y = Mathf.clamp(resY + Mathf.random(-randomMag, randomMag), 0, world.height() - 1);
            Tile tile = world.tile(x, y);
            UnitType type = units.get(p.uuid());
            if (type == null) { // why does this happen
                type = UnitTypes.dagger;
            }
            if (!type.flying && tile.solid()) {
                int tries = 0;
                while (world.tile(x, y).solid()) {
                    x += Mathf.random(-3, 3);
                    x = Mathf.clamp(x, 0, world.width() - 1);
                    y += Mathf.random(-3, 3);
                    y = Mathf.clamp(y, 0, world.height() - 1);
                    tries++;
                    if (tries > 200) {
                        world.tile(x, y).setNet(Blocks.air);
                        break;
                    }
                }
            }
            Unit unit = type.spawn(x * tilesize, y * tilesize);
            setUnit(unit);
            p.unit(unit);
            unitIDs.put(p.uuid(), unit.id);
            return;
        }

        if (p.unit().health < p.unit().maxHealth) {
            p.unit().heal();
            Bundle.sendMessage("crawler.events.heal", p, Pal.heal);
        }
    }

    public static void applyStatus(Unit unit, float duration, int amount, StatusEffect... effects) {
        Seq<StatusEntry> entries = new Seq<>();
        for (int i = 0; i < amount; i++) {
            for (StatusEffect effect : effects) {
                StatusEntry entry = Pools.obtain(StatusEntry.class, StatusEntry::new);
                entry.set(effect, duration);
                entries.add(entry);
            }
        }
        var fields = unit.getClass().getFields();
        for (var field : fields) {
            if (field.getName().equals("statuses")) {
                try {
                    if (field.get(unit) instanceof Seq s) {
                        s.addAll(entries);
                    }
                } catch (Exception ignore) {
                }
            }
        }
    }

    public static void applyStatus(Unit unit, float duration, StatusEffect... effects) {
        applyStatus(unit, duration, 1, effects);
    }

    public static void spawnEnemy(UnitType unit) {
        float sX = 32;
        float sY = 32;
        Seq<Tile> spawns = spawner.getSpawns();
        if (spawns.isEmpty()) {
            float spX = worldWidth * 0.4f;
            float spY = worldHeight * 0.4f;
            switch (Mathf.random(0, 3)) {
                case 0 -> {
                    sX = worldWidth - 32;
                    sY = worldCenterY + Mathf.random(-spY, spY);
                }
                case 1 -> {
                    sX = worldCenterX + Mathf.random(-spX, spX);
                    sY = worldHeight - 32;
                }
                case 2 -> sY = worldCenterY + Mathf.random(-spY, spY);
                case 3 -> sX = worldCenterX + Mathf.random(-spX, spX);
            }
        } else {
            Tile at = spawns.random();
            sX = at.getX() + Mathf.random(-32f, 32f);
            sY = at.getY() + Mathf.random(-32f, 32f);
        }

        Unit u = unit.spawn(state.rules.waveTeam, sX, sY);
        if (world.tileWorld(sX, sY).solid()) {
            u.elevation = 1f;
        }
        u.armor = 0f;
        u.maxHealth *= statScaling * healthMultiplierBase;
        u.health = u.maxHealth;

        if (unit == UnitTypes.reign) {
            u.apply(StatusEffects.boss);
            if (Groups.player.size() > bossT1Cap) {
                u.apply(StatusEffects.overclock);
            }
            if (Groups.player.size() > bossT2Cap) {
                u.apply(StatusEffects.overdrive);
            }
            if (Groups.player.size() > bossT3Cap) {
                applyStatus(u, Float.MAX_VALUE, StatusEffects.overdrive, StatusEffects.overclock);
            }
            float totalHealth = 0f;
            for (Unit un : Groups.unit) {
                totalHealth += un.maxHealth * (un.team == state.rules.defaultTeam ? 1 : 0);
            }
            if (totalHealth >= bossBuffThreshold) {
                applyStatus(u, Float.MAX_VALUE, (int) totalHealth / bossBuffThreshold, StatusEffects.overdrive, StatusEffects.overclock);
            }
            u.maxHealth *= bossHealthMultiplier * Mathf.sqrt(Groups.player.size());
            u.health = u.maxHealth;
            addUnitAbility(u, new UnitSpawnAbility(UnitTypes.scepter, bossScepterDelayBase / Groups.player.size(), 0, -32));
        }
        makeAttack(u);
    }

    public static void nextWave() {
        state.wave++;
        waveStartTime = Time.millis();
        statScaling = 1f + state.wave * statScalingNormal;
        Timer.schedule(() -> waveIsOver = false, 1f);

        int crawlers = Mathf.ceil(Mathf.pow(crawlersExpBase, 1f + state.wave * crawlersRamp * (1f + state.wave * extraCrawlersRamp)) * Groups.player.size() * crawlersMultiplier);

        if (state.wave == bossWave - 5) Bundle.sendMessage("crawler.events.good-game");
        else if (state.wave == bossWave - 3) Bundle.sendMessage("crawler.events.what-so-long");
        else if (state.wave == bossWave - 1) Bundle.sendMessage("crawler.events.why-alive");
        else if (state.wave == bossWave) {
            Bundle.sendMessage("crawler.events.boss");
            spawnEnemy(UnitTypes.reign);
            return;
        } else if (state.wave == bossWave + 1) {
            Bundle.sendMessage("crawler.events.victory", Time.timeSinceMillis(timer) / 1000f);
        } else if (state.wave > maxWave) {
            gameIsOver = true;
            Bundle.sendMessage("crawler.events.gameover.win");
            Timer.schedule(() -> Events.fire(new GameOverEvent(state.rules.defaultTeam)), 2f);
            return;
        }

        if (crawlers > crawlersCeiling) {
            crawlers = crawlersCeiling;
        }

        UnitTypes.crawler.health += crawlerHealthRamp * state.wave * statScaling;
        UnitTypes.crawler.speed += crawlerSpeedRamp * state.wave * statScaling;

        int totalTarget = maxUnits - keepCrawlers;
        for (UnitType type : enemyTypes) {
            int typeCount = Math.min(crawlers / enemyCrawlerCuts.get(type), totalTarget / 2);
            totalTarget -= typeCount;
            if (typeCount != 0) {
                spawnsLeft.put(type, typeCount);
            }
            crawlers -= typeCount * enemyCrawlerCuts.get(type) / 2;
            type.speed = defaultEnemySpeeds.get(type, 1f);
        }
        crawlers = Math.min(crawlers, keepCrawlers);
        if (crawlers != 0) {
            spawnsLeft.put(UnitTypes.crawler, crawlers);
        }
    }

    public static void addUnitAbility(Unit unit, Ability ability) {
        unit.abilities = Seq.with(unit.abilities).add(ability).toArray();
    }

    public static void setUnit(Unit unit, boolean ultraEligible) {
        if (unit.type == UnitTypes.crawler) {
            unit.maxHealth = playerCrawlerHealth;
            unit.health = unit.maxHealth;
            unit.armor = playerCrawlerArmor;
            addUnitAbility(unit, new UnitSpawnAbility(UnitTypes.crawler, playerCrawlerCooldown, 0f, -8f));
            unit.apply(StatusEffects.boss);
            unit.apply(StatusEffects.overclock, Float.MAX_VALUE);
            unit.apply(StatusEffects.overdrive, Float.MAX_VALUE);
        } else if (unit.type == UnitTypes.mono) {
            unit.maxHealth = playerMonoHealth;
            unit.health = unit.maxHealth;
            unit.armor = playerMonoArmor;
            addUnitAbility(unit, new UnitSpawnAbility(playerMonoSpawnTypes.random(), playerMonoCooldown, 0f, -8f));
            unit.apply(StatusEffects.boss);
            unit.apply(StatusEffects.overclock, Float.MAX_VALUE);
            unit.apply(StatusEffects.overdrive, Float.MAX_VALUE);
        } else if (unit.type == UnitTypes.poly) {
            unit.maxHealth = playerPolyHealth;
            unit.health = unit.maxHealth;
            unit.armor = playerPolyArmor;
            unit.apply(StatusEffects.boss);
            unit.apply(StatusEffects.overclock, Float.MAX_VALUE);
            unit.apply(StatusEffects.overdrive, Float.MAX_VALUE);
            for (Ability ab : unit.abilities) {
                if (ab instanceof UnitSpawnAbility s) {
                    s.spawnTime = playerPolyCooldown;
                }
            }
        } else if (unit.type == UnitTypes.omura) {
            unit.maxHealth = playerOmuraHealth;
            unit.health = unit.maxHealth;
            unit.armor = playerOmuraArmor;
            unit.apply(StatusEffects.boss);
            unit.apply(StatusEffects.overclock, Float.MAX_VALUE);
            unit.apply(StatusEffects.overdrive, Float.MAX_VALUE);
            for (Ability ab : unit.abilities) {
                if (ab instanceof UnitSpawnAbility s) {
                    s.spawnTime = playerOmuraCooldown;
                }
            }
        } else if (ultraEligible && unit.type == UnitTypes.dagger && Mathf.chance(ultraDaggerChance)) {
            unit.maxHealth = ultraDaggerHealth;
            unit.health = unit.maxHealth;
            unit.armor = ultraDaggerArmor;
            addUnitAbility(unit, new UnitSpawnAbility(UnitTypes.dagger, ultraDaggerCooldown, 0f, -1f));
            applyStatus(unit, Float.MAX_VALUE, 3, StatusEffects.overclock, StatusEffects.overdrive, StatusEffects.boss);
        }
    }

    public static void setUnit(Unit unit) {
        setUnit(unit, false);
    }

    public static void reset() {
        statScaling = 1f;
        UnitTypes.crawler.speed = crawlerSpeedBase;
        UnitTypes.crawler.health = crawlerHealthBase;
        money.clear();
        units.clear();
        unitIDs.clear();
        spawnsLeft.clear();
        toRespawn.clear();
        timers.each(Timer.Task::cancel);
        timers.clear();
    }

    public static UnitType findType(String name) {
        Seq<UnitType> types = Seq.with(unitCosts.keys());
        //UnitType type = types.filter(u -> u.name.contains(name)).min(u -> Strings.levenshtein(u.name, name));
        UnitType type = types.find(u -> u.name.contains(name));
        return type == null ? Seq.with(unitCosts.keys()).min(u -> Strings.levenshtein(u.name, name)) : type;
    }

    public static void registerServerCommands(CommandHandler handler) {
        handler.register("kill", "Kill all enemies in the current wave.", args -> Groups.unit.each(u -> u.team == state.rules.waveTeam, Unitc::kill));
        handler.register("spawnaid", "[amount]", "Spawn aid drops.", args -> {
            if (args.length > 0) {
                int amount;
                try {
                    amount = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    Log.info("Invalid amount.");
                    return;
                }
                spawnReinforcements(amount);
            } else {
                spawnReinforcements();
            }
        });
    }
}
