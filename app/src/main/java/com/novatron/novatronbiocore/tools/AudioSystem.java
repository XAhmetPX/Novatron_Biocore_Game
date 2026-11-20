package com.novatron.novatronbiocore.tools;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import java.util.HashMap;

public class AudioSystem{
    private final Context context;

    //müzik (mediaPlayer)
    private MediaPlayer mediaPlayer;
    private float musicVolume = 1.0f;
    private int currentLoopCount = 0;
    private int targetLoopCount = -1; //-1 = sonsüz döngü

    //ses efektleri (soundPool)
    private SoundPool soundPool;
    private final HashMap<Integer, Integer> soundMap;
    private float sfxVolume = 1.0f;

    public AudioSystem(Context context){
        this.context = context;

        //aynı anda oynatılabilecek maksimum ses efekti sayısı 10
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(audioAttributes)
                .build();

        soundMap = new HashMap<>();
    }

    //sonsuz döngü için
    public void loop(Integer musicResId){
        loop(musicResId, -1);
    }

    //belirli bir sayıda döndürmek için (times = istenilen tekrar sayısı)
    public void loop(Integer musicResId, int times){
        stopMusic(); //varsa çalan müziği durdur
        if(!com.novatron.novatronbiocore.Configs.USE_MUSIC) return;
        if(musicResId == null) return;

        targetLoopCount = times;
        currentLoopCount = 0;
        mediaPlayer = MediaPlayer.create(context, musicResId);

        if(mediaPlayer == null) return;
        mediaPlayer.setVolume(musicVolume, musicVolume);

        if(times == -1){
            //sonsuz döngü
            mediaPlayer.setLooping(true);
        }
        else{
            //belirli sayıda döngü
            mediaPlayer.setLooping(false);
            mediaPlayer.setOnCompletionListener(mp -> {
                currentLoopCount++;
                if(currentLoopCount < targetLoopCount) mp.start();
                else stopMusic();
            });
        }
        mediaPlayer.start();
    }

    private void stopMusic(){
        if(mediaPlayer == null) return;

        if(mediaPlayer.isPlaying()) mediaPlayer.stop();
        mediaPlayer.release();
        mediaPlayer = null;
    }

    public void pauseLoop(){
        if(mediaPlayer != null && mediaPlayer.isPlaying())
            mediaPlayer.pause();
    }
    public void startLoop(){
        if(mediaPlayer != null && !mediaPlayer.isPlaying())
            mediaPlayer.start();
    }

    //ses efektlerini oyun başlamadan önce RAM'e yüklemelisiniz
    public void loadSound(int sfxResId){
        int soundId = soundPool.load(context, sfxResId, 1);
        soundMap.put(sfxResId, soundId);
    }

    //yüklenen ses efektini oynatır
    public void playSound(int sfxResId){
        if(!soundMap.containsKey(sfxResId)) return;
        if(!com.novatron.novatronbiocore.Configs.USE_SOUND_EFFECT) return;
        int soundId = java.util.Objects.requireNonNull(soundMap.get(sfxResId));
        soundPool.play(soundId, sfxVolume, sfxVolume, 1, 0, 1.0f);
    }

    public void setMusicVolume(float volume){
        this.musicVolume = Math.max(0.0f, Math.min(1.0f, volume)); //sınırları koru
        if(mediaPlayer == null) return;
        mediaPlayer.setVolume(this.musicVolume, this.musicVolume);

    }
    public void setSfxVolume(float volume){
        this.sfxVolume = Math.max(0.0f, Math.min(1.0f, volume));
    }
    //Activity veya Fragment kapandığında hafızayı temizlemek için çağrılmalı
    public void release(){
        stopMusic();
        if(soundPool == null) return;
        soundPool.release();
        soundPool = null;
    }
}
