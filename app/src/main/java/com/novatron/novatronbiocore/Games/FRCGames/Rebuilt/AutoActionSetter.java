package com.novatron.novatronbiocore.Games.FRCGames.Rebuilt;

import androidx.annotation.NonNull;

import com.novatron.novatronbiocore.Games.FRCGames.Physic3D;
import com.novatron.novatronbiocore.Games.FRCGames.AutoActions;

/**
 * FRC Rebuilt sezonundaki robotların otonom aksiyonlarını kuran metotlar. Her metot Robot.setAutoScript(...) ile bir robota bağlanır
 * (örn. robot.setAutoScript(AutoActionSetter::setRobot2Autonomous)) ve robot her sıfırlandığında çağrılır.
 * Aksiyonlar tek kullanımlıktır: her çağrıda new ile baştan oluşturulmalı (zaten böyle olur).
 * <br>
 * EKSENLER: koordinat çiftleri (x, z) şeklindedir. x = sahanın KISA kenarı (0..~8.23), z = UZUN kenarı (0..16.44).
 * Açılar: 0 = +x yönü, +x'ten +z'ye doğru artar. Yani +z (kırmızıdan maviye) = 90°, -z (maviden kırmızıya) = 270°.
 */
public final class AutoActionSetter{

    private AutoActionSetter(){}

    //--------------------------------KIRMIZI İTTİFAK-------------------------------
    public static void setRobot1Autonomous(@NonNull Physic3D.Robot robot){
        AutoActions actions = robot.getActions();

        //ortaya git
        actions.addAutoTask(new AutoActions.DriveBezierCurve(2f, false,
                7.40f, 3.60f,
                7.25f, 7.70f));
        //intake dolana kadar top topla
        actions.addAutoTask(new AutoActions.ParallelActionsWithMain(
                new AutoActions.IntakeUntilFull(),
                new AutoActions.DriveToPosition(4f, 7.70f, 3f, false)
        ));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true, 0.5f));
        //geri git
        actions.addAutoTask(new AutoActions.DriveBezierCurve(1.5f, false,
                4.90f, 7.80f,
                5.50f, 6.0f,
                5.90f, 2.80f));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true,1f));
        //hub'a intake boşalana kadar atış yap
        actions.addAutoTask(new AutoActions.Shoot(4.026f, 1.83f, 4.625f));
        //ortaya git
        actions.addAutoTask(new AutoActions.ParallelActions(
                new AutoActions.TurnToAngle((float)Math.toRadians(180)),
                new AutoActions.DriveBezierCurve(1.5f, false,
                        5.85f, 2.70f,
                        7.45f, 3.50f,
                        7.70f, 6.30f,
                        7.52f, 8.05f)
        ));
        //intake'i aç
        actions.addAutoTask(new AutoActions.SetIntakeState(true));
        //tüm topları kendi bölgene atmaya çalış
        actions.addAutoTask(new AutoActions.ParallelActions(
                new AutoActions.DriveToPosition(0.90f, 8.05f, 2.5f, false),
                new AutoActions.Shoot(robot.getIntakeCapacity()*2, 6.85f, 0, 3.10f)
        ));
    }

    public static void setRobot2Autonomous(@NonNull Physic3D.Robot robot){
        AutoActions actions = robot.getActions();

        //depoya git
        actions.addAutoTask(new AutoActions.DriveBezierCurve(1.1f, false,
                4.0f, 3.45f,
                7.25f, 1.90f,
                7.20f, Physic3D.Robot.ROBOT_SIZE/2f + 0.02f));
        //deponun hepsini topla
        actions.addAutoTask(new AutoActions.ParallelActionsWithMain(
                new AutoActions.WithTimeout(new AutoActions.IntakeUntilFull(), 1.4f),
                new AutoActions.DriveToPosition(5.15f, Physic3D.Robot.ROBOT_SIZE/2f + 0.02f, 1.3f, false)
        ));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true,0.2f));
        //geri git
        actions.addAutoTask(new AutoActions.DriveBezierCurve(1f, false,
                5.45f, 0.50f,
                5.10f, 2.0f,
                3.90f, 2.45f));
        if(((FRCGameRebuilt.RobotRebuilt)robot).shouldTurnTarget) //gerekiyorsa hedefe dön
            actions.addAutoTask(new AutoActions.TurnToPoint(4f, 4.6f));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true,0.3f));
        //hub'a intake boşalana kadar atış yap
        actions.addAutoTask(new AutoActions.Shoot(4.026f, 1.83f, 4.625f));
    }

    public static void setRobot3Autonomous(@NonNull Physic3D.Robot robot){
        AutoActions actions = robot.getActions();

        //orta git
        actions.addAutoTask(new AutoActions.DriveBezierCurve(2.2f, false,
                2.50f, 3.70f,
                2.60f, 5.50f,
                1.70f, 6.55f,
                0.60f, 6.60f,
                0.50f, 7.70f));
        //intake dolana kadar topla
        actions.addAutoTask(new AutoActions.ParallelActionsWithMain(
                new AutoActions.IntakeUntilFull(),
                new AutoActions.DriveToPosition(4f, 7.70f, 2.8f, false)
        ));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true, 0.2f));
        //geri git
        actions.addAutoTask(new AutoActions.DriveToPosition(2.40f, 2.60f, 3f, false));
        if(((FRCGameRebuilt.RobotRebuilt)robot).shouldTurnTarget) //gerekiyorsa hedefe dön
            actions.addAutoTask(new AutoActions.TurnToPoint(4f, 4.6f));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true,0.5f));
        //hub'a intake boşalana kadar atış yap
        actions.addAutoTask(new AutoActions.Shoot(4.026f, 1.83f, 4.625f));
        //geri ortaya git
        actions.addAutoTask(new AutoActions.ParallelActions(
                new AutoActions.DriveBezierCurve(2f, false,
                        2.45f, 2.70f,
                        2.40f, 5.55f,
                        1.30f, 6.90f),
                new AutoActions.TurnToAngle((float)Math.toRadians(0))
        ));
        //intake dolana kadar topla
        actions.addAutoTask(new AutoActions.ParallelActionsWithMain(
                new AutoActions.WithTimeout(new AutoActions.IntakeUntilFull(), 2f),
                new AutoActions.DriveToPosition(5.95f, 6.90f, 2f, false)
        ));
        //geri git
        actions.addAutoTask(new AutoActions.DriveBezierCurve(2.5f, false,
                4.10f, 6.85f,
                2.50f, 6.20f,
                2.40f, 4.00f,
                1.90f, 2.40f));
        if(((FRCGameRebuilt.RobotRebuilt)robot).shouldTurnTarget) //gerekiyorsa hedefe dön
            actions.addAutoTask(new AutoActions.TurnToPoint(4f, 4.6f));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true,0.3f));
        //hub'a intake boşalana kadar atış yap
        actions.addAutoTask(new AutoActions.Shoot(4.026f, 1.83f, 4.625f));
    }

    //----------------------------------MAVİ İTTİFAK----------------------------------
    public static void setRobot4Autonomous(@NonNull Physic3D.Robot robot){
        AutoActions actions = robot.getActions();

        //ortaya git
        actions.addAutoTask(new AutoActions.DriveToPosition(7.30f, 8.80f, 4f, false));
        //intake dolana kadar topla
        actions.addAutoTask(new AutoActions.ParallelActionsWithMain(
                new AutoActions.IntakeUntilFull(),
                new AutoActions.DriveToPosition(4f, 8.70f, 2f, false)
        ));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true, 0.2f));
        //geri git
        actions.addAutoTask(new AutoActions.DriveBezierCurve(2f, false,
                4.00f, 8.70f,
                5.90f, 8.65f,
                7.70f, 9.85f,
                7.85f, 11.60f,
                7.20f, 13.25f));
        if(((FRCGameRebuilt.RobotRebuilt)robot).shouldTurnTarget) //gerekiyorsa hedefe dön
            actions.addAutoTask(new AutoActions.TurnToPoint(4f, 11.815f));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true,0.1f));
        //hub'a intake boşalana kadar atış yap
        actions.addAutoTask(new AutoActions.Shoot(4.026f, 1.83f, 11.815f));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true, 1f));
        //ortaya git
        actions.addAutoTask(new AutoActions.ParallelActions(
                new AutoActions.TurnToAngle((float)Math.toRadians(180)),
                new AutoActions.DriveBezierCurve(1.2f, false,
                        7.25f, 13.30f,
                        7.55f, 10.20f,
                        7.50f, 8.80f)
        ));
        //intake dolana kadar top topla
        actions.addAutoTask(new AutoActions.ParallelActionsWithMain(
                new AutoActions.IntakeUntilFull(),
                new AutoActions.DriveToPosition(1.80f, 8.70f, 2f, false)
        ));
        //geri top atmak için dön
        actions.addAutoTask(new AutoActions.DriveBezierCurve(1.1f, true,
                3.90f, 8.80f,
                2.30f, 9.90f,
                2.40f, 13.75f));
        if(((FRCGameRebuilt.RobotRebuilt)robot).shouldTurnTarget) //gerekiyorsa hedefe dön
            actions.addAutoTask(new AutoActions.TurnToPoint(4f, 11.815f));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true,0.1f));
        //hub'a intake boşalana kadar atış yap
        actions.addAutoTask(new AutoActions.Shoot(4.026f, 1.83f, 11.815f));
    }

    public static void setRobot5Autonomous(@NonNull Physic3D.Robot robot){
        AutoActions actions = robot.getActions();

        //ortaya git
        actions.addAutoTask(new AutoActions.DriveBezierCurve(1.8f, false,
                5.50f, 12.70f,
                5.80f, 10.60f,
                4.00f, 10.00f));
        //intake dolana kadar topla
        actions.addAutoTask(new AutoActions.ParallelActionsWithMain(
                new AutoActions.WithTimeout(new AutoActions.IntakeUntilFull(), 1.3f),
                new AutoActions.DriveToPosition(4f, 8.30f, 2.5f, false)
        ));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true, 0.2f));
        //geri git
        actions.addAutoTask(new AutoActions.DriveBezierCurve(2.2f, false,
                4.00f, 8.30f,
                5.00f, 10.20f,
                5.95f, 12.10f,
                5.40f, 13.70f));
        if(((FRCGameRebuilt.RobotRebuilt)robot).shouldTurnTarget) //gerekiyorsa hedefe dön
            actions.addAutoTask(new AutoActions.TurnToPoint(4f, 11.815f));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true,0.5f));
        //hub'a intake boşalana kadar atış yap
        actions.addAutoTask(new AutoActions.Shoot(4.026f, 1.83f, 11.815f));
    }

    public static void setRobot6Autonomous(@NonNull Physic3D.Robot robot){
        AutoActions actions = robot.getActions();

        //ortaya git
        actions.addAutoTask(new AutoActions.DriveToPosition(0.60f, 8.80f, 4f, false));
        //intake dolana kadar topla
        actions.addAutoTask(new AutoActions.ParallelActionsWithMain(
                new AutoActions.IntakeUntilFull(),
                new AutoActions.DriveToPosition(4f, 8.70f, 2.5f, false)
        ));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true, 0.3f));
        //geri git
        actions.addAutoTask(new AutoActions.DriveBezierCurve(1.3f, false,
                2.70f, 8.70f,
                2.15f, 10.60f,
                2.40f, 14.00f));
        //biraz bekle
        actions.addAutoTask(new AutoActions.WaitTimeout(true,0.05f));
        //hub'a intake boşalana kadar atış yap
        actions.addAutoTask(new AutoActions.Shoot(4.026f, 1.83f, 11.815f));
        //depoya git
        actions.addAutoTask(new AutoActions.ParallelActions(
                new AutoActions.WithTimeout(new AutoActions.DriveToPosition(0.60f, FRCGameRebuilt.FIELD_DEPTH_METERS-Physic3D.Robot.ROBOT_SIZE/2f, 3f, false), 1.5f),
                new AutoActions.TurnToAngle((float)Math.toRadians(0))
        ));
        //depoyu toplarken tüm topları ateşle
        actions.addAutoTask(new AutoActions.ParallelActionsWithMain(
                new AutoActions.DriveToPosition(3.20f, FRCGameRebuilt.FIELD_DEPTH_METERS-Physic3D.Robot.ROBOT_SIZE/2f, 1.5f, false),
                new AutoActions.IntakeUntilFull(),
                new AutoActions.Shoot(robot.getIntakeCapacity(), 4.026f, 1.83f, 11.815f)
        ));
    }
}
