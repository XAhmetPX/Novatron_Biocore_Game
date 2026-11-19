package com.novatron.novatronbiocore.Games;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

import com.novatron.novatronbiocore.GamePanel;
import com.novatron.novatronbiocore.R;
import com.novatron.novatronbiocore.tools.FloatingDegreeStick;
import com.novatron.novatronbiocore.tools.FloatingJoystick;

public class TempGame implements Game{

    private final GamePanel gp;

    public TempGame(GamePanel gp){
        this.gp = gp;
    }

    @Override
    public void updateScreenSize(){}

    @Override
    public void update(){}

    @Override
    public void draw(Canvas canvas){
        canvas.drawColor(android.graphics.Color.GREEN);
    }

    @Override
    public boolean handleTouch(MotionEvent event){
        return false;
    }

    @Override
    public void preloadGame(){}
    @Override
    public void destroyGame(){}

    @Override
    public int getTime(){ return -1; }
    @Override
    public int getCurrentScore(){ return -1; }
    @Override
    public int getMaxScore(){ return 0; }
    @Override
    public State getState(){ return State.RUNNING; }
    @Override
    public int getLogoTextureID(){ return -1; }
    @Override
    public void reset(){}
}
