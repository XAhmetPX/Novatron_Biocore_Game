package com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.Brains;

import com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.FRCGameRebuilt;

/**
 * BotSkills kullanan beyinlerin ortak atası: robota bağlanınca (ilk kullanımda) kendi BotSkills'ini kurar ve takım arkadaşları
 * birbirinin seçtiği top bölgesi/atış noktasını (claim) görebilsin diye skills'i dışarı verir.
 * Kendi beynini yazan (BotSkills'i kullanan) herkes RobotBrain yerine bundan miras alabilir. */
public abstract class SkillBrain extends RobotBrain{
    private BotSkills skills;

    public SkillBrain(long seed){ super(seed); }

    /** Bu beynin robotuna ait skills */
    public final BotSkills getSkills(){ return skills; }

    /** Alt sınıf decide/onUpdate içince bunu çağırır, robota göre bir kez kurulur. */
    protected final BotSkills skills(FRCGameRebuilt.RobotRebuilt robot){
        if(skills == null) skills = new BotSkills(robot, rng);
        return skills;
    }

    @Override
    protected void onReset(FRCGameRebuilt.RobotRebuilt robot){
        if(skills != null) skills.clearClaims();
    }
}
