package com.novatron.novatronbiocore.tools;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;


public class VibrationManager{

    private final Vibrator vibrator;

    public VibrationManager(@NonNull Context context){
        Context appContext = context.getApplicationContext();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            VibratorManager vibratorManager =
                    (VibratorManager)appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            this.vibrator = (vibratorManager != null) ? vibratorManager.getDefaultVibrator() : null;
        }
        else{
            this.vibrator = (Vibrator)appContext.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    /** Checks if the device has a working vibration motor. */
    public boolean isVibrationSupported(){ return vibrator != null && vibrator.hasVibrator() && com.novatron.novatronbiocore.Configs.USE_VIBRATION; }

    /** Checks if the device supports custom amplitude/strength control. */
    public boolean hasAmplitudeControl(){
        return isVibrationSupported() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                vibrator.hasAmplitudeControl();
    }


    /** Light tick feedback. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public void vibrateTick(){
        if(!isVibrationSupported()) return;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
        else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createOneShot(10, 50));
        else
            vibrator.vibrate(10);
    }

    /** Standart UI click feedback. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public void vibrateClick(){
        if(!isVibrationSupported()) return;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
        else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE));
        else
            vibrator.vibrate(20);
    }

    /** Heavy click feedback. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public void vibrateHeavyClick(){
        if(!isVibrationSupported()) return;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
        else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createOneShot(40, 255));
        else
            vibrator.vibrate(40);
    }

    /** Double click feedback. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public void vibrateDoubleClick(){
        if(!isVibrationSupported()) return;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK));
        else
            vibrateWaveform(new long[]{0, 20, 40, 20}, new int[]{0, 180, 0, 180}, -1);
    }



    /** Crisp double-pulse for successful operations. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public void vibrateSuccess(){
        if(!isVibrationSupported()) return;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            vibrator.vibrate(VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 1.0f, 50)
                    .compose());
        }
        else{
            vibrateWaveform(new long[]{0, 30, 60, 50}, new int[]{0, 120, 0, 255}, -1);
        }
    }

    /** Distinct multi-pulse pattern for errors or validation failures. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public void vibrateError(){
        if(!isVibrationSupported()) return;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
            vibrator.vibrate(VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f, 60)
                    .compose());
        }
        else{
            vibrateWaveform(new long[]{0, 60, 40, 60, 40, 60}, new int[]{0, 255, 0, 200, 0, 150}, -1);
        }
    }

    /**
     * One-shot custom vibration with specific duration and strength.
     *
     * @param durationMs Duration in milliseconds.
     * @param amplitude  Strength from 1 to 255 (or VibrationEffect.DEFAULT_AMPLITUDE).
     */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public void vibrateOneShot(long durationMs, int amplitude){
        if(!isVibrationSupported()) return;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            int safeAmplitude = Math.max(1, Math.min(255, amplitude));
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, safeAmplitude));
        }
        else{
            vibrator.vibrate(durationMs);
        }
    }

    /**
     * Advanced custom waveform with alternating timings and amplitudes.
     *
     * @param timings    Array of durations (ms): [delay, vibrate, pause, vibrate...]
     * @param amplitudes Array of strengths (0-255) corresponding to timings.
     * @param repeat     Index to start looping at, or -1 for no repeat.
     */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public void vibrateWaveform(long[] timings, int[] amplitudes, int repeat){
        if(!isVibrationSupported()) return;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            if(hasAmplitudeControl() && amplitudes != null && amplitudes.length == timings.length){
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, repeat));
            }
            else{
                vibrator.vibrate(VibrationEffect.createWaveform(timings, repeat));
            }
        }
        else{
            vibrator.vibrate(timings, repeat);
        }
    }

    /** Immediately stops any ongoing vibration. */
    @RequiresPermission(android.Manifest.permission.VIBRATE)
    public void cancel(){ if(vibrator != null) vibrator.cancel(); }
}
