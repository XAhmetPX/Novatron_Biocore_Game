package com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.Brains;

import com.novatron.novatronbiocore.Games.FRCGames.AutoActions;
import com.novatron.novatronbiocore.Games.FRCGames.Physic3D;
import com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.FRCGameRebuilt;
import com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.FRCGameRebuilt.FuelBall;
import com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.FRCGameRebuilt.ShotMode;
import com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.FRCGameRebuilt.RobotRebuilt;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class BotSkills{

    /** Top toplama bölgesi adayı. */
    public static final class FuelSpot{
        public float x, z;
        public int count;       //çevresindeki (alınabilir) top sayısı
        public float pathDist;  //robottan yol uzaklığı (m)
        public float score;
    }

    /** Atış noktası adayı: kendi hub'ına atış ya da nötr bölgeden kendi alanına atma  noktası. */
    public static final class ShootSpot{
        public float x, z;
        public ShotMode mode;   //bu noktadaki atış tipi
        public float pathDist;  //robottan yol uzaklığı (m)
        public float targetDist;//atış hedefine uzaklık (m): hub merkezi ya da atma noktası
        public int quality;     //0..8: çevresindeki kaç komşu nokta da atışa uygun (yüksek = atış hatasına dayanıklı)
        public float score;
    }

    private static final float SHOOT_SPOT_STEP = 0.35f;  //atış noktası adaylarının ızgara aralığı (m)
    private static final float QUALITY_OFFSET  = 0.20f;  //kalite testinde komşu uzaklığı (m)
    private static final int   MIN_QUALITY     = 6;      //bu kadar komşusu da uygun değilse nokta aday olmaz
    private static final float DENSITY_RADIUS  = 0.8f;   //top yoğunluğu ölçülen yarıçap (m)
    private static final float MATE_CLAIM_RADIUS = 1.3f; //takım arkadaşının seçtiği noktaya bu kadar yakın nokta cezalı (m)
    private static final float OPP_NEAR_RADIUS   = 1.6f; //rakip bu kadar yakındaki atış noktası cezalı (m)

    public final RobotRebuilt robot;
    private final FRCGameRebuilt game;
    private final Random r;

    //takım ittifaklarının görmesi için şu an gidilen top bölgesi/atış noktası (yoksa NaN)
    public float fuelClaimX = Float.NaN, fuelClaimZ = Float.NaN;
    public float shootClaimX = Float.NaN, shootClaimZ = Float.NaN;

    private ArrayList<ShootSpot> shootSpots, throwSpots;
    private final ArrayList<RobotRebuilt> opponents = new ArrayList<>();
    private final ArrayList<BotSkills> mates = new ArrayList<>();
    private final float[] tmp = new float[2];

    public BotSkills(RobotRebuilt robot, Random r){
        this.robot = robot;
        this.game = robot.game;
        this.r = r;
    }

    public float fillRatio(){ return robot.getIntakeCount() / (float) robot.getIntakeCapacity(); }
    public int spaceLeft(){ return robot.getIntakeCapacity() - robot.getIntakeCount(); }
    public boolean ownHubActive(){ return game.isHubActive(robot.isBlueAlliance); }
    /** Kendi hub'ı aktifleşene kadar saniye (aktifse 0, bir daha aktif olmayacaksa +sonsuz). */
    public float secondsUntilOwnHubActive(){ return game.getSecondsUntilHubActive(robot.isBlueAlliance); }
    /** Kendi hub'ı aktifse bu aktif aralığın bitmesine kalan süre (aktif değilse 0). */
    public float secondsOwnHubActiveRemaining(){ return game.getSecondsHubActiveRemaining(robot.isBlueAlliance); }

    public float ownHubX(){ return FRCGameRebuilt.getHubCenterX(); }
    public float ownHubZ(){ return FRCGameRebuilt.getHubCenterZ(robot.isBlueAlliance); }
    /** Kendi deposunun merkezi (x). */
    public float ownDepotX(){
        final float c = FRCGameRebuilt.DEPOT_OFFSET_X + FRCGameRebuilt.DEPOT_W / 2f;
        return (robot.isBlueAlliance) ? c : FRCGameRebuilt.FIELD_WIDTH_METERS - c;
    }
    /** Kendi deposunun merkezi (z). */
    public float ownDepotZ(){
        return (robot.isBlueAlliance) ? FRCGameRebuilt.FIELD_DEPTH_METERS - FRCGameRebuilt.DEPOT_D / 2f : FRCGameRebuilt.DEPOT_D / 2f;
    }
    /** Depoda yarıçap içinde kaç top var? (beyin "depo boşaldı mı" diye bakar) */
    public int fuelInOwnDepot(){
        return game.getPhysic().countNear(ownDepotX(), ownDepotZ(), 0.8f, GROUND_FUEL);
    }
    public int outpostFuelLeft(){ return (robot.isBlueAlliance) ? game.getBlueOutpostBallCount() : game.getRedOutpostBallCount(); }
    /** Outpost'un altında durulacak nokta (robotun merkezi, köşeye yakın). */
    public float outpostX(){ return (robot.isBlueAlliance) ? FRCGameRebuilt.FIELD_WIDTH_METERS - 0.55f : 0.55f; }
    public float outpostZ(){ return (robot.isBlueAlliance) ? FRCGameRebuilt.FIELD_DEPTH_METERS - 0.55f : 0.55f; }

    public List<RobotRebuilt> opponents(){
        BotActions.opponentsOf(robot, opponents);
        return opponents;
    }

    /** Hub'a en yakın rakip (savunma hedefi / tehdit hesabı için); yoksa null. */
    public RobotRebuilt nearestOpponentTo(float x, float z){
        RobotRebuilt best = null;
        float bestD = Float.MAX_VALUE;
        for(RobotRebuilt o : opponents()){
            float d = (float) Math.hypot(o.position.x - x, o.position.z - z);
            if(d < bestD){ bestD = d; best = o; }
        }
        return best;
    }

    /** En tehlikeli rakip: doluluğu yüksek ve kendi hub'ına yakın olan (yani yakında puan yazacak). */
    public RobotRebuilt mostDangerousOpponent(){
        RobotRebuilt best = null;
        float bestScore = -Float.MAX_VALUE;
        for(RobotRebuilt o : opponents()){
            float full = o.getIntakeCount() / (float) o.getIntakeCapacity();
            float d = (float) Math.hypot(o.position.x - FRCGameRebuilt.getHubCenterX(), o.position.z - FRCGameRebuilt.getHubCenterZ(o.isBlueAlliance));
            float score = full * 10f - d * 0.5f;
            if(score > bestScore){ bestScore = score; best = o; }
        }
        return best;
    }

    private List<BotSkills> mates(){
        mates.clear();
        for(RobotRebuilt r : game.getRobots()){
            if(r == robot || r.isBlueAlliance != robot.isBlueAlliance) continue;
            if(r.brain instanceof SkillBrain){
                BotSkills s = ((SkillBrain) r.brain).getSkills();
                if(s != null) mates.add(s);
            }
        }
        return mates;
    }

    public void clearClaims(){
        fuelClaimX = fuelClaimZ = shootClaimX = shootClaimZ = Float.NaN;
    }

    /** Yerdeki (havada/üstte değil), bir robotun içinde olmayan yakıt. */
    private static final Physic3D.EntityFilter GROUND_FUEL = e -> e instanceof FuelBall && e.position.y < 0.25f;

    /**
     * Toplanacak en iyi bölgeleri puanlar ve (softmax ile) birini seçer. Puan = alınabilecek top sayısı - yol uzaklığı * distWeight
     * - rakip/takım arkadaşı cezası. null: sahada top yok.
     *
     * @param distWeight 1 m yolun "kaç top değerinde" olduğu (yüksek = yakını tercih eder, düşük = kalabalık ama uzak bölgeyi de göze alır)
     * @param rivalFear rakipten korkma (0 = umursamaz, 1 = rakibin daha önce varacağı bölgeden kaçınır)
     * @param temperature seçimin rastgeleliği (küçük = hep en iyi, büyük = daha dağınık)
     */
    public FuelSpot pickFuelSpot(float distWeight, float rivalFear, float temperature){
        final Physic3D world = game.getPhysic();
        if(world == null) return null;
        final NavGrid grid = game.getNavGrid(robot);
        final int space = Math.max(1, spaceLeft());
        final List<BotSkills> mates = mates();
        final List<RobotRebuilt> opps = opponents();

        grid.distanceFrom(robot.position.x, robot.position.z);

        final int K = 6;
        final FuelSpot[] top = new FuelSpot[K];
        int topCount = 0;

        for(int i = 0; i < world.entities.size(); i++){
            final Physic3D.PhysicsEntity e = world.entities.get(i);
            if(!(e instanceof FuelBall) || e.position.y > 0.25f) continue;

            final float bx = e.position.x, bz = e.position.z;
            final float d = grid.distanceTo(bx, bz);
            if(d == Float.POSITIVE_INFINITY) continue;
            if(grid.isBlocked(bx, bz)){ //engel dibindeki top: robot yaklaşamıyorsa değerlendirme dışı
                if(!grid.nearestFree(bx, bz, tmp) || Math.hypot(tmp[0] - bx, tmp[1] - bz) > 0.6f) continue;
            }

            final int density = world.countNear(bx, bz, DENSITY_RADIUS, GROUND_FUEL);
            float score = Math.min(density, space) - d * distWeight;

            //rakip bize göre bu bölgeye daha yakınsa orada top kalmayabilir
            for(int q = 0; q < opps.size(); q++){
                final RobotRebuilt o = opps.get(q);
                if(o.getIntakeCount() >= o.getIntakeCapacity()) continue; //dolu rakip top almaz
                final float od = (float) Math.hypot(o.position.x - bx, o.position.z - bz);
                if(od < d * 0.8f) score -= rivalFear * Math.min(density, space) * 0.7f;
            }
            //takım arkadaşı oraya gidiyorsa başka yer seç
            for(int q = 0; q < mates.size(); q++){
                final BotSkills m = mates.get(q);
                if(!Float.isNaN(m.fuelClaimX) && Math.hypot(m.fuelClaimX - bx, m.fuelClaimZ - bz) < MATE_CLAIM_RADIUS)
                    score -= Math.min(density, space) * 0.8f;
            }

            //yakın zamanda seçilen başka adaya çok yakınsa (aynı küme) tekrarlama: yalnızca daha iyiyse yerini al
            int near = -1;
            for(int q = 0; q < topCount; q++)
                if(Math.hypot(top[q].x - bx, top[q].z - bz) < 1.2f){ near = q; break; }
            if(near >= 0){
                if(score <= top[near].score) continue;
                top[near].x = bx; top[near].z = bz; top[near].count = density; top[near].pathDist = d; top[near].score = score;
                continue;
            }
            if(topCount < K){
                top[topCount++] = fill(new FuelSpot(), bx, bz, density, d, score);
            }
            else{
                int worst = 0;
                for(int q = 1; q < K; q++) if(top[q].score < top[worst].score) worst = q;
                if(score > top[worst].score) fill(top[worst], bx, bz, density, d, score);
            }
        }
        if(topCount == 0) return null;

        final float[] scores = new float[topCount];
        for(int i = 0; i < topCount; i++) scores[i] = top[i].score;
        return top[softmax(scores, temperature)];
    }

    private static FuelSpot fill(FuelSpot s, float x, float z, int count, float d, float score){
        s.x = x; s.z = z; s.count = count; s.pathDist = d; s.score = score;
        return s;
    }


    /** Robot nötr bölgeden kendi alanına top atabilir mi? (canThrowToAllianceArea) */
    public boolean canThrow(){ return robot.canThrowToAllianceArea; }

    /** Kendi hub'ına atış yapılabilen (engele takılmayan, menzil içinde, hataya dayanıklı) noktalar; robota özel, bir kez hesaplanır. */
    public List<ShootSpot> shootSpots(){
        if(shootSpots == null) shootSpots = computeSpots(ShotMode.HUB);
        return shootSpots;
    }

    /** Nötr bölgeden kendi alanına atma yapılabilen noktalar (robot atamıyorsa boş liste); robota özel, bir kez hesaplanır. */
    public List<ShootSpot> throwSpots(){
        if(throwSpots == null) throwSpots = computeSpots(ShotMode.ALLIANCE_THROW);
        return throwSpots;
    }

    private ArrayList<ShootSpot> computeSpots(ShotMode mode){
        final ArrayList<ShootSpot> list = new ArrayList<>();
        if(mode == ShotMode.ALLIANCE_THROW && !robot.canThrowToAllianceArea) return list;
        final NavGrid grid = game.getNavGrid(robot);
        final float W = FRCGameRebuilt.FIELD_WIDTH_METERS, D = FRCGameRebuilt.FIELD_DEPTH_METERS;
        final float hx = ownHubX(), hz = ownHubZ();
        final float margin = Physic3D.Robot.ROBOT_SIZE / 2f + 0.1f;

        //tüm saha taranır; atış tipi (bölge), menzil ve engel kontrolü zaten uygun olmayanları eler
        for(float x = margin; x <= W - margin; x += SHOOT_SPOT_STEP){
            for(float z = margin; z <= D - margin; z += SHOOT_SPOT_STEP){
                if(robot.getShotModeAt(x, z) != mode) continue;
                if(grid.isBlocked(x, z)) continue;
                if(!robot.canShootFrom(x, z)) continue;

                int quality = 0;
                for(int dx = -1; dx <= 1; dx++)
                    for(int dz = -1; dz <= 1; dz++){
                        if(dx == 0 && dz == 0) continue;
                        final float qx = x + dx * QUALITY_OFFSET, qz = z + dz * QUALITY_OFFSET;
                        //komşu da aynı tipte ve atılabilir olmalı (örn. hub önü bandının kenarındaki nokta kalite almaz)
                        if(robot.getShotModeAt(qx, qz) == mode && robot.canShootFrom(qx, qz)) quality++;
                    }
                if(quality < MIN_QUALITY) continue;

                ShootSpot s = new ShootSpot();
                s.x = x; s.z = z; s.quality = quality; s.mode = mode;
                s.targetDist = (mode == ShotMode.HUB) ? (float) Math.hypot(x - hx, z - hz) : Math.abs(robot.throwTargetZ - z);
                list.add(s);
            }
        }
        return list;
    }

    /** Hub atış noktalarını puanlayıp birini seçer: yol uzaklığı, hub'a uzaklık (uzaktan atış daha dağınık), kalite, takım arkadaşı ve
     * rakip yakınlığı. null: hiç atış noktası yok (robot o hub'a atamıyor).
     *
     * @param distWeight yol uzaklığı ağırlığı (1 m yol = kaç puan)
     * @param temperature seçimin rastgeleliği
     */
    public ShootSpot pickShootSpot(float distWeight, float temperature){
        return pickSpot(shootSpots(), distWeight, 0.25f, temperature);
    }

    /** Alana atma noktalarını puanlar ve birini seçer. Puan hub'a atışla aynı mantıkta; fark: atma hedefine ne kadar yakınsa o kadar
     * iyi (kısa atış daha isabetli). null: robot atamıyor (canThrow() false) ya da uygun nokta yok.
     */
    public ShootSpot pickThrowSpot(float distWeight, float temperature){
        return pickSpot(throwSpots(), distWeight, 0.15f, temperature);
    }

    private ShootSpot pickSpot(List<ShootSpot> spots, float distWeight, float targetDistWeight, float temperature){
        if(spots.isEmpty()) return null;
        final NavGrid grid = game.getNavGrid(robot);
        final List<BotSkills> mates = mates();
        final List<RobotRebuilt> opps = opponents();

        grid.distanceFrom(robot.position.x, robot.position.z);

        final int K = 6;
        final ShootSpot[] top = new ShootSpot[K];
        int topCount = 0;
        for(int i = 0; i < spots.size(); i++){
            final ShootSpot s = spots.get(i);
            final float d = grid.distanceTo(s.x, s.z);
            if(d == Float.POSITIVE_INFINITY) continue;

            float score = -d * distWeight - s.targetDist * targetDistWeight + s.quality * 0.15f;
            for(int q = 0; q < mates.size(); q++){
                final BotSkills m = mates.get(q);
                if(!Float.isNaN(m.shootClaimX) && Math.hypot(m.shootClaimX - s.x, m.shootClaimZ - s.z) < 1.1f) score -= 3f;
            }
            for(int q = 0; q < opps.size(); q++){
                final RobotRebuilt o = opps.get(q);
                if(Math.hypot(o.position.x - s.x, o.position.z - s.z) < OPP_NEAR_RADIUS) score -= 2f;
            }

            s.pathDist = d; s.score = score;
            if(topCount < K) top[topCount++] = s;
            else{
                int worst = 0;
                for(int q = 1; q < K; q++) if(top[q].score < top[worst].score) worst = q;
                if(score > top[worst].score) top[worst] = s;
            }
        }
        if(topCount == 0) return null;

        final float[] scores = new float[topCount];
        for(int i = 0; i < topCount; i++) scores[i] = top[i].score;
        return top[softmax(scores, temperature)];
    }


    //---------------------------------------KUYRUĞA EKLENECEKLER---------------------------------------
    private AutoActions actions(){ return robot.getActions(); }

    /** Seçilen top bölgesine gidip orayı toplar (robot dolana / bölge boşalana / timeout'a kadar). */
    public void queueCollect(FuelSpot spot, float radius, float speed, float timeout){
        fuelClaimX = spot.x; fuelClaimZ = spot.z;
        actions().addAutoTask(new BotActions.CollectFuel(spot.x, spot.z, radius, speed, timeout));
    }

    /** Kendi deposunu toplar. */
    public void queueDepot(float speed, float timeout){
        fuelClaimX = ownDepotX(); fuelClaimZ = ownDepotZ();
        actions().addAutoTask(new BotActions.CollectFuel(ownDepotX(), ownDepotZ(), 1.2f, speed, timeout));
    }

    /** Outpost'un altına gidip top dökülmesini bekler (dolana / outpost boşalana / timeout'a kadar). */
    public void queueOutpost(float speed, float waitTimeout){
        fuelClaimX = outpostX(); fuelClaimZ = outpostZ();
        actions().addAutoTask(new AutoActions.ParallelActionsWithMain(
                new FollowPath(outpostX(), outpostZ(), speed, false, 0.15f)));
        actions().addAutoTask(new BotActions.WaitForOutpost(waitTimeout));
    }

    /** Hub atış noktasına (pickShootSpot) gidip hub'a atar. holdFire: hub aktif değilken ateş etme, bekle (maxShootSeconds'a kadar). */
    public void queueScore(ShootSpot spot, float speed, boolean holdFire, float maxShootSeconds){
        shootClaimX = spot.x; shootClaimZ = spot.z;
        actions().addAutoTask(new AutoActions.ParallelActionsWithMain(
                new FollowPath(spot.x, spot.z, speed, false, 0.10f),
                new AutoActions.SetIntakeState(false)));
        actions().addAutoTask(new BotActions.AimAndShoot(holdFire, maxShootSeconds));
    }

    /** Atma noktasına (pickThrowSpot) gidip topları kendi alanına fırlatır (puan yazmaz; hub kapalıyken topu alana taşımak için). */
    public void queueThrow(ShootSpot spot, float speed, float maxShootSeconds){
        shootClaimX = spot.x; shootClaimZ = spot.z;
        actions().addAutoTask(new AutoActions.ParallelActionsWithMain(
                new FollowPath(spot.x, spot.z, speed, false, 0.10f),
                new AutoActions.SetIntakeState(false)));
        actions().addAutoTask(new BotActions.AimAndShoot(false, maxShootSeconds));
    }

    /** Rakibi gölgeler (rakip ile hub'ı arasına girer). */
    public void queueDefend(RobotRebuilt opponent, float duration, float speed){
        actions().addAutoTask(new BotActions.ShadowRobot(opponent, duration, speed));
    }

    /** Yerinde bekler (sn). */
    public void queueWait(float seconds){
        actions().addAutoTask(new AutoActions.WaitTimeout(true, seconds));
    }

    /** Puanlara göre ağırlıklı rastgele indeks (temperature küçükse hep en iyi). */
    int softmax(float[] scores, float temperature){
        final float t = Math.max(temperature, 1e-4f);
        float max = scores[0];
        for(float s : scores) if(s > max) max = s;
        final float[] w = new float[scores.length];
        float sum = 0f;
        for(int i = 0; i < scores.length; i++){
            w[i] = (float) Math.exp((scores[i] - max) / t);
            sum += w[i];
        }
        float r = this.r.nextFloat() * sum;
        for(int i = 0; i < w.length; i++){
            r -= w[i];
            if(r <= 0f) return i;
        }
        return w.length - 1;
    }
}
