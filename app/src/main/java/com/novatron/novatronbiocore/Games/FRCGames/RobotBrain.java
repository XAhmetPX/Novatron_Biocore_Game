package com.novatron.novatronbiocore.Games.FRCGames;

import java.util.Random;

/**
 * Botların teleop'taki karar vericisi.
 * Beyin hareketi kendisi yapmaz; karar verince robotun AutoActions kuyruğuna aksiyon ekler.
 * Her beyin ayrı dosyada bu sınıfı miras alır ve sadece decide() metodunu yazmak zorundadır.
 */
public abstract class RobotBrain{

    /** Tohumlu rastgelelik: aynı seed ile aynı maç tekrar oynatılabilir. */
    protected final Random rng;

    /** Kuyruk doluyken bile kaç saniyede bir "durum değişti mi?" kontrolü yapılsın (3-5 Hz). */
    protected float decisionInterval = 0.25f;

    /** Karar öncesi tepki gecikmesi aralığı (saniye). Anında tepki robotik görünür. */
    protected float reactionMin = 0.2f;
    protected float reactionMax = 0.8f;

    private float decisionTimer = 0f;
    private float reactionTimer = 0f;
    private boolean reacting = false;
    private boolean replanRequested = true;

    public RobotBrain(long seed){
        this.rng = new Random(seed);
    }

    // ---------------------------------------------------------------- Alt sınıfın yazacakları

    /** Yeni bir niyet seç ve robot.getActions().addAutoTask(...) ile kuyruğa ekle.
     * Çağrılmadan önce kuyruk temizlenmiş olur, yani sıfırdan karar verilir. */
    protected abstract void decide(Physic3D.Robot robot);

    /** İsteğe bağlı: her karede çağrılır. Kesinti tetikleyicilerini (hedef alındı, sıkıştı...) burada kontrol et. */
    protected void onUpdate(Physic3D.Robot robot, float dt){}

    /** İsteğe bağlı: maç/teleop başında çağrılır. Alt sınıf kendi durumunu sıfırlar. */
    protected void onReset(Physic3D.Robot robot){}

    // ---------------------------------------------------------------- Robotun çağıracakları

    /** Robot.update içinden, sadece bot (oyuncu olmayan) ve otonom bitmişken çağrılır. */
    public final void update(Physic3D.Robot robot, float dt){
        onUpdate(robot, dt);

        if(reacting){
            reactionTimer -= dt;
            if(reactionTimer <= 0f){
                reacting = false;
                robot.getActions().clear(robot); //yeni karar eskisini ezer
                decide(robot);
            }
            return;
        }

        decisionTimer -= dt;
        if(decisionTimer > 0f) return;
        decisionTimer = decisionInterval;

        if(replanRequested || robot.getActions().isEmpty()){
            replanRequested = false;
            reacting = true;
            reactionTimer = randomRange(reactionMin, reactionMax);
        }
    }

    /** Teleop başlarken çağır. */
    public final void reset(Physic3D.Robot robot){
        decisionTimer = 0f;
        reactionTimer = 0f;
        reacting = false;
        replanRequested = true;
        onReset(robot);
    }

    /** Kesinti: mevcut işi bırakıp (tepki gecikmesinden sonra) yeniden karar ver. */
    public final void requestReplan(){
        replanRequested = true;
    }

    // ---------------------------------------------------------------- Yardımcılar

    protected final float randomRange(float min, float max){
        return min + rng.nextFloat() * (max - min);
    }

    /** true olma olasılığı p (0..1). */
    protected final boolean chance(float p){
        return rng.nextFloat() < p;
    }

    /** [-amount, +amount] arası sapma. Hedef noktaya hata payı eklemek için. */
    protected final float noise(float amount){
        return (rng.nextFloat() * 2f - 1f) * amount;
    }

    /**
     * Puanlara göre ağırlıklı rastgele seçim (softmax). Seçilen indeksi döner.
     * temperature küçükse neredeyse hep en iyiyi seçer, büyükse daha rastgele olur.
     */
    protected final int pickSoftmax(float[] scores, float temperature){
        if(scores.length == 0) return -1;
        float t = Math.max(temperature, 1e-4f);
        float max = scores[0];
        for(float s : scores) if(s > max) max = s;

        float[] w = new float[scores.length];
        float sum = 0f;
        for(int i = 0; i < scores.length; i++){
            w[i] = (float) Math.exp((scores[i] - max) / t);
            sum += w[i];
        }

        float r = rng.nextFloat() * sum;
        for(int i = 0; i < w.length; i++){
            r -= w[i];
            if(r <= 0f) return i;
        }
        return w.length - 1;
    }
}