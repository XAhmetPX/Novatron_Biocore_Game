package com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.Brains;

import androidx.annotation.NonNull;

import com.novatron.novatronbiocore.Games.FRCGames.AutoActions;
import com.novatron.novatronbiocore.Games.FRCGames.Physic3D;
import com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.FRCGameRebuilt.RobotRebuilt;

import java.util.List;

/**
 * Robotu (x, z) hedefine, sahadaki engellerin ve diğer robotların etrafından dolaştırarak götürür.
 * Yolu NavGrid (A*) bulur; bu aksiyon yolu takip eder, hedefe frenleyerek oturur, takılırsa ya da biri yolu tıkarsa yeniden planlar.
 * <br>
 * Robot hendeğin altından geçebiliyor mu (yükseklik), hangi engel nereden dolanılır gibi her şeyi harita halleder:
 * aksiyonu doğrudan hedefle çağırmak yeter. Biten aksiyonun sonucu getStatus() ile okunur (hedefe mi vardı, takıldı mı?).
 * Robot yönüne karışmaz (faceTravel = false iken), toplama/atış aksiyonlarıyla ParallelActions içinde birlikte çalışabilir.
 * <br>
 * Not: robot kare olduğu için yolun en dar yeri (hendek koridoru) ancak robot eksenlere hizalıyken (0/90/180/270°) güvenle geçilir;
 * köşegen duruşta (45°) köşeler engele sürtebilir. Takılırsa yeniden planlama yardımcı olur ama hizalamak beynin işidir.
 */
public class FollowPath implements AutoActions.AutoAction{

    public enum Status{
        RUNNING,
        ARRIVED, //hedefe (ya da ona en yakın erişilebilir noktaya) varıldı
        NO_PATH, //yol yok
        STUCK //defalarca yeniden planlamaya rağmen ilerleyemedi
    }

    private static final float REACH_SHARP  = 0.20f;  //keskin dönüşlü ara noktaya bu kadar yaklaşınca sıradakine geç (m)
    private static final float REACH_SOFT   = 0.45f;  //yumuşak dönüşlü ara nokta için
    private static final float SOFT_TURN    = 0.45f;  //bu açıdan (rad) küçük dönüş "yumuşak"
    private static final float BRAKE_FACTOR = 0.8f;   //hedefe frenleme ivmesinin kullanılan oranı
    private static final float CORNER_BRAKE = 0.5f;   //köşeye frenleme ivmesinin kullanılan oranı
    private static final float OFF_COURSE   = 1.2f;   //yoldan bu kadar uzaklaşırsa (itilirse) yeniden planla (m)

    private static final float CHECK_INTERVAL   = 0.4f;  //yolun önünü kontrol sıklığı (s)
    private static final float LOOKAHEAD_DIST   = 2.5f;  //yolun önündeki bu kadar kısmı tıkanık mı diye bak (m)
    private static final float BLOCK_DIST       = Physic3D.Robot.ROBOT_SIZE + 0.15f; //yolun bu kadar yakınındaki robot "yolda"
    private static final float REPLAN_COOLDOWN  = 1.0f;  //iki tıkanıklık yeniden planlaması arası (s)
    private static final float AVOID_COST       = 0.35f; //diğer robotların etrafı için kare başı ceza (m)
    private static final float AVOID_COST_STUCK = 0.9f;
    private static final float AVOID_RADIUS     = Physic3D.Robot.ROBOT_SIZE + 0.15f; //iki kare robotun merkezleri en az ROBOT_SIZE uzakta olmalı
    private static final float AVOID_CORE_RADIUS = Physic3D.Robot.ROBOT_SIZE * 0.9f;
    private static final float AVOID_RANGE      = 5f;    //bundan uzak robotları cezaya katma (m)

    private static final float STUCK_WINDOW     = 0.25f; //ilerleme ölçüm penceresi (s)
    private static final float STUCK_TIME       = 0.8f;  //bu kadar süre ilerlemezse "takıldı"
    private static final int   MAX_REPLANS      = 4;     //ilerleme olmadan en fazla bu kadar yeniden planla
    private static final float NEAR_GOAL_ACCEPT = 0.6f; //hedefe bu kadar yakınken takılırsa "vardı" sayılır (m)
    private static final float PROGRESS_RESET   = 1.0f;  //yeniden planladıktan sonra bu kadar yol alırsa sayaç sıfırlanır (m)

    private final float goalX, goalZ;
    private final float speed;
    private final boolean faceTravel;
    private final float arriveTolerance;

    private Status status = Status.RUNNING;
    private RobotRebuilt robot;
    private NavGrid grid;
    private final NavGrid.Route route = new NavGrid.Route();
    private int next; //şu an yaklaşılan ara nokta (route'un indeksi)

    private float checkTimer, cooldown;
    private float windowTime, windowCmd, stuckTime;
    private float windowX, windowZ;
    private int replans, totalReplans;
    private float replanX, replanZ;

    /**
     * @param goalX : hedefin x'i
     * @param goalZ : hedefin z'si. Hedef yasak bölgedeyse (duvar dibi, engel için) ona en yakın
     *              erişilebilir noktaya gidilir
     * @param speed : azami hız (Robot.MAX_SPEED ile sınırlı)
     * @param faceTravel true : gidilen yöne bakılır
     */
    public FollowPath(float goalX, float goalZ, float speed, boolean faceTravel){
        this(goalX, goalZ, speed, faceTravel, 0.10f);
    }

    /** @param arriveTolerance hedefe bu kadar yaklaşınca (ve yavaşlamışsa) vardı say (m) */
    public FollowPath(float goalX, float goalZ, float speed, boolean faceTravel, float arriveTolerance){
        if(!(speed > 0f)) throw new IllegalArgumentException("Speed 0'dan büyük olmalı: "+speed);
        if(!(arriveTolerance > 0f)) throw new IllegalArgumentException("arriveTolerance 0'dan büyük olmalı: "+arriveTolerance);
        this.goalX = goalX;
        this.goalZ = goalZ;
        this.speed = Math.min(speed, Physic3D.Robot.MAX_SPEED);
        this.faceTravel = faceTravel;
        this.arriveTolerance = arriveTolerance;
    }

    public Status getStatus(){ return status; }

    /**Bu aksiyonun yaptığı yeniden planlama sayısı (ilk plan hariç). */
    public int getReplanCount(){ return totalReplans; }

    /** Hedefe kalan yol uzunluğu; yol yoksa 0. */
    public float getRemainingDistance(){
        if(robot == null || route.size == 0) return 0f;
        float d = (float)Math.hypot(route.x[Math.min(next, route.size - 1)] - robot.position.x,
                route.z[Math.min(next, route.size - 1)] - robot.position.z);
        for(int i = next; i < route.size - 1; i++)
            d += (float)Math.hypot(route.x[i + 1] - route.x[i], route.z[i + 1] - route.z[i]);
        return d;
    }

    @Override
    public void start(@NonNull Physic3D.Robot r){
        robot = (RobotRebuilt) r;
        grid = robot.game.getNavGrid(robot);
        status = Status.RUNNING;
        checkTimer = 0f;
        cooldown = 0f;
        windowTime = windowCmd = stuckTime = 0f;
        windowX = robot.position.x;
        windowZ = robot.position.z;
        replans = 0;
        replanX = windowX; replanZ = windowZ;
        if(!plan(0f)) status = Status.NO_PATH;
    }

    /** Yolu (şu anki konumdan) yeniden kurar. avoidCost > 0: diğer robotların etrafına ceza koyar. */
    private boolean plan(float avoidCost){
        if(avoidCost > 0f){
            final List<RobotRebuilt> robots = robot.game.getRobots();
            for(int i = 0; i < robots.size(); i++){
                final RobotRebuilt o = robots.get(i);
                if(o == robot) continue;
                if(Math.hypot(o.position.x - robot.position.x, o.position.z - robot.position.z) > AVOID_RANGE) continue;
                grid.addPenaltyDisc(o.position.x, o.position.z, AVOID_RADIUS, avoidCost);
                grid.addPenaltyDisc(o.position.x, o.position.z, AVOID_CORE_RADIUS, avoidCost * 3f); //tam üstünden geçmek en pahalısı
            }
        }
        final boolean ok = grid.findPath(robot.position.x, robot.position.z, goalX, goalZ, route);
        grid.clearDynamic();
        next = Math.min(1, route.size - 1);
        return ok && route.size > 0;
    }

    @Override
    public boolean execute(@NonNull Physic3D.Robot r, float dt){
        if(status != Status.RUNNING) return true;

        final float px = robot.position.x, pz = robot.position.z;
        final int last = route.size - 1;

        //ara noktaları ilerlet
        while(next < last){
            final float d = (float) Math.hypot(route.x[next] - px, route.z[next] - pz);
            final float reach = (turnAngle(next) < SOFT_TURN) ? REACH_SOFT : REACH_SHARP;
            if(d < reach){ next++; continue; }

            //köşeyi kesip noktanın ötesine geçtiyse (yani gelen yönde noktayı aştıysa) de ilerle
            final float sx = route.x[next] - route.x[next - 1], sz = route.z[next] - route.z[next - 1];
            if((px - route.x[next]) * sx + (pz - route.z[next]) * sz > 0f && turnAngle(next) < 1.2f){ next++; continue; }
            break;
        }

        final float tx = route.x[next], tz = route.z[next];
        final float dx = tx - px, dz = tz - pz;
        final float dist = (float) Math.hypot(dx, dz);

        //vardık mı?
        if(next == last && dist < arriveTolerance && robot.velocity.len() < 0.8f){
            status = Status.ARRIVED;
            return true;
        }

        //yeniden planlama tetikleyicileri
        cooldown = Math.max(0f, cooldown - dt);
        checkTimer -= dt;
        if(checkTimer <= 0f){
            checkTimer = CHECK_INTERVAL;
            if(distanceToRoute(px, pz) > OFF_COURSE){ //itilmiş/sürüklenmiş
                return !replan(AVOID_COST);
            }
            if(cooldown <= 0f && isRouteBlockedByRobot(px, pz)){
                cooldown = REPLAN_COOLDOWN;
                return !replan(AVOID_COST);
            }
        }

        //hız profili
        final float acc = Physic3D.Robot.ACCELERATION;
        float vmax = speed;
        final float remaining = getRemainingDistance();
        vmax = Math.min(vmax, (float)Math.sqrt(2f * acc * BRAKE_FACTOR * remaining));

        float along = dist; //robottan k. noktaya yol boyunca mesafe
        for(int k = next; k < last && k < next + 3; k++){
            final float vCorner = speed * Math.max(0.25f, Math.min(1f, 1f - turnAngle(k) / (0.6f * (float) Math.PI)));
            vmax = Math.min(vmax, (float) Math.sqrt(vCorner * vCorner + 2f * acc * CORNER_BRAKE * along));
            along += (float) Math.hypot(route.x[k + 1] - route.x[k], route.z[k + 1] - route.z[k]);
        }

        //takılma tespiti
        windowTime += dt;
        windowCmd = vmax * dt;
        if(windowTime >= STUCK_WINDOW){
            final float moved = (float)Math.hypot(px - windowX, pz - windowZ);
            if(windowCmd / windowTime > 0.8f && moved < 0.25f * windowCmd) stuckTime += windowTime;
            else stuckTime = Math.max(0f, stuckTime - windowTime);
            windowTime = 0f; windowCmd = 0f; windowX = px; windowZ = pz;

            if(stuckTime >= STUCK_TIME){
                stuckTime = 0f;
                //hedefin hemen dibinde takıldıysa (top yığını duvara sıkışmış, hedef başka robotun altında...) vardı say
                if(remaining < NEAR_GOAL_ACCEPT && next >= last - 1){
                    status = Status.ARRIVED;
                    return true;
                }
                if(Math.hypot(px - replanX, pz - replanZ) > PROGRESS_RESET) replans = 0;
                replanX = px; replanZ = pz;
                if(++replans > MAX_REPLANS){
                    status = Status.STUCK;
                    return true;
                }
                return !replan(AVOID_COST_STUCK);
            }
        }

        //sür
        final float mag = vmax / Physic3D.Robot.MAX_SPEED;
        if(dist > 0.0001f) robot.setInputXZ(dx / dist*mag, dz / dist*mag);
        else robot.setInputXZ(0f, 0f);

        if(faceTravel)
            robot.setTargetHeading((dist > 0.3f) ? (float)Math.atan2(dz, dx) : Physic3D.Robot.NO_TURN);

        return false;
    }

    @Override
    public void end(@NonNull Physic3D.Robot r){
        r.setInputXZ(0f, 0f);
        if(faceTravel) r.setTargetHeading(Physic3D.Robot.NO_TURN);
    }

    /** Yeniden planlar; yol bulunamazsa durumu NO_PATH yapar ve false döner. */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean replan(float avoidCost){
        totalReplans++;
        if(plan(avoidCost)) return true;
        status = Status.NO_PATH;
        return false;
    }

    /** route[k] noktasındaki dönüş açısı (rad, 0 = düz devam). Son nokta ve ilk nokta için 0. */
    private float turnAngle(int k){
        if(k <= 0 || k >= route.size - 1) return 0f;
        final float ax = route.x[k] - route.x[k - 1], az = route.z[k] - route.z[k - 1];
        final float bx = route.x[k + 1] - route.x[k], bz = route.z[k + 1] - route.z[k];
        final float la = (float) Math.hypot(ax, az), lb = (float) Math.hypot(bx, bz);
        if(la < 1e-4f || lb < 1e-4f) return 0f;
        float c = (ax * bx + az * bz) / (la * lb);
        return (float) Math.acos(Math.max(-1f, Math.min(1f, c)));
    }

    /** (px, pz)'nin, yaklaşılan segmente (route[next-1] -> route[next]) uzaklığı. */
    private float distanceToRoute(float px, float pz){
        if(route.size < 2) return 0f;
        final int a = Math.max(0, next - 1), b = Math.max(a, Math.min(next, route.size - 1));
        return pointToSegment(px, pz, route.x[a], route.z[a], route.x[b], route.z[b]);
    }

    /** Robotun önündeki LOOKAHEAD_DIST uzunluğundaki yola başka bir robot BLOCK_DIST'ten fazla yaklaşmış mı? */
    private boolean isRouteBlockedByRobot(float px, float pz){
        final List<RobotRebuilt> robots = robot.game.getRobots();
        for(int i = 0; i < robots.size(); i++){
            final RobotRebuilt o = robots.get(i);
            if(o == robot) continue;
            final float ox = o.position.x, oz = o.position.z;
            if(Math.hypot(ox - px, oz - pz) > LOOKAHEAD_DIST + BLOCK_DIST) continue;

            //robottan başlayıp yol boyunca LOOKAHEAD_DIST ilerle
            float ax = px, az = pz, left = LOOKAHEAD_DIST;
            for(int k = next; k < route.size && left > 0f; k++){
                float bx = route.x[k], bz = route.z[k];
                float seg = (float) Math.hypot(bx - ax, bz - az);
                if(seg > left){ //segmentin yalnızca ilk 'left' kısmı
                    bx = ax + (bx - ax) / seg * left;
                    bz = az + (bz - az) / seg * left;
                    seg = left;
                }
                if(pointToSegment(ox, oz, ax, az, bx, bz) < BLOCK_DIST) return true;
                left -= seg;
                ax = bx; az = bz;
            }
        }
        return false;
    }

    private static float pointToSegment(float px, float pz, float ax, float az, float bx, float bz){
        final float abx = bx - ax, abz = bz - az;
        final float len2 = abx * abx + abz * abz;
        float t = (len2 < 1e-8f) ? 0f : ((px - ax) * abx + (pz - az) * abz) / len2;
        t = Math.max(0f, Math.min(1f, t));
        return (float) Math.hypot(px - (ax + abx * t), pz - (az + abz * t));
    }
}
