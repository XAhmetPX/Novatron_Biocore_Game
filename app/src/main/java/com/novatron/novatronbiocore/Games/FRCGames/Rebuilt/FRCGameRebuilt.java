package com.novatron.novatronbiocore.Games.FRCGames.Rebuilt;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

import com.novatron.novatronbiocore.Configs;
import com.novatron.novatronbiocore.GamePanel;
import com.novatron.novatronbiocore.Games.FRCGames.AutoActions;
import com.novatron.novatronbiocore.Games.Game;
import com.novatron.novatronbiocore.R;
import com.novatron.novatronbiocore.tools.CanvasButton;
import com.novatron.novatronbiocore.tools.DegreeStickN;
import com.novatron.novatronbiocore.tools.FloatingDegreeStickN;
import com.novatron.novatronbiocore.tools.FloatingJoystick;
import com.novatron.novatronbiocore.tools.Joystick;
import com.novatron.novatronbiocore.tools.Viewport;
import com.novatron.novatronbiocore.Games.FRCGames.Physic3D;

import java.util.ArrayList;
import java.util.Locale;

public class FRCGameRebuilt implements Game{

    private static final float BALL_SPACING = FuelBall.RADIUS * 2 + 0.02f;

    //EKSEN İSİMLENDİRMESİ (FRC kılavuzuyla uyumlu, tüm FRC dosyalarında geçerli):
    //  x = WIDTH  = sahanın KISA kenarı (~8 m)
    //  y = HEIGHT = yükseklik (3. boyut)
    //  z = DEPTH  = sahanın UZUN kenarı (~16 m)
    //Fizik dünyasında konum/hız Vector3'tür ve sıra her yerde (x, y, z)'dir. Yer düzleminde çalışan API'ler isimde XZ taşır.
    //Açı: 0 = +x yönü, +x'ten +z'ye doğru artar.
    //2D ekranda saha yatay görünür: z ekranda SAĞA, x ekranda YUKARI doğru artar.

    //engel uzunlukları (W = x yönündeki ölçü, D = z yönündeki ölçü, H = yükseklik)
    public static final float TRENCH_STICK_D = 0.088f;
    public static final float TRENCH_STICK_W = 1.279f;
    public static final float TRENCH_STICK_H = 0.5652f;
    public static final float TRENCH_BODY_W = 0.298f;
    public static final float TRENCH_BODY_D = 1.193f;
    public static final float TRENCH_BODY_H = 0.593f;


    public static final float BUMP_W = 1.854f;
    public static final float BUMP_D = 1.128f;
    public static final float BUMP_H = 0.1654f;

    public static final float HUB_W = 1.19f;
    public static final float HUB_D = HUB_W;
    public static final float HUB_H = 1.83f;

    public static final float TOWER_Z_OFFSET = 1.143f; //z yönünde (uzun kenar boyunca)
    public static final float TOWER_W = 0.895f;
    public static final float TOWER_D = 0.076f;
    public static final float TOWER_H = 1.822f;

    public static final float DEPOT_FRAME_W = 0.0762f;
    public static final float DEPOT_OFFSET_X = 1.57f; //x yönünde (yan duvardan)
    public static final float DEPOT_W = (BALL_SPACING * 6) + DEPOT_FRAME_W * 2; //x yönü: 6 sütun top
    public static final float DEPOT_D = (BALL_SPACING * 4) + DEPOT_FRAME_W * 2; //z yönü: 4 sıra top

    //alan uzunlukları
    public static final float ALLIANCE_ZONE_DEPTH = 4.03f; //z yönü
    public static final float NEUTRAL_ZONE_DEPTH = 8.38f;  //z yönü
    public static final float FIELD_DEPTH_METERS = ALLIANCE_ZONE_DEPTH * 2 + NEUTRAL_ZONE_DEPTH; //z ekseni (uzun kenar)
    public static final float FIELD_WIDTH_METERS = (TRENCH_STICK_W + TRENCH_BODY_W + BUMP_W) * 2 + HUB_W;              //x ekseni (kısa kenar)

    //Süreler
    private static final int TELEOP_TIME = 140 * 60;
    private static final int AUTONOMOUS_TIME = 20 * 60;
    private static final int AUTONOMOUS_TO_TELEOP_WAIT_TIME = 2 * 60;

    final GamePanel gp;
    private Joystick joystick;
    private DegreeStickN degreeStick;
    private CanvasButton btnIntake, btnShot;
    final Viewport viewport;
    private State state = State.RUNNING;

    //objects
    private Physic3D physic;
    RobotRebuilt playerRobot;
    private static final float DT = 1.0f / 60.0f;
    //intake güncellemesi için (tüm cisimleri taramamak için)
    private final java.util.List<RobotRebuilt> robots = new java.util.ArrayList<>();

    //TODO temps
    private final Paint fieldPaint = new Paint();
    private final Paint robotPaint = new Paint();

    //buton (UI thread) yazar, oyun thread'i okur: volatile değerin görünürlüğünü garanti eder
    private volatile boolean intakeOn;
    private volatile boolean shootOn;

    //gösterge
    private int redAllianceScore, blueAllianceScore;
    private int time;
    private boolean inAutonomous;
    private float timeWidth;
    private float teleopWidth, teleopTimeWidth;

    //paints
    private Paint indicatorScorePaint;
    private Paint indicatorTimePaint;
    private Paint indicatorTeleopPaint;
    private Paint indicatorTeleopTimePaint;
    private Paint depotPaint;

    private float touchX, touchZ;

    public FRCGameRebuilt(GamePanel gp){
        this.gp = gp;
        //noinspection SuspiciousNameCombination
        this.viewport = new Viewport(FIELD_DEPTH_METERS, FIELD_WIDTH_METERS); //(ekran yatayı = z, ekran dikeyi = x)

        fieldPaint.setColor(Color.DKGRAY);
        fieldPaint.setStyle(Paint.Style.STROKE);
        fieldPaint.setStrokeWidth(5f);

        robotPaint.setColor(Color.BLUE);
        robotPaint.setStyle(Paint.Style.FILL);

        updateScreenSize();
    }

    @Override
    public void updateScreenSize(){
        viewport.updateScreenSize(gp.SCREEN_WIDTH, gp.SCREEN_HEIGHT);

        final float uiTile = gp.tileSize * Configs.UI_SCALE;

        if(degreeStick != null){
            degreeStick.setOuterRadius(uiTile * 1.7f);
            if(degreeStick instanceof FloatingDegreeStickN){
                FloatingDegreeStickN ds = (FloatingDegreeStickN) degreeStick;
                ds.setArea(gp.SCREEN_WIDTH / 2.0f, 0, gp.SCREEN_WIDTH, gp.SCREEN_HEIGHT);
            }
        }

        if(joystick != null){
            joystick.setOuterRadius(uiTile * 1.7f);
            if(joystick instanceof FloatingJoystick){
                FloatingJoystick js = (FloatingJoystick) joystick;
                js.setArea(0, 0, gp.SCREEN_WIDTH / 2.0f, gp.SCREEN_HEIGHT);
            }
        }

        float padding = uiTile * 0.1f;

        if(btnIntake != null && degreeStick != null){
            float right = gp.SCREEN_WIDTH - padding;
            float left = right - uiTile * 2;
            float bottom = gp.SCREEN_HEIGHT - (50 + degreeStick.getRadius()*2 + padding);
            float top = bottom - uiTile * 2;
            btnIntake.setBounds(left, top, right, bottom);
        }

        if(btnShot != null && joystick != null){
            float right = padding + uiTile * 2;
            float bottom = gp.SCREEN_HEIGHT - (50 + joystick.getOuterRadius()*2 + padding);
            float top = bottom - uiTile * 2;
            btnShot.setBounds(padding, top, right, bottom);
        }
    }

    @Override
    public void update(){
        if(joystick == null || degreeStick == null || btnIntake == null || btnShot == null) return;
        if(physic == null) return;

        if(!inAutonomous){
            //ekranda yukarı = +x, sağa = +z
            float inputX = (float)-joystick.getValueY();
            float inputZ = (float)joystick.getValueX();
            //degreeStick ekran açısı verir (0 = sağ, saat yönünün tersi pozitif); heading'e (0 = +x) çevir
            float targetHeading = (float)(Math.PI / 2.0 - degreeStick.getRadian());

            playerRobot.setInputs(inputX, inputZ, targetHeading);
            playerRobot.intakeActive = intakeOn;

            if(shootOn)
                playerRobot.shoot(FIELD_WIDTH_METERS/2f, HUB_H, ALLIANCE_ZONE_DEPTH + HUB_D/2f);
        }

        for(int i = 0; i < physic.entities.size(); i++)
            physic.entities.get(i).update(DT);

        physic.solveCollisions();

        //çarpışmalar kesinleştikten sonra intake'ler topları alır
        for(int i = 0; i < robots.size(); i++)
            robots.get(i).updateIntake();

        time--;
        if(time <= 0){
            if(inAutonomous){
                if(time == 0){
                    //robot girdilerinin hemen sıfırla
                    for(RobotRebuilt robot : robots)
                        robot.stopAutonomous();
                }
                else if(time <= -AUTONOMOUS_TO_TELEOP_WAIT_TIME){
                    //otonomdan teleopa geç
                    time = TELEOP_TIME;
                    inAutonomous = false;
                }
                else if(time == -(AUTONOMOUS_TO_TELEOP_WAIT_TIME - 60)){
                    gp.vibration.vibrateOneShot(400, 210);
                }
            }
            else{
                //oyun bitti
                time = 0;
                state = State.GAME_OVER;
            }
        }
    }

    @Override
    public void draw(Canvas canvas){
        if(canvas == null) return;

        canvas.drawColor(Color.parseColor("#313131"));

        //-----------------------------SAHA ÇİZİMİ-----------------------------
        //ekran yatayı = z, ekran dikeyi = x
        float fieldLeft = viewport.toPixelX(0);
        //noinspection SuspiciousNameCombination
        float fieldTop = viewport.toPixelY(FIELD_WIDTH_METERS);
        float fieldRight = viewport.toPixelX(FIELD_DEPTH_METERS);
        float fieldBottom = viewport.toPixelY(0);
        canvas.drawRect(fieldLeft, fieldTop, fieldRight, fieldBottom, fieldPaint);

        //kırmızı deposu
        drawDepot(canvas, FIELD_WIDTH_METERS - DEPOT_OFFSET_X - DEPOT_W, 0f, Color.RED);
        //mavi deposu
        drawDepot(canvas, DEPOT_OFFSET_X, FIELD_DEPTH_METERS - DEPOT_D, Color.BLUE);

        //----------------------------CANLİ ÇİZİMİ----------------------------
        physic.drawEntities(canvas, robotPaint); //tabanı yüksek olan cisim üstte çizilir

        //-------------------------------GÖSTERGE------------------------------
        final float indicatorH = gp.tileSize * 1.1f;
        drawIndicator(canvas, gp.SCREEN_WIDTH/2f, 20 + indicatorH/2f, indicatorH * 8f, indicatorH);

        if(!inAutonomous){
            //----------UI----------
            if(degreeStick != null) degreeStick.draw(canvas);
            if(joystick != null) joystick.draw(canvas);
            if(btnIntake != null) btnIntake.draw(canvas);
            if(btnShot != null) btnShot.draw(canvas);
        }

        //--------DEBUG--------
        if(!Configs.DEBUG) return;
        String text = String.format(Locale.US, "X: %f, Z: %f", touchX, touchZ);
        canvas.drawText(text, 20, gp.SCREEN_HEIGHT - 40, gp.ui.fontDebugText);
    }
    /** @param minX deponun x eksenindeki başlangıcı (m), @param minZ deponun z eksenindeki başlangıcı (m) */
    private void drawDepot(Canvas canvas, float minX, float minZ, int color){
        float half = DEPOT_FRAME_W / 2f;
        //ekran yatayı = z, ekran dikeyi = x
        float left   = viewport.toPixelX(minZ + half);
        float right  = viewport.toPixelX(minZ + DEPOT_D - half);
        float top    = viewport.toPixelY(minX + DEPOT_W - half);
        float bottom = viewport.toPixelY(minX + half);
        depotPaint.setStrokeWidth(viewport.toPixelSize(DEPOT_FRAME_W));
        depotPaint.setColor(color);
        canvas.drawRect(left, top, right, bottom, depotPaint);
    }
    private void drawIndicator(Canvas canvas, float centerX, float centerY, float width, float height){
        gp.imageStore.drawScaledPixelImageWithCenter(canvas,(inAutonomous)
                ? R.drawable.frc_game_counter_autonomous : R.drawable.frc_game_counter, centerX, centerY, width, height);
        double px = width / 256.0;
        float textX;
        float textY;
        String text;

        //ortadaki sayaç
        textX = centerX - timeWidth/2f;
        textY = centerY + (float)(px * 5);
        int tempTime = ((inAutonomous) ? ((time < 60) ? TELEOP_TIME : time) : time) / 60;
        text = String.format(Locale.US, "%01d:%02d", tempTime/60, tempTime % 60);
        canvas.drawText(text, textX, textY, indicatorTimePaint);

        //kırmızı skor
        text = String.valueOf(redAllianceScore);
        textY = centerY + (float)(px * 5);
        textX = centerX - (float)(px * 29.5f)
                - indicatorScorePaint.measureText(text)/2;
        canvas.drawText(text, textX, textY, indicatorScorePaint);

        //mavi skor
        text = String.valueOf(blueAllianceScore);
        textX = centerX + (float)(px * 29.5f)
                - indicatorScorePaint.measureText(text)/2f;
        canvas.drawText(text, textX, textY, indicatorScorePaint);

        if(inAutonomous) return;

        //teleop periyotu
        text = String.format(Locale.US, "%01d/%01d",getCurrentPeriod(time), 6);
        textY = centerY + (float)(px * 14.5);
        textX = centerX - (float)(px * 3.65) - teleopWidth/2f;
        canvas.drawText(text, textX, textY, indicatorTeleopPaint);

        //teleop periyotu kalan süre
        text = String.format(Locale.US, "%02d", getRemainingTimeInPeriod(time));
        textX = centerX + (float)(px * 10.5) - teleopTimeWidth/2f;
        canvas.drawText(text, textX, textY, indicatorTeleopTimePaint);
    }

    /** mevcut periyotun numarasını döndürür */
    public static int getCurrentPeriod(int time){
        if(time < 31 * 60){ return 6; }
        if(time < 51 * 60){ return 5; }
        if(time < 81 * 60){ return 4; }
        if(time < 106 * 60){ return 3; }
        if(time < 131 * 60){ return 2; }
        if(time < 141 * 60){ return 1; }
        else return -1;
    }
    /** saniye cinsinden diğer periyota kalan süreyi döndürür */
    private int getRemainingTimeInPeriod(int time){
        int remainingTicks;

        if(time <= 31 * 60){
            //Periyot 6 (Endgame): 30. saniyeden 0'a doğru kalan süre doğrudan time kadardır
            remainingTicks = time;
        }
        else if(time <= 55 * 60){
            //Periyot 5: 55. saniyeden 30. saniyeye kalan süre
            remainingTicks = time - (30 * 60);
        }
        else if(time <= 81 * 60){
            //Periyot 4: 80. saniyeden 55. saniyeye kalan süre
            remainingTicks = time - (55 * 60);
        }
        else if(time <= 106 * 60){
            //Periyot 3: 105. saniyeden 80. saniyeye kalan süre
            remainingTicks = time - (80 * 60);
        }
        else if(time < 131 * 60){
            //Periyot 2: 130. saniyeden 105. saniyeye kalan süre
            remainingTicks = time - (105 * 60);
        }
        else if(time < 141 * 60){
            //Periyot 1: 140. saniyeden 130. saniyeye kalan süre
            remainingTicks = time - (130 * 60);
        }
        else{
            remainingTicks = 0;
        }

        if(remainingTicks < 0)
            remainingTicks = 0;

        //Tick değerini saniyeye çevirip döndürür
        return remainingTicks / 60;
    }

    @Override
    public boolean handleTouch(MotionEvent event){
        if(Configs.DEBUG){
            touchX = viewport.toMeterY(event.getY()); //ekran dikeyi = x
            touchZ = viewport.toMeterX(event.getX()); //ekran yatayı = z
        }
        if(inAutonomous) return false;
        //UI thread'i, oyun thread'i destroyGame() ile nesneleri null yaparken çağrılabilir: yerel kopyayla koru
        final Joystick joystick = this.joystick;
        final CanvasButton btnIntake = this.btnIntake;
        final CanvasButton btnShoot = this.btnShot;
        final DegreeStickN degreeStick = this.degreeStick;
        if(joystick == null || btnIntake == null || degreeStick == null || btnShoot == null) return false;

        return (btnShoot.handleTouch(event) || joystick.handleTouch(event)) | (btnIntake.handleTouch(event) || degreeStick.handleTouch(event));
    }

    @Override
    public void preloadGame(){
        physic = new Physic3D(FIELD_WIDTH_METERS, FIELD_DEPTH_METERS); //(x ölçüsü, z ölçüsü)

        final float RED_ALLIANCE_Z = ALLIANCE_ZONE_DEPTH - RobotRebuilt.ROBOT_SIZE / 2f;
        final float BLUE_ALLIANCE_Z = FIELD_DEPTH_METERS - RED_ALLIANCE_Z;

        //-----------------------------------KIRMIZI İTTİFAK-----------------------------------
        addRobot(new RobotRebuilt(7.4f, 0f, RED_ALLIANCE_Z, (float) Math.toRadians(180), 0.4f, 30,  false,
                0.3f, 58f, 0.41f).setAutoScript(AutoActionSetter::setRobot1Autonomous));
        addRobot(new RobotRebuilt(4.1f, 0f, RED_ALLIANCE_Z, (float) Math.toRadians(180), 0.64f, 35,  false,
                0.2f, 60f, 0.69f).setAutoScript(AutoActionSetter::setRobot2Autonomous));
        this.playerRobot = addRobot(new RobotRebuilt(2.7f, 0f, RED_ALLIANCE_Z, (float) Math.toRadians(0), 0.62f, 45, false,
                0.08f, 65f, 0.64f).setAutoScript(AutoActionSetter::setRobot3Autonomous));

        //-------------------------------------MAVİ İTTİFAK-------------------------------------
        addRobot(new RobotRebuilt(7.4f, 0f, BLUE_ALLIANCE_Z, (float) Math.toRadians(180), 0.55f, 35,  true,
                0.15f, 62f, 0.57f).setAutoScript(AutoActionSetter::setRobot4Autonomous));
        addRobot(new RobotRebuilt(5.8f, 0f, BLUE_ALLIANCE_Z, (float) Math.toRadians(270), 0.4f, 25,  true,
                0.25f, 60f, 0.41f).setAutoScript(AutoActionSetter::setRobot5Autonomous));
        addRobot(new RobotRebuilt(0.7f, 0f, BLUE_ALLIANCE_Z, (float) Math.toRadians(0), 0.54f, 40,  true,
                0.09f, 60f, 0.59f).setAutoScript(AutoActionSetter::setRobot6Autonomous));

        //-------------------------------------YAKIT TOPLAR------------------------------------

        //(cols = x yönündeki sütun sayısı, rows = z yönündeki sıra sayısı)
        //merkez
        spawnBallGridCentered(FIELD_WIDTH_METERS / 2f, FIELD_DEPTH_METERS / 2f, 30, 12);

        int depotCols = 6, depotRows = 4;
        float depotCenterX = DEPOT_OFFSET_X + DEPOT_W / 2f;

        //kırmızı depo
        spawnBallGridCentered(FIELD_WIDTH_METERS - depotCenterX, DEPOT_D / 2f, depotCols, depotRows);

        //mavi depo
        spawnBallGridCentered(depotCenterX, FIELD_DEPTH_METERS - DEPOT_D / 2f, depotCols, depotRows);

        //---------------------------------------ENGELLER---------------------------------------
        setObstacles();

        //-------------------------------JOYSTICK VE DEGREE_STICK-------------------------------
        if(Configs.IS_USE_FLOATING_VIEWS){
            degreeStick = new FloatingDegreeStickN();
            joystick = new FloatingJoystick();
        }
        else{
            degreeStick = new DegreeStickN(gp.SCREEN_WIDTH, gp.SCREEN_HEIGHT);
            degreeStick.setPosition(DegreeStickN.Position.BOTTOM_RIGHT);

            joystick = new Joystick(gp.SCREEN_WIDTH, gp.SCREEN_HEIGHT);
            joystick.setPosition(Joystick.Position.BOTTOM_LEFT);
        }
        btnIntake = new CanvasButton(0, 0, 0, 0,
                gp.getContext().getString(R.string.intake)+"OFF", Color.parseColor("#66000000"));
        btnIntake.setOnClickListener(() -> {
            //noinspection NonAtomicOperationOnVolatileField
            intakeOn = !intakeOn;
            updateIntakeLabel();
        });
        btnShot = new CanvasButton(0, 0, 0, 0,
                gp.getContext().getString(R.string.shoot), Color.parseColor("#66000000"));
        btnShot.setOnDownListener(() -> shootOn = true);
        btnShot.setOnClickListener(() -> shootOn = false);

        //-------------------------------------GÖSTERGE-------------------------------------
        indicatorScorePaint = new Paint();
        indicatorScorePaint.setColor(Color.WHITE);
        indicatorScorePaint.setTypeface(gp.ui.pixelFont);
        indicatorScorePaint.setTextSize(gp.tileSize*0.25f);

        indicatorTimePaint = new Paint(indicatorScorePaint);
        indicatorTimePaint.setColor(Color.BLACK);
        indicatorTimePaint.setTextSize(gp.tileSize*0.22f);
        timeWidth = indicatorTimePaint.measureText("0:00");

        indicatorTeleopPaint = new Paint(indicatorTimePaint);
        indicatorTeleopPaint.setTextSize(gp.tileSize*0.16f);
        teleopWidth = indicatorTeleopPaint.measureText("0/6");

        indicatorTeleopTimePaint = new Paint(indicatorTeleopPaint);
        indicatorTeleopTimePaint.setTextSize(gp.tileSize*0.13f);
        teleopTimeWidth = indicatorTeleopTimePaint.measureText("00");

        depotPaint = new Paint();
        depotPaint.setStyle(Paint.Style.STROKE);
        depotPaint.setStrokeWidth(viewport.toPixelSize(DEPOT_FRAME_W));

        gp.imageStore.preloadImage(R.drawable.frc_game_counter);
        gp.imageStore.preloadImage(R.drawable.frc_game_counter_autonomous);
    }
    private void setObstacles(){

        final float RED_OBSTACLES_CENTER_Z = ALLIANCE_ZONE_DEPTH + HUB_D/2f;
        //-------------------KIRMIZI-------------------
        //merkez
        addHub(RED_OBSTACLES_CENTER_Z, false);
        //hendek 1
        addTrench(RED_OBSTACLES_CENTER_Z, false);
        //hendek 2
        addTrench(RED_OBSTACLES_CENTER_Z, true);
        //tümsek 1
        addBump(RED_OBSTACLES_CENTER_Z, false);
        //tümsek 2
        addBump(RED_OBSTACLES_CENTER_Z, true);
        //kule
        addTower(true);
        //kule filesi
        addTent(RED_OBSTACLES_CENTER_Z + 0.8919f);


        final float BLUE_OBSTACLES_CENTER_Z = FIELD_DEPTH_METERS - (ALLIANCE_ZONE_DEPTH + HUB_D/2f);
        //---------------------MAVİ--------------------
        //merkez
        addHub(BLUE_OBSTACLES_CENTER_Z, true);
        //hendek 1
        addTrench(BLUE_OBSTACLES_CENTER_Z, false);
        //hendek 2
        addTrench(BLUE_OBSTACLES_CENTER_Z, true);
        //tümsek 1
        addBump(BLUE_OBSTACLES_CENTER_Z, false);
        //tümsek 2
        addBump(BLUE_OBSTACLES_CENTER_Z, true);
        //kule
        addTower(false);
        //kule filesi
        addTent(BLUE_OBSTACLES_CENTER_Z - 0.8919f);
    }
    private void addHub(float startZ, boolean isBlueHub){
        physic.addEntity(new Physic3D.Obstacle(
                FIELD_WIDTH_METERS/2f, 0, startZ, 0,
                HUB_W, HUB_H, HUB_D, viewport, gp.ui.fontDebug){

            private final int signum = (isBlueHub) ? -1 : 1;

            @Override
            protected void onEntityLanded(Physic3D.PhysicsEntity landed){
                if(getWorld() == null) return;
                if(!(landed instanceof FuelBall)) return;

                if(isBlueHub) blueAllianceScore++;
                else redAllianceScore++;

                java.util.Random r = getWorld().random;

                float ballStartZ = this.startZ + (signum * (HUB_D/2f + FuelBall.RADIUS + 0.02f));
                float vz = (r.nextFloat() * 8f + 6f) * signum;
                landed.respawn(landed.position.x, HUB_H/2f, ballStartZ,
                        landed.velocity.x * r.nextFloat(), 0, vz);
            }
        });
    }
    private void addTrench(float startZ, boolean subtractFieldWidth){
        //çubuk
        float startX = (subtractFieldWidth) ? FIELD_WIDTH_METERS - TRENCH_STICK_W / 2f : TRENCH_STICK_W / 2f;
        physic.addEntity(new Physic3D.Obstacle(
                startX, TRENCH_STICK_H, startZ, 0,
                TRENCH_STICK_W, TRENCH_STICK_D, TRENCH_STICK_D, viewport, gp.ui.fontDebug));
        //gövde
        startX = (subtractFieldWidth) ? FIELD_WIDTH_METERS - (TRENCH_STICK_W + TRENCH_BODY_W/2f) : TRENCH_STICK_W + TRENCH_BODY_W/2f;
        physic.addEntity(new Physic3D.Obstacle(
                startX, 0, startZ, 0,
                TRENCH_BODY_W, TRENCH_BODY_H, TRENCH_BODY_D, viewport, gp.ui.fontDebug));
    }
    private void addBump(float startZ, boolean subtractFieldWidth){
        float startX = (subtractFieldWidth) ? FIELD_WIDTH_METERS - (TRENCH_STICK_W + TRENCH_BODY_W + BUMP_W/2f)
                : TRENCH_STICK_W + TRENCH_BODY_W + BUMP_W/2f;

        physic.addEntity(new Physic3D.Obstacle(startX, -BUMP_H/2f, startZ, 0,
                BUMP_W, BUMP_H, BUMP_D, viewport, gp.ui.fontDebug).setSensor(true));
    }
    private void addTower(boolean isRedAlliance){
        float startZ = (isRedAlliance) ? TOWER_Z_OFFSET : FIELD_DEPTH_METERS - TOWER_Z_OFFSET;
        physic.addEntity(new Physic3D.Obstacle(
                FIELD_WIDTH_METERS/2f, 0, startZ, 0,
                TOWER_W, TOWER_H, TOWER_D, viewport, gp.ui.fontDebug
        ));
    }
    private void addTent(float startZ){
        physic.addEntity(new Physic3D.Obstacle(
                FIELD_WIDTH_METERS/2f, HUB_H, startZ, 0,
                1.4419f, 1.6299f, 0.05f, viewport, gp.ui.fontDebug){
            {
                //canlının yataydaki hızını sonlandır
                entityIntersectListener = (other, impactSpeed) -> other.velocity.set(other.velocity.x*0.01f, other.velocity.y, 0);
            }
        });
    }

    private void updateIntakeLabel(){
        final CanvasButton btn = btnIntake;
        if(btn == null) return;
        btn.setLabel(gp.getContext().getString(R.string.intake) + ((intakeOn) ? "ON" : "OFF"));
    }

    /** Robotu hem fizik dünyasına hem intake güncelleme listesine ekler */
    private RobotRebuilt addRobot(RobotRebuilt robot){
        robots.add(robot);
        return physic.addEntity(robot);
    }
    private void spawnBallGrid(float startX, float startZ, int columns, int rows){
        for(int c = 0; c < columns; c++){
            for(int r = 0; r < rows; r++){
                float x = startX + (c * BALL_SPACING);
                float z = startZ + (r * BALL_SPACING);
                physic.addEntity(new FuelBall(x, 0f, z, this));
            }
        }
    }
    private void spawnBallGridCentered(float centerX, float centerZ, int cols, int rows){
        spawnBallGrid(centerX - ((cols - 1) * BALL_SPACING) / 2f,
                centerZ - ((rows - 1) * BALL_SPACING) / 2f, cols, rows);
    }

    @Override
    public void destroyGame(){
        if(physic != null)
            physic.entities.clear();
        physic = null;
        robots.clear();
        playerRobot = null;
        degreeStick = null;
        btnIntake = null;
        btnShot = null;
        joystick = null;
        indicatorScorePaint = null;
        indicatorTimePaint = null;
        gp.imageStore.removeImage(R.drawable.frc_game_counter);
        gp.imageStore.removeImage(R.drawable.frc_game_counter_autonomous);
    }
    @Override
    public void reset(){
        if(physic == null) return;
        state = State.RUNNING;
        physic.resetAll();
        joystick.reset();
        degreeStick.reset();
        intakeOn = false;
        shootOn = false;
        updateIntakeLabel();
        time = AUTONOMOUS_TIME;
        inAutonomous = true;
        blueAllianceScore = 0;
        redAllianceScore  = 0;
    }

    @Override
    public int getTime(){ return -1; }
    @Override
    public int getCurrentScore(){ return -1; }
    @Override
    public int getMaxScore(){ return -1; }
    @Override
    public State getState(){ return state; }
    @Override
    public int getLogoTextureID(){ return -1; }

    public static class FuelBall extends Physic3D.Ball{

        public static final float RADIUS          = 0.07506f; //25 cm çap (0.075m yarıçap)

        //çizim: üstten görünüşte yükseklik, topun büyümesiyle ve zeminde bir gölgeyle gösterilir
        private static final float HEIGHT_DRAW_SCALE = 0.5f;  //her 1 m yükseklik için yarıçap +%50
        private static final Paint SHADOW_PAINT = new Paint();
        static{
            SHADOW_PAINT.setColor(Color.BLACK);
            SHADOW_PAINT.setStyle(Paint.Style.FILL);
            SHADOW_PAINT.setAntiAlias(true);
        }

        private final FRCGameRebuilt frcGameRebuilt;
        //TODO temp
        private final Paint ballPaint = new Paint();

        public FuelBall(float startX, float startY, float startZ, FRCGameRebuilt frcGameRebuilt){
            super(startX, startY, startZ, 0f, 0.215f, 0.75f, RADIUS * 2f);
            this.frcGameRebuilt = frcGameRebuilt;

            ballPaint.setColor(Color.YELLOW);
            ballPaint.setStyle(Paint.Style.FILL);
            ballPaint.setAntiAlias(true);
        }

        @Override
        public void drawShadow(Canvas canvas){
            //gölge zeminde, topun gerçek (x, z) konumunda ve normal boyutta; yükseldikçe silikleşir
            SHADOW_PAINT.setAlpha(Math.max(30, (int) (110f - 40f * position.y)));
            //noinspection SuspiciousNameCombination
            canvas.drawCircle(frcGameRebuilt.viewport.toPixelX(position.z),
                    frcGameRebuilt.viewport.toPixelY(position.x),
                    frcGameRebuilt.viewport.toPixelSize(RADIUS), SHADOW_PAINT);
        }

        @Override
        public void draw(Canvas canvas, Paint paint){
            //ekran yatayı = z, ekran dikeyi = x
            float ballPxX = frcGameRebuilt.viewport.toPixelX(position.z);
            //noinspection SuspiciousNameCombination
            float ballPxY = frcGameRebuilt.viewport.toPixelY(position.x);
            //yükseldikçe daha büyük çiz ki oyuncu topun havada olduğunu anlasın
            float ballPxRadius = frcGameRebuilt.viewport.toPixelSize(RADIUS * (1f + HEIGHT_DRAW_SCALE * position.y));

            //topu ekrana daire olarak çiz
            canvas.drawCircle(ballPxX, ballPxY, ballPxRadius, ballPaint);
        }

        /** Topun yarı çapı */
        @Override public float getRadius(){ return RADIUS; }
        /** Yüzey merkezine bu kadar yakınsa (m) kayma yönü belirsizdir: dünyanın rastgelesinden bir yön seçilir */
        @Override public float getSlideCenterRadius(){ return 0.03f; }
        /** Bir yüzeyin (robot, engel) üstünde duran topun yüzeyin merkezinden uzağa kayma ivmesi (m/s²): üstte kalmaz */
        @Override public float getSurfaceSlideAccel(){return 4.0f; }
        /** Zemin üzerindeki yuvarlanma sürtünmesi */
        @Override public float getGroundFriction(){ return 3.0f; }
    }

    public class RobotRebuilt extends Physic3D.Robot{

        //bu karede robotun tepesine inen toplar (updateIntake işler)
        protected final ArrayList<FuelBall> landedOnTop = new ArrayList<>(); //burada tanımlamamın sebebi, robotlar her sezon yukarıdan yakıt alamayacak

        public RobotRebuilt(float startX, float startY, float startZ, float startHeading, float robotHeight, int intakeCapacity, boolean isBlueAlliance,
                            float shooterDelay, float shooterAngle, float shooterHeight){
            super(startX, startY, startZ, startHeading, robotHeight, intakeCapacity, isBlueAlliance, shooterDelay, shooterAngle, shooterHeight);
        }

        @Override
        protected void onEntityLanded(Physic3D.PhysicsEntity landed){
            if(!isTargetFuel(landed)) return;
            FuelBall ball = (FuelBall) landed;
            for(int i = 0; i < landedOnTop.size(); i++) if(landedOnTop.get(i) == ball) return;
            landedOnTop.add(ball);
        }

        @Override
        public void updateIntake(){
            if(world == null) return;

            //robotun TEPESİNE düşen yakıtlar: yer varsa intake açık olmasa da içeri alınır (örn. outpost'tan dökülen toplar).
            //Yer yoksa top tepede kalır ve kayganlıkla düşer. Aday yalnızca indiği kare için geçerlidir; üstünde duran top
            //her karede yeniden aday olduğu için robotun yeri açılırsa sonradan da alınır.
            for(int i = 0; i < landedOnTop.size(); i++){
                FuelBall fuel = landedOnTop.get(i);
                if(fuel.getWorld() != world || intakeCount >= stored.length) continue;
                if(world.removeEntity(fuel)){ //true = top bizim
                    fuel.velocity.setZero();
                    stored[intakeCount++] = fuel;
                }
            }
            landedOnTop.clear();

            super.updateIntake();
        }

        @Override
        protected void releaseAllIntakeFuels(){
            landedOnTop.clear();
            super.releaseAllIntakeFuels();
        }

        @Override
        public boolean isTargetFuel(Physic3D.PhysicsEntity e){
            return e instanceof FuelBall; //sadece topları al
        }

        @Override
        public float getFuelHalfSize(){
            return FuelBall.RADIUS;
        }

        @Override
        public boolean isPlayerRobot(){ return playerRobot == this; }

        @Override
        protected Viewport getViewport(){ return viewport; }
        @Override
        protected GamePanel getGamePanel(){ return gp; }

        @Override
        public RobotRebuilt setAutoScript(AutoActions.Script autoScript){
            super.setAutoScript(autoScript); return this;
        }

        //--------------------------------------SHOOTER--------------------------------------
        /** Namlunun robota göre azami top hızı (m/s). Hedef bunu gerektiriyorsa atış yapılamaz. TODO robota özel */
        public static final float MAX_SHOOT_SPEED = 12f;
        /** Atış hatası: yatay yönde (radyan) ve hızda (oran) standart sapma. 0 = kusursuz nişan. Robota özel ayarlanabilir.
         *  Rastgelelik dünyanın seed'li random'ından gelir (deterministik). */
        public float shootAngleSigma = (float)Math.toRadians(0.5f);
        public float shootSpeedSigma = 0.01f; //menzil ~ hız², yani %1 hız hatası ~%2 menzil hatası

        private static final float SHOT_MIN_TIME  = 0.05f; //uçuş süresi taraması (s)
        private static final float SHOT_MAX_TIME  = 3.0f;
        private static final float SHOT_SCAN_STEP = 0.02f;
        private static final float SHOT_MAX_GAUSS = 2.5f;  //hata dağılımının kuyruğunu kes (sigma cinsinden)

        /** solveShot'un sonucu topun dünya hızı */
        private final com.badlogic.gdx.math.Vector3 shotVelocity = new com.badlogic.gdx.math.Vector3();

        /**
         * Sabit atış açısıyla (shooterAngle) top, namludan (dx, dy, dz) kadar ötedeki hedefe İNERKEN ulaşsın diye gereken
         * dünya hızını bulur ve shotVelocity'ye yazar. Hedef bir noktadır (topun TABANI o yükseklikten geçer).
         * <br>
         * Nişan alma: shooter dönen bir taret gibidir (robotun yönünden bağımsız yatayda hedefe çevrilir), açısı sabittir; hızı ayarlanır.
         * Robot hareket halindeyse bu hesaba katılır (top robotun hızını devralır, taret buna göre öne/yana nişan alır).
         * <br>
         * Matematik: uçuş süresi T için topun dünya yatay hızı (D/T) olmalı; robota göre yatay hız w = |D/T - vRobot|, dikey hız
         * vy = w·tan(açı). Hedef yüksekliğinde olma koşulu: tan·|D - vRobot·T| - g/2·(T² + DT·T) - dy = 0 (DT, fizik adımı:
         * dünyadaki yerçekimi yarı-örtük Euler olduğu için g·DT·T/2 kadarlık ayrık düzeltme). Tek köklü olmak zorunda olmadığından
         * T taranır, işaret değişimleri ikiye bölmeyle inceltilir; kökler arasından top HEDEFE İNERKEN gelen ve robota göre
         * hızı MAX_SHOOT_SPEED'i aşmayan en yavaş atış seçilir.
         *
         * @return false: ulaşılamaz (çok uzak/yüksek, çok yakın, namlu hızı yetmez)
         */
        private boolean solveShot(float dx, float dy, float dz){
            final float cos = (float)Math.cos(shooterAngle), tan = (float)Math.tan(shooterAngle);

            boolean found = false;
            float bestSpeed = Float.MAX_VALUE;

            float prevT = SHOT_MIN_TIME;
            float prevF = shotError(dx, dy, dz, prevT, tan);
            for(float t = SHOT_MIN_TIME + SHOT_SCAN_STEP; t <= SHOT_MAX_TIME + 1e-4f; t += SHOT_SCAN_STEP){
                float f = shotError(dx, dy, dz, t, tan);

                if(prevF > 0f && f <= 0f){ //kök top hedef yüksekliğini bu aralıta geçiyor
                    float lo = prevT, hi = t;

                    for(int i = 0; i < 24; i++){
                        float mid = (lo + hi) * 0.5f;
                        if(shotError(dx, dy, dz, mid, tan) > 0f) lo = mid;
                        else hi = mid;
                    }
                    final float T = (lo + hi) * 0.5f;

                    float ex = dx - velocity.x * T, ez = dz - velocity.z * T;
                    float w = (float) Math.sqrt(ex * ex + ez * ez) / T; //robota göre yatay hız
                    float vy = w * tan;
                    float speed = w / cos;                              //robota göre toplam namlu hızı

                    //hedefe inerken gelmeli (yükselirken değil) ve namlu bu hızı verebilmeli
                    if(vy - Physic3D.GRAVITY * T < 0f && speed <= MAX_SHOOT_SPEED && speed < bestSpeed){
                        found = true;
                        bestSpeed = speed;
                        shotVelocity.set(dx / T, vy, dz / T);
                    }
                }
                prevT = t;
                prevF = f;
            }
            return found;
        }

        /** T anında topun hedef yüksekliğine göre yüksekliği (sıfır = hedefte). Bkz. solveShot. */
        private float shotError(float dx, float dy, float dz, float t, float tan){
            float ex = dx - velocity.x * t, ez = dz - velocity.z * t;
            return tan * (float) Math.sqrt(ex * ex + ez * ez)
                    - 0.5f * Physic3D.GRAVITY * (t * t + DT * t) - dy;
        }

        @Override
        public boolean canShoot(float targetX, float targetY, float targetZ){
            if(!super.canShoot(targetX, targetY, targetZ)) return false;
            return solveShot(targetX - position.x, targetY - (position.y + shooterHeight), targetZ - position.z);
        }

        @Override
        protected boolean launch(float dx, float dy, float dz){
            if(world == null || intakeCount <= 0) return false;
            if(!solveShot(dx, dy, dz)) return false;

            float vx = shotVelocity.x, vy = shotVelocity.y, vz = shotVelocity.z;

            //atış hatası (kapalıysa rastgele sayı tüketilemez)
            if(shootAngleSigma > 0f){
                float a = clampGauss((float) world.random.nextGaussian()) * shootAngleSigma;
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                float rx = vx * cos - vz * sin, rz = vx * sin + vz * cos;
                vx = rx;
                vz = rz;
            }
            if(shootSpeedSigma > 0f){
                float k = 1f + clampGauss((float) world.random.nextGaussian()) * shootSpeedSigma;
                vx *= k; vy *= k; vz *= k;
            }

            FuelBall ball = (FuelBall)stored[--intakeCount];
            stored[intakeCount] = null;
            ball.respawn(position.x, position.y + shooterHeight, position.z, vx, vy, vz);
            world.addEntity(ball);
            return true;
        }

        private float clampGauss(float v){
            return Math.max(-SHOT_MAX_GAUSS, Math.min(SHOT_MAX_GAUSS, v));
        }
    }
}
