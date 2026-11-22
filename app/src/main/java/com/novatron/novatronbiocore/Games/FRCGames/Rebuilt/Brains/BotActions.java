package com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.Brains;

import androidx.annotation.NonNull;

import com.novatron.novatronbiocore.Games.FRCGames.AutoActions.AutoAction;
import com.novatron.novatronbiocore.Games.FRCGames.Physic3D;
import com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.FRCGameRebuilt;
import com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.FRCGameRebuilt.FuelBall;
import com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.FRCGameRebuilt.RobotRebuilt;

import java.util.List;

/** Bot beyinlerinin kullandığı, Rebuilt'e özel aksiyonlar. Genel AutoActions'tan farkı: yol bulur (FollowPath), diğer
 * robotları ve hub'ın aktiflik zamanını bilir. Her biri normal bir AutoAction'dır, yani kuyruğa eklenir ya da
 * ParallelActions/WithTimeout içinde kullanılır. Bitince sonuçlarını getResult() ile verirler. */
public final class BotActions{

    private BotActions(){}

    public enum Result{ RUNNING, FULL, NO_FUEL, TIMEOUT}

    public static class CollectFuel implements AutoAction{
        private static final float BALL_MOVED_REPLAN = 0.4f;  //hedef top bu kadar kaydıysa yeniden git
        private static final float BAN_SECONDS       = 4f;    //ulaşılamayan top bu kadar süre denenmez
        private static final float MAX_GROUND_HEIGHT = 0.25f; //bundan yüksekteki top hedef değil
        //topun, robotun gidebileceği en yakın noktaya uzaklığı bundan büyükse top alınamaz (engel dibi)
        private static final float MAX_REACH_GAP     = 0.60f;
        private static final float TURN_WEIGHT       = 0.35f; //hedef seçerken dönüş açısı (rad) başına eklenen mesafe

        private final float centerX, centerZ, radius, speed, timeout;

        private RobotRebuilt robot;
        private NavGrid grid;
        private FollowPath leg;
        private FuelBall target;
        private float targetX, targetZ;
        private float time;
        private boolean goingToCenter;
        private Result result = Result.RUNNING;

        //yasaklı toplar(robotun ulaşamadığı toplar, pickBall'da tekrar seçmemek için)
        private final FuelBall[] banned = new FuelBall[6];
        private final float[] bannedUntil = new float[6]; //her birinin yasağının biteceği an
        private int bannedNext;
        private final float[] tmp = new float[2];

        /** Bir bölgedeki topları toplar. Intake'i açar, FollowPath'i kullanarak bölgedeki en uygun topa gider,
         * o topu alınca ya da top kaçınca bir sonrakini seçer. Bölgede top kalmazsa, robot dolarsa ya da süre bitince
         * biter. Robot giderken gidilen yönde bakar(intake önde olduğu için).
         *
         * @param centerX toplanacak bölgenin merkezi (m)
         * @param centerZ toplanacak bölgenin merkezi (m)
         * @param radius merkezden bu uzaklıktaki toplar hedef olabilir (m)
         * @param speed azami hız (m/s)
         * @param timeout en fazla bu kadar sürsün (s)
         */
        public CollectFuel(float centerX, float centerZ, float radius, float speed, float timeout){
            this.centerX = centerX; this.centerZ = centerZ;
            this.radius = radius; this.speed = speed; this.timeout = timeout;
        }

        public Result getResult(){ return result; }

        @Override
        public void start(@NonNull Physic3D.Robot r){
            robot = (RobotRebuilt)r;
            grid = robot.game.getNavGrid(robot);
            robot.intakeActive = true;
            result = Result.RUNNING;
            time = 0f;
            leg = null;
            target = null;
            goingToCenter = false;
            java.util.Arrays.fill(banned, null);
        }

        @Override
        public boolean execute(@NonNull Physic3D.Robot r, float dt){
            if(robot.getIntakeCount() >= robot.getIntakeCapacity()){ result = Result.FULL; return true; }
            time += dt;
            if(time > timeout){ result = Result.TIMEOUT; return true; }

            boolean needNew = (leg == null || leg.getStatus() != FollowPath.Status.RUNNING);
            if(!needNew && target != null){
                if(target.getWorld() != robot.getWorld()) needNew = true; //alındı
                else if(Math.hypot(target.position.x - targetX, target.position.z - targetZ) > BALL_MOVED_REPLAN)
                    needNew = true;
            }

            if(needNew){
                //ulaşılamayan topu bir süre yasakla (takıldı / yol yok)
                //ulaşılamayan topu bir süre yasakla (takıldı / yol yok)
                if(leg != null && target != null && leg.getStatus() != FollowPath.Status.RUNNING
                        && leg.getStatus() != FollowPath.Status.ARRIVED && target.getWorld() == robot.getWorld())
                    ban(target);

                if(leg != null){
                    leg.end(robot);
                    leg = null;
                }

                target = pickBall();
                if(target != null){
                    goingToCenter = false;
                    targetX = target.position.x;
                    targetZ = target.position.z;
                    leg = new FollowPath(targetX, targetZ, speed, true, 0.15f);
                    leg.start(robot);
                }
                else if(!goingToCenter && Math.hypot(robot.position.x - centerX, robot.position.z - centerZ) > 1.2f){
                    //bölgede top görünmüyor ama henüz bölgeye varmadık, merkeze git
                    goingToCenter = true;
                    leg = new FollowPath(centerX, centerZ, speed, true, 0.3f);
                    leg.start(robot);
                }
                else{ result = Result.NO_FUEL; return true; }
            }

            if(leg != null) leg.execute(robot, dt);
            return false;
        }

        @Override
        public void end(@NonNull Physic3D.Robot r){
            if(leg != null) leg.end(robot);
            leg = null;
            r.intakeActive = false;
        }

        private void ban(FuelBall b){
            banned[bannedNext] = b;
            bannedUntil[bannedNext] = time + BAN_SECONDS;
            bannedNext = (bannedNext + 1) % banned.length;
        }

        private boolean isBanned(FuelBall b){
            for(int i = 0; i < banned.length; i++)
                if(banned[i] == b && bannedUntil[i] > time) return true;
            return false;
        }

        /** Bölgedeki, ulaşılabilir, robota (mesafe + dönüş açısına göre) en uygun top; yoksa null.*/
        private FuelBall pickBall(){
            final Physic3D world = robot.getWorld();
            if(world == null) return null;

            final float rx = robot.position.x, rz = robot.position.z;
            FuelBall best = null;
            float bestScore = Float.MAX_VALUE;

            for(int i = 0; i < world.entities.size(); i++){
                final Physic3D.PhysicsEntity e = world.entities.get(i);
                if(!(e instanceof FuelBall)) continue;
                if(e.position.y > MAX_GROUND_HEIGHT) continue;
                if(Math.hypot(e.position.x - centerX, e.position.z - centerZ) > radius) continue;
                if(isBanned((FuelBall) e)) continue;

                final float dx = e.position.x - rx, dz = e.position.z - rz;
                float score = (float)Math.hypot(dx, dz);
                if(score > 0.05f){
                    float turn = Math.abs(angleDiff((float)Math.atan2(dz, dx), robot.getHeading()));
                    score += TURN_WEIGHT * turn;
                }
                if(score >= bestScore) continue;

                //engel dibindeki top (robot o kadar yaklaşamaz), atla
                if(grid.isBlocked(e.position.x, e.position.z)){
                    if(!grid.nearestFree(e.position.x, e.position.z, tmp)) continue;
                    if(Math.hypot(tmp[0] - e.position.x, tmp[1] - e.position.z) > MAX_REACH_GAP) continue;
                }
                bestScore = score;
                best = (FuelBall) e;
            }
            return best;
        }
    }

    public static class WaitForOutpost implements AutoAction{
        private final float timeout;
        private float time;
        private RobotRebuilt robot;

        /** Outpost'un altında durup top dökülmesini bekler. Robot dolunca, outpost
         * boşalınca ya da süre bitince biter. OUTPOST'UN ALTINA GÖTÜRMEZ, oraya sen
         * getireceksin! */
        public WaitForOutpost(float timeout){ this.timeout = timeout; }

        @Override
        public void start(@NonNull Physic3D.Robot r){ robot = (RobotRebuilt)r; time = 0f; }

        @Override
        public boolean execute(@NonNull Physic3D.Robot r, float dt){
            time += dt;
            if(time > timeout) return true;
            if(robot.getIntakeCount() >= robot.getIntakeCapacity()) return true;
            return (robot.isBlueAlliance) ? robot.game.isBlueOutpostEmpty() : robot.game.isRedOutpostEmpty();
        }

        @Override
        public void end(@NonNull Physic3D.Robot r){}
    }

    public static class ShadowRobot implements AutoAction{
        private static final float RETARGET_INTERVAL = 0.35f; //konumu yeniden hesaplama sıklığı
        private static final float MOVE_THRESHOLD = 0.5f; //savunma noktası bu kadar kaydıysa yeniden yol planla
        private static final float STAND_OFF = 1.0f; //rakip hub'a doğru bu kadar önünde dur

        private final RobotRebuilt opponent;
        private final float duration, speed;

        private RobotRebuilt robot;
        private FollowPath leg;
        private float time, timer, goalX, goalZ;

        /** Bir rakip robotu gölgeler: rakip ile kendi hub'ı arasına girer (rakibin atış noktasına/hub'a giden yolunu keser) ve rakip
         * hareket ettikçe yeniden konumlanır. Süre dolunca biter. Robota değerse fizik zaten itişir. */
        public ShadowRobot(RobotRebuilt opponent, float duration, float speed){
            this.opponent = opponent; this.duration = duration; this.speed = speed;
        }

        @Override
        public void start(@NonNull Physic3D.Robot r){
            robot = (RobotRebuilt)r;
            time = 0f; timer = 0f;
            leg = null;
            robot.intakeActive = false;
        }

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){
            time += dt;
            if(time > duration) return true;

            timer -= dt;
            if(timer <= 0f || leg == null || leg.getStatus() != FollowPath.Status.RUNNING){
                if(timer <= 0f) time = RETARGET_INTERVAL;

                //rakipin gideceği yer: kendi hub'ı
                final float hx = FRCGameRebuilt.getHubCenterX();
                final float hz = FRCGameRebuilt.getHubCenterZ(opponent.isBlueAlliance);
                final float ox = opponent.position.x, oz = opponent.position.z;
                float dx = hx - ox, dz = hz - oz;
                final float len = (float)Math.hypot(dx, dz);
                float gx, gz;
                if(len < STAND_OFF + 0.5f){
                    gx = ox; gz = oz; //zaten hub'ın dibinde, üstüne git
                }
                else{
                    gx = ox + dx / len * STAND_OFF;
                    gz = oz + dz / len * STAND_OFF;
                }

                if(leg == null || Math.hypot(gx - goalX, gz - goalZ) > MOVE_THRESHOLD || leg.getStatus() != FollowPath.Status.RUNNING){
                    if(leg != null) leg.end(robot);
                    goalX = gx; goalZ = gz;
                    leg = new FollowPath(gx, gz, speed, false, 0.25f);
                    leg.start(robot);
                }
            }
            leg.execute(robot, dt);
            if(leg.getStatus() != FollowPath.Status.RUNNING) robot.setInputXZ(0f, 0f);
            return false;
        }

        @Override
        public void end(@NonNull Physic3D.Robot r){
            if(leg != null) leg.end(robot);
            leg = null;
            r.setInputXZ(0f, 0f);
        }
    }

    /** a - b, (-pi, pi] aralığında. */
    static float angleDiff(float a, float b){
        float d = a - b;
        while(d > Math.PI) d -= 2f * (float) Math.PI;
        while(d <= -Math.PI) d += 2f * (float) Math.PI;
        return d;
    }

    /** Kendi ittifakı dışındaki robotlar. */
    static void opponentsOf(RobotRebuilt me, List<RobotRebuilt> out){
        out.clear();
        for(RobotRebuilt r : me.game.getRobots()) if(r.isBlueAlliance != me.isBlueAlliance) out.add(r);
    }
}
