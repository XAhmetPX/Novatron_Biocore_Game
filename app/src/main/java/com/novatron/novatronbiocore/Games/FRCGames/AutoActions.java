package com.novatron.novatronbiocore.Games.FRCGames;

import androidx.annotation.NonNull;

import com.badlogic.gdx.math.Vector3;

import java.util.ArrayDeque;
import java.util.Objects;

public class AutoActions{

    private final ArrayDeque<AutoAction> queue = new ArrayDeque<>();
    private AutoAction current = null;

    public boolean execute(@NonNull Physic3D.Robot robot, float dt){
        if(current == null){
            current = queue.pollFirst();
            if(current == null) return true;
            current.start(robot);
        }

        //current, execute() içinde clear/interrupt ile değiştirilmiş olabilir (o durumda end() zaten çağırıldı)
        final AutoAction action = current;
        if(action != null && action.execute(robot, dt) && current == action){
            current = null;
            action.end(robot);
        }

        return isEmpty();
    }

    /** Çalışan aksiyonun end()'ini çağırır ve bekleyen tüm aksiyonları siler (hiçbirinin start()'ı çağrılmaz).
     * Otonom süresi bitince (Robot.stopAutonomous), reset'te ve interrupt()'ta kullanılır.
     * Boş kuyrukta çağırmak zararsızdır. */
    public void clear(@NonNull Physic3D.Robot robot){
        queue.clear(); //önce tamizle: end() içinde eklenen bir şey varsa o kalsın
        final AutoAction action = current;
        current = null;
        if(action != null) action.end(robot);
    }

    /** Mevcut işi bırakıp (end() çağrılır) bekleyenleri siler ve verilen aksiyonu tek iş olarak başlatır. */
    public void interrupt(@NonNull Physic3D.Robot robot, @NonNull AutoAction autoAction){
        Objects.requireNonNull(autoAction, "autoAction null olamaz");
        clear(robot);
        queue.addLast(autoAction);
    }

    /** Aksiyonu kuyruğun sonuna ekler */
    public void addAutoTask(@NonNull AutoAction autoAction){
        queue.addLast(Objects.requireNonNull(autoAction, "autoAction null olamaz"));
    }

    /** true: çalışan da bekleyen de aksiyon yok (bot beyni için "yeni iş ver" işareti). */
    public boolean isEmpty(){ return current == null && queue.isEmpty(); }

    /** Çalışan + bekleyen aksiyon sayısı. */
    public int size(){ return queue.size() + ((current == null) ? 0 : 1); }

    /**
     * Robotun otonom aksiyonlarını kuran fonksiyon. Robot sıfırlanırken (her oyun başında) kuyruk temizlenir ve bu
     * çağrılır; içinde robot.getActions().addAutoTask(new ...) ile aksiyonlar baştan oluşturulur.
     * Her seferinde yeni aksiyon oluşturulduğu için eski çalıştırmadan kalan durum olmaz.
     * (Metot referansıyla kullanılabilir: robot.setAutoScript(AutoActionSetter::setRobot1Autonomus))
     */
    public interface Script{
        void build(@NonNull Physic3D.Robot robot);
    }

    //robot yönü için "hedefe ulaşıldı" toleransı (radyan, ~0.23°). TurnToAngle ve TurnToPoint ortak kullanır.
    private static final float HEADING_TOLERANCE = 0.004f;

    public interface AutoAction{

        /** Execute ilk defa çağırılmadan önce çağırılır. Hazırlık için */
        void start(@NonNull Physic3D.Robot robot);

        /** Aksiyon çalışırken her karede çağırılır.
         * @return false: aksiyon devam ediyor, true: aksiyor bitti. */
        boolean execute(@NonNull Physic3D.Robot robot, float dt);

        /** Aksiyon zorla ya da normal olarak bittiğinde çağırılır. */
        void end(@NonNull Physic3D.Robot robot);
    }

    /** Lamda ile bir koşul yazıp bunu AutoAction'lara koşul olarak atamak
     * amacıyla kullanılır. */
    public interface Condition{
        /** @return true: koşul sağlandı, false: koşul henüz sağlanmadı. */
        //dt belki sayaç olarak kullanılır diye
        boolean isConditionMet(@NonNull Physic3D.Robot robot, float dt);
    }

    public static class DriveBezierCurve implements AutoAction{
        private final float[] points;
        private final float duration;
        private float timeElapsed = 0f;
        private final boolean rotation;

        //temp calculate arrays
        private final float[] tempX;
        private final float[] tempZ;
        private float carrotX, carrotZ; //eğri üzerindeki anlık hedef nokta ("havuç")

        /** Verilen kordinatlardan geçerek kıvrımlı (ya da istersen düz de olur)
         * şekilleri ile son kordinat çiftine gider.
         *
         * @param duration Aksiyon kaç saniye sürecek
         * @param points X ve Z kordinat çiftleri (x0, z0, x1, z1, ...). Yer düzlemi: x = sahanın kısa kenarı, z = uzun kenarı
         * @param rotation true: aksiyon esnasında gidilen yöne bakılacak
         */
        public DriveBezierCurve(float duration, boolean rotation, float... points){
            if(points.length < 4 || points.length % 2 != 0)
                throw new IllegalArgumentException("A Bézier curve requires at least 2 points (4 floats),and the count must be an even number!");

            if(!(duration > 0f)) //0, negatif ve NaN: t = timeElapsed / duration sonsuz/NaN olurdu
                throw new IllegalArgumentException("duration 0'dan büyük olmalı: " + duration);

            this.duration = duration;
            //start() ilk noktayı robotun konumuyla değiştirdiği için kopya al; yoksa çağıranın dizisi bozulur
            this.points = points.clone();
            this.rotation = rotation;

            int numPoints = points.length / 2;
            this.tempX = new float[numPoints];
            this.tempZ = new float[numPoints];
        }

        @Override
        public void start(@NonNull Physic3D.Robot robot){
            timeElapsed = 0f;
            //ilk pozisyonu robotunki yap ki daha temiz gitsin
            this.points[0] = robot.position.x;
            this.points[1] = robot.position.z;
        }

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){
            timeElapsed += dt;
            float t = timeElapsed / duration;

            //final point
            float finalX = points[points.length - 2];
            float finalZ = points[points.length - 1];

            if(t >= 1.0f){
                float dx = finalX - robot.position.x;
                float dz = finalZ - robot.position.z;
                if(dx * dx + dz * dz < 0.01f)
                    return true; //the route is done

                t = 1.0f; //keep the carrot at the finish line.
            }

            //acceleration/deceleration smoothing with S-Curve (motion profiling)
            float smoothT = (3.0f * t * t) - (2.0f * t * t * t);

            //Calculating Instantaneous Position for N Points Using the De Casteljau Algorithm
            calculateBezierPoint(smoothT);

            //following the carrot with P-Controller
            float dx = carrotX - robot.position.x;
            float dz = carrotZ - robot.position.z;
            float distance = (float) Math.sqrt(dx * dx + dz * dz);

            //increase the input based on distance (P-Controller), but limit it to a maximum of 1.0
            float inputMag = Math.min(1.0f, distance * 2.5f);
            float inputX = 0f;
            float inputZ = 0f;
            if(distance > 0.01f){
                inputX = (dx / distance) * inputMag;
                inputZ = (dz / distance) * inputMag;
            }

            if(rotation){
                //face the direction of travel; very close to the carrot there is no turn command (prevents shaking)
                float targetHeading = Physic3D.Robot.NO_TURN;
                if(distance > 0.1f)
                    targetHeading = (float) Math.atan2(dz, dx);
                robot.setTargetHeading(targetHeading);
            }

            robot.setInputXZ(inputX, inputZ);

            return false;
        }

        @Override
        public void end(@NonNull Physic3D.Robot robot){
            robot.setInputXZ(0, 0);
            if(rotation) robot.setTargetHeading(Physic3D.Robot.NO_TURN);
        }

        /** Any-Degree Bézier Calculator (De Casteljau) */
        private void calculateBezierPoint(float t){
            int numPoints = points.length / 2;

            for(int i = 0; i < numPoints; i++){
                tempX[i] = points[i * 2];
                tempZ[i] = points[i * 2 + 1];
            }

            for(int r = 1; r < numPoints; r++){
                for(int i = 0; i < numPoints - r; i++){
                    tempX[i] = (1.0f - t) * tempX[i] + t * tempX[i + 1];
                    tempZ[i] = (1.0f - t) * tempZ[i] + t * tempZ[i + 1];
                }
            }

            carrotX = tempX[0];
            carrotZ = tempZ[0];
        }
    }

    public static class WaitUntil implements AutoAction{

        private final Condition condition;
        private final boolean setInputs0;

        /** Verilen koşul karşılanana kadar bekler, tüm süre boyunca robotun
         * girdilerini 0/Float.NaN girer
         *
         * @param condition aksiyonun bitmesi için karşılanması gereken koşul.
         * @param setInputs0 true: robotun girdileri 0/Float.NaN girilir,
         *                   false: robotun girdilerine karışılmaz
         */
        public WaitUntil(boolean setInputs0, Condition condition){
            this.condition = condition;
            this.setInputs0 = setInputs0;
        }

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){
            if(setInputs0)
                robot.setInputs(0, 0, Physic3D.Robot.NO_TURN);
            return condition == null || condition.isConditionMet(robot, dt);
        }

        @Override public void end(@NonNull Physic3D.Robot robot){
            if(setInputs0)
                robot.setInputs(0, 0, Physic3D.Robot.NO_TURN);
        }
        @Override public void start(@NonNull Physic3D.Robot robot){
            if(setInputs0)
                robot.setInputs(0, 0, Physic3D.Robot.NO_TURN);
        }
    }

    public static class WithCondition implements AutoAction{
        private final Condition condition;
        private final AutoAction action;

        /** Verilen aksiyonu verilen koşu ile sarmalar, eğer koşu karşılanırsa
         * aksiyon bitmediyse aksiyonu yarıda keser, eğer aksiyon önce biterse
         * koşulun karşılanmasını beklemez.
         *
         * @param condition karşılanması gereken koşul
         * @param action sarmalanan aksiyon
         */
        public WithCondition(Condition condition, AutoAction action){
            this.condition = condition;
            this.action = action;
        }

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){
            if(condition == null) return true;

            //aksiyon bitti
            if(action != null && action.execute(robot, dt)) return true;

            return condition.isConditionMet(robot, dt);
        }

        @Override
        public void start(@NonNull Physic3D.Robot robot){ if(action != null) action.start(robot); }

        @Override
        public void end(@NonNull Physic3D.Robot robot){ if(action != null) action.end(robot); }
    }

    public static class WaitTimeout implements AutoAction{

        private final float timeout;
        private final boolean setInputs0;
        private float totalTime;

        public WaitTimeout(boolean setInputs0, float timeout){
            this.timeout = timeout;
            this.setInputs0 = setInputs0;
        }

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){
            totalTime += dt;
            if(setInputs0)
                robot.setInputs(0, 0, Physic3D.Robot.NO_TURN);

            return Float.isNaN(timeout) || Float.isInfinite(timeout) || totalTime >= timeout;
        }

        @Override public void end(@NonNull Physic3D.Robot robot){
            if(setInputs0)
                robot.setInputs(0, 0, Physic3D.Robot.NO_TURN);
        }
        @Override public void start(@NonNull Physic3D.Robot robot){
            totalTime = 0f;
            if(setInputs0)
                robot.setInputs(0, 0, Physic3D.Robot.NO_TURN);
        }
    }

    public static class SetIntakeState implements AutoAction{
        private final boolean intakeOn;

        /** execute çağırıldığında robotun intake durumunu güncelleyip aksiyonu bitirir.
         *  @param intakeOn execute'de robot.intakeActive'e atanacak durum. */
        public SetIntakeState(boolean intakeOn){ this.intakeOn = intakeOn; }

        @Override
        public void start(@NonNull Physic3D.Robot robot){}

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){
            robot.intakeActive = intakeOn;
            return true;
        }

        @Override
        public void end(@NonNull Physic3D.Robot robot){}
    }

    public static class IntakeUntilFull implements AutoAction{
        private final int count;
        private int goal; //start()'ta hesaplanan, ulaşılması gereken toplam top sayısı

        /** Robotun intake'ini açar, hedef sayıda yeni top toplanınca
         * kapar ve aksiyonu bitirir. Robotta zaten top varsa onlar sayılmaz;
         * boş yer kalmadıysa (kapasite) dolunca biter, yani sonsuza kadar beklemez.
         * @param count alınması hedeflenen yeni top sayısı, -1 = intake dolana kadar
         */
        public IntakeUntilFull(int count){
            if(count < -1) throw new IllegalArgumentException("count -1 (dolana kadar) veya >= 0 olmalı: " + count);
            this.count = count;
        }

        /** Robotun intaki'in açar, intake dolana kadar yeni top toplayınca
         * kapar ve aksiyonu bitirir.
         */
        public IntakeUntilFull(){
            this.count = -1;
        }

        @Override
        public void start(@NonNull Physic3D.Robot robot){
            robot.intakeActive = true;
            goal = (count == -1)
                    ? robot.getIntakeCapacity()
                    : Math.min(robot.getIntakeCount() + count, robot.getIntakeCapacity());
        }

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){ return robot.getIntakeCount() >= goal; }

        @Override
        public void end(@NonNull Physic3D.Robot robot){ robot.intakeActive = false; }
    }

    public static class DriveToPosition implements AutoAction{
        private static final float ARRIVE_TOLERANCE = 0.03f; //hedefe bu kadar yaklaşınca (m) ...
        private static final float ARRIVE_MAX_SPEED = 0.6f;  //... ve bu hızdan yavaşsa (m/s) "vardı" say
        private static final float BRAKE_FACTOR     = 0.8f;  //frenleme ivmesinin ne kadarı kullanılsın (1'e yakın = agresif)

        private Vector3 targetPos; //yalnızca x ve z okunur (yer düzleminde gider)
        private float speed;
        private boolean rotation;

        /**
         * Verilen pozisyona gider. Hedef konum "canlı" okunur: verdiğin Vector3 sonradan değişirse (örn. bir topun
         * position'ı) robot onu takip eder. Sabit bir hedef için aşağıdaki (x, z) kurucusunu kullan.
         * Yalnızca hedefin x ve z bileşeni kullanılır.
         * <br>
         * Robot en fazla verilen hızda gider, hedefe yaklaşırken robotun frenleme ivmesine göre yavaşlar ve
         * hedefi aşmadan oturur. Robot bir engele takılırsa aksiyon bitmez; gerekirse WithTimeout ile sar.
         *
         * @param targetPosition hedef konum (canlı okunur, null = hedef yok)
         * @param speed azami hız (m/s, 0'dan büyük; Robot.MAX_SPEED ile sınırlanır)
         * @param rotation hedefe gidilirken hedefe dönülecek mi?
         */
        public DriveToPosition(Vector3 targetPosition, float speed, boolean rotation){
            set(targetPosition, speed, rotation);
        }

        /** Sabit bir (x, z) noktasına gider. Diğer parametreler için Vector3 kurucusuna bak. */
        public DriveToPosition(float targetX, float targetZ, float speed, boolean rotation){
            set(new Vector3(targetX, 0f, targetZ), speed, rotation);
        }

        public void set(Vector3 targetPosition, float speed, boolean rotation){
            if(!(speed > 0f)) //0, negatif ve NaN
                throw new IllegalArgumentException("speed 0'dan büyük olmalı (m/s): " + speed);
            this.targetPos = targetPosition;
            this.speed = Math.min(speed, Physic3D.Robot.MAX_SPEED);
            this.rotation = rotation;
        }

        @Override
        public void start(@NonNull Physic3D.Robot robot){}

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){
            if(targetPos == null) return true; //eğer hedef yoksa bitir

            float dx = targetPos.x - robot.position.x;
            float dz = targetPos.z - robot.position.z;
            float distance = (float)Math.sqrt(dx * dx + dz * dz);

            //vardı mı? Yalnızca yakın olmak yetmez, yavaş da olmalı (yoksa hedefi hızla geçip giderken bitirirdi)
            if(distance < ARRIVE_TOLERANCE && robot.velocity.len() < ARRIVE_MAX_SPEED) return true;

            //hız profili: azami hız ile "hedefte durabilmek için şu an
            //sahip olunabilecek en yüksek hız" arasından küçüğü.

            //v = sqrt(2 * a * d): bu hızdan frenlenince tam hedefte durulur
            //(Robot'un yön kontrolcüsüyle aynı fikir).
            float brakeSpeed = (float)Math.sqrt(2f * Physic3D.Robot.ACCELERATION * BRAKE_FACTOR * distance);
            float desiredSpeed = Math.min(speed, brakeSpeed);

            //robotun girdisi 0..1 arası MAX_SPEED aranıdır
            float inputMag = desiredSpeed / Physic3D.Robot.MAX_SPEED;
            float inputX = 0f;
            float inputZ = 0f;
            if(distance > 0.0001f){
                inputX = (dx / distance) * inputMag;
                inputZ = (dz / distance) * inputMag;
            }

            robot.setInputXZ(inputX, inputZ);

            if(rotation){
                robot.setTargetHeading((distance > 0.1f)
                        ? (float) Math.atan2(dz, dx) : Physic3D.Robot.NO_TURN);
            }

            return false;
        }

        @Override
        public void end(@NonNull Physic3D.Robot robot){
            robot.setInputXZ(0, 0);
            if(rotation) robot.setTargetHeading(Physic3D.Robot.NO_TURN);
        }
    }

    public static class DriveToNearestFuel implements AutoAction{

        //Robot yakıtın merkezine giremez: yakıt robotun yüzüne değince (robotun yarı boyu + yakıtın yarı boyutu + küçük pay) "ulaşıldı"
        //say. Bu mesafe intake'in ucundan (ROBOT_SIZE/2 + INTAKE_DEPTH + FUEL_SIZE/2) KÜÇÜK olmalı ki intake açıksa yakıt bitmeden
        //önce alınabilsin. Karşılaştırma kare mesafeyle yapıldığı için karesi alınıyor.
        private float ARRIVE_DISTANCE_SQ;

        private final DriveToPosition driveToPosition;
        private Physic3D.PhysicsEntity targetFuel;
        private final boolean rotation;
        private final float speed;

        /** En yakın yakıtı bulur, DriveToPosition ile yakıta gider. Seçtiği yakıt dünyadan çıkarsa
         * (biz aldıysak aksiyon biter, başkası aldıysa başka bir yakıt seçer).
         * Yakıta değecek kadar yaklaşınca (ya da robot yakıtı alınca) aksiyon biter.
         * Intake'i açmaz: yakıtı almak için önce SetIntakeState(true) kullan (ya da ParallelActionsWithMain ile birleştir).
         * Yakıt kaçarsa/robot takılırsa bitmeyebilir; gerekirse WithTimeout ile sar.
         *
         * @param speed azami hız (m/s, 0'dan büyük; Robot.MAX_SPEED ile sınırlanır)
         * @param rotation hedefe gidilirken hedefe dönülecek mi?
         */
        public DriveToNearestFuel(float speed, boolean rotation){
            this.rotation = rotation;
            this.speed = speed;
            driveToPosition = new DriveToPosition(null, speed, rotation);
        }

        @Override
        public void start(@NonNull Physic3D.Robot robot){
            //robotun yakıt'ının yarı çapı farklı olabileceğinden
            //her seferinde yeniden hesapla
            float ARRIVE_DISTANCE = Physic3D.Robot.ROBOT_SIZE / 2f + robot.getFuelHalfSize() + 0.03f;
            ARRIVE_DISTANCE_SQ = ARRIVE_DISTANCE * ARRIVE_DISTANCE;

            targetFuel = findTheNearestBall(robot);
            driveToPosition.set(
                    (targetFuel == null) ? null : targetFuel.position, speed, rotation);
            driveToPosition.start(robot);
        }

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){
            //seçili yakıt artık Robotun dünyasında değilse (alındı ya da silindi)
            if(targetFuel != null && targetFuel.getWorld() != robot.getWorld()){
                //eğer bizim robot topu almışsa aksiyonu bitir
                if(robot.isIntakeContains(targetFuel)) return true;
                targetFuel = null; //başkası aldı, yeni top seçilecek
            }

            //henüz hedef yoksa en yakın topu bul
            if(targetFuel == null){
                targetFuel = findTheNearestBall(robot);

                //yine top yoksa yapacak bir şey kalmadı, aksiyonu bitir
                if(targetFuel == null) return true;

                driveToPosition.set(targetFuel.position, speed, rotation);
            }

            //yakıta değecek kadar yaklaştıysa bitir
            if(robot.getDistanceSquare(targetFuel) <= ARRIVE_DISTANCE_SQ) return true;

            //DriveToPosition kendi bitişi buraya yetişmez ama zararı yok
            return driveToPosition.execute(robot, dt);
        }

        @Override
        public void end(@NonNull Physic3D.Robot robot){
            targetFuel = null;
            driveToPosition.end(robot);
        }

        /** Robotun dünyasındaki Robot'a en yakın yakıtı bulup, onu döndürür. Yakıt yoksa null. */
        private Physic3D.PhysicsEntity findTheNearestBall(@NonNull Physic3D.Robot robot){
            final Physic3D physic = robot.getWorld();
            if(physic == null) return null;

            Physic3D.PhysicsEntity nearest = null;
            float minDistance = Float.MAX_VALUE;

            //tüm topları tara
            for(Physic3D.PhysicsEntity e : physic.entities){
                if(!robot.isTargetFuel(e)) continue; //robotun alabileceği bir yakıt değil

                float d = robot.getDistanceSquare(e);
                if(d > minDistance) continue;
                //şu an kadar bulunan en yakın yakıttan daha yakınsa

                minDistance = d;
                nearest = e;
            }

            return nearest;
        }
    }

    public static class ParallelActions implements AutoAction{
        private final AutoAction[] actions;
        private final boolean[] isDone; //biten aksiyonları tekrar çağırmamak için

        /** Verilen dizideki tüm aksiyonları aynı anda başlatır
         * ve aynı anda çalıştırır. Hepsi bitince aksiyonunu bitirir.
         * null aksiyonlar (ya da null dizi) yok sayılır.
         *
         * @param actions çalıştırılacak aksiyonlar
         */
        public ParallelActions(AutoAction... actions){
            this.actions = (actions == null) ? new AutoAction[0] : actions.clone();
            this.isDone = new boolean[this.actions.length];
        }

        @Override
        public void start(@NonNull Physic3D.Robot robot){
            for(int i = 0; i < actions.length; i++){
                //null aksiyon baştan bitmiş sayılır
                if(!(isDone[i] = actions[i] == null))
                    actions[i].start(robot);
            }
        }

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){
            boolean allDone = true;

            for(int i = 0; i < actions.length; i++){
                if(isDone[i]) continue; //aksiyon bitmiş

                if(actions[i].execute(robot, dt)){
                    isDone[i] = true;
                    actions[i].end(robot); //aksiyon yeni bitti
                }
                else allDone = false; //devam eden bir aksiyon var
            }

            return allDone;
        }

        @Override
        public void end(@NonNull Physic3D.Robot robot){
            //zorla durdurulma durumlarına karşı henüz bitmemiş
            //aksiyonlar varsa end()'lerini çağır
            for(int i = 0; i < actions.length; i++){
                if(isDone[i]) continue;

                isDone[i] = true;
                actions[i].end(robot);
            }
        }
    }

    public static class ParallelActionsWithMain implements AutoAction{
        private final AutoAction mainAction;
        private final ParallelActions otherActions;
        private boolean isOthersDone;

        /** Verilen tüm aksiyonları aynı anda başlatır
         * ve aynı anda çalıştırır. ANA AKSİYON bitince
         * aksiyonunu bitirir (diğerleri bitmemişse onlar da sonlandırılır).
         *
         * @param mainAction ana aksiyon
         * @param actions çalıştırılacak diğer aksiyonlar
         */
        public ParallelActionsWithMain(AutoAction mainAction, AutoAction... actions){
            this.mainAction = mainAction;
            this.otherActions = new ParallelActions(actions);
        }

        @Override
        public void start(@NonNull Physic3D.Robot robot){
            if(mainAction != null) mainAction.start(robot);
            otherActions.start(robot);
            isOthersDone = false;
        }

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){
            if(mainAction == null || mainAction.execute(robot, dt)) //ana aksiyon bittiğinde bitir
                return true;

            if(!isOthersDone) //eğer bitmedilerse diğer aksiyonları da çalıştır
                isOthersDone = otherActions.execute(robot, dt);

            return false;
        }

        @Override
        public void end(@NonNull Physic3D.Robot robot){
            if(mainAction != null) mainAction.end(robot);
            otherActions.end(robot); //zaten bitenlere dokunmaz
        }
    }

    public static class WithTimeout implements AutoAction{
        private final float timeout;
        private float totalTime;
        private final Condition condition;
        private final AutoAction action;

        /** Verilen aksiyon bittiğinde ya da verilen koşul sağlandığında
         * aksiyonu bitirir (hangisi önce olursa).
         * @param action sarılan aksiyon
         * @param condition sağlandığında aksiyonun zorla bitirileceği koşul
         */
        public WithTimeout(AutoAction action, Condition condition){
            this.condition = Objects.requireNonNull(condition);
            this.action = action;
            this.timeout = 0f;
        }

        /** Aksiyon verilen süreden uzun sürerse (süre aşımı)
         * aksiyonu sonlandırır. Takılmaların olabileceği aksiyonlar
         * için birebir.
         * @param action sarılan aksiyon
         * @param timeout saniye cinsinden süre aşımı
         */
        public WithTimeout(AutoAction action, float timeout){
            if(!(timeout >= 0)) throw new IllegalArgumentException("Zaman aşımı 0'dan küçük (veya NaN) olamaz: "+timeout);

            this.condition = null;
            this.action = action;
            this.timeout = timeout;
        }

        @Override
        public void start(@NonNull Physic3D.Robot robot){
            totalTime = 0f;
            if(action != null) action.start(robot);
        }

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){
            if(action == null || action.execute(robot, dt)) return true;

            totalTime += dt;
            return (condition != null)
                    ? (condition.isConditionMet(robot, dt)) : (totalTime >= timeout);
        }

        @Override
        public void end(@NonNull Physic3D.Robot robot){ if(action != null) action.end(robot); }
    }

    public static class TurnToAngle implements AutoAction{

        private final float targetRadian;
        private final Condition condition;

        /** Robotu verilen açıya(randyan cinsinden) döndürür ve aksiyonu sonlandırır.
         * Açı herhangi bir aralıkta olabilir (-PI..PI, 0..2PI, ...), robot en kısa yoldan döner.
         * @param targetRadian hedef açı (radyan cinsinden)
         */
        public TurnToAngle(float targetRadian){
            this.targetRadian = validateAngle(targetRadian);
            this.condition = null;
        }

        /** Verilen koşul sağlanana kadar robotu hedeflenen açıya
         * döndürür (radyan cinsinden). Koşul sağlandığında aksiyon
         * biter.
         *
         * @param condition aksiyonun bitmesi için sağlanması gereken koşul (null olamaz)
         * @param targetRadian hedef açı (radyan cinsinden)
         */
        public TurnToAngle(Condition condition, float targetRadian){
            this.condition = Objects.requireNonNull(condition);
            this.targetRadian = validateAngle(targetRadian);
        }

        private static float validateAngle(float targetRadian){
            if(Float.isNaN(targetRadian) || Float.isInfinite(targetRadian))
                throw new IllegalArgumentException("Hedef açı geçerli bir sayı olmalı (NaN = dönüş yok olamaz): " + targetRadian);
            return targetRadian;
        }

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){
            robot.setTargetHeading(targetRadian);

            return (condition != null)
                    ? condition.isConditionMet(robot, dt)
                    : Math.abs(robot.getHeadingError(targetRadian)) < HEADING_TOLERANCE;
        }

        @Override
        public void start(@NonNull Physic3D.Robot robot){ robot.setTargetHeading(Physic3D.Robot.NO_TURN); }
        @Override
        public void end(@NonNull Physic3D.Robot robot){ robot.setTargetHeading(Physic3D.Robot.NO_TURN); }
    }

    public static class TurnToPoint implements AutoAction{

        private final Vector3 target; //yalnızca x ve z okunur
        private final Condition condition;

        /** Robotu verilen pozisyona döndürür ve aksiyonu bitirir (canlı okunur, yalnızca x ve z kullanılır).
         * @param targetPosition hedef pozisyon */
        public TurnToPoint(Vector3 targetPosition){
            this.target = targetPosition;
            this.condition = null;
        }

        /** Robotu verilen sabit (x, z) noktasına döndürür ve aksiyonu bitirir. */
        public TurnToPoint(float targetX, float targetZ){
            this(new Vector3(targetX, 0f, targetZ));
        }

        /** Verilen koşul sağlanana kadar robotu hedef pozisyona döndürür.
         * Koşul sağlandığında aksiyon biter.
         * @param targetPosition hedef pozisyon
         * @param condition sağlanacak koşul (null olamaz)
         */
        public TurnToPoint(Vector3 targetPosition, Condition condition){
            this.target = targetPosition;
            this.condition = Objects.requireNonNull(condition);
        }

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){
            if(target == null) return true; //aksiyonu bitir

            float dx = target.x - robot.position.x;
            float dz = target.z - robot.position.z;
            float targetHeading = (float)Math.atan2(dz, dx);

            robot.setTargetHeading(targetHeading);

            return (condition != null)
                    ? condition.isConditionMet(robot, dt)
                    : Math.abs(robot.getHeadingError(targetHeading)) < HEADING_TOLERANCE;
        }

        @Override
        public void start(@NonNull Physic3D.Robot robot){ robot.setTargetHeading(Physic3D.Robot.NO_TURN); }
        @Override
        public void end(@NonNull Physic3D.Robot robot){ robot.setTargetHeading(Physic3D.Robot.NO_TURN); }
    }

    public static class Shoot implements AutoAction{
        private final int targetShotCount;
        private int shotCounter;
        private final float x, y, z;
        private final boolean untilIntakeEmpty; //intake boşalana kadar mı fırlatacak

        /** Robotun intake'inden istenilen sayıda yakıtı ard arda fırlatır. İstenilen
         * sayıda yakıt fırlatana kadar devam eder, eğer robotun intake'inde
         * yeteri kadar yakıt yoksa takılma yaşanabilir. WithTimeout ile sarabilirsin.
         * @param count : atılması hedeflenen yakıt sayısı */
        public Shoot(int count, float targetX, float targetY, float targetZ){
            this.untilIntakeEmpty = false;
            this.targetShotCount = count;
            this.x = targetX; this.y = targetY; this.z = targetZ;
        }

        /** Robotun intake'i tamamen boşalana kadar tüm yakıtları ard arda fırlatır. */
        public Shoot(float targetX, float targetY, float targetZ){
            this.untilIntakeEmpty = true;
            this.targetShotCount = -1;
            this.x = targetX; this.y = targetY; this.z = targetZ;
        }

        @Override
        public void start(@NonNull Physic3D.Robot robot){
            shotCounter = 0;
        }

        @Override
        public boolean execute(@NonNull Physic3D.Robot robot, float dt){
            //robotun tekte çoklu top atmasına karşın, intake'den çıkan topları say
            int beforeBallCount = robot.getIntakeCount();
            robot.shoot(x, y, z);
            shotCounter += robot.getIntakeCount() - beforeBallCount;

            return (untilIntakeEmpty) ? robot.getIntakeCount() == 0 : shotCounter >= targetShotCount;
        }

        @Override
        public void end(@NonNull Physic3D.Robot robot){}
    }
}
