package com.novatron.novatronbiocore.Games;

import android.graphics.Canvas;
import android.view.MotionEvent;

public interface Game{

    enum State{ RUNNING, GAME_OVER }

    int SKY_COLOR = android.graphics.Color.parseColor("#84fffe");

    int getTime();
    int getCurrentScore();
    int getMaxScore();

    void preloadGame();
    void destroyGame();
    void updateScreenSize();
    void reset();

    State getState();
    int getLogoTextureID();

    void update();
    void draw(Canvas canvas);
    boolean handleTouch(MotionEvent event);

}
