package com.novatron.novatronbiocore.tools;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;

public class FloatingDegreeStickN extends DegreeStickN{

    private RectF area;

    public FloatingDegreeStickN(){
        super(0,0);
        area = new RectF(0, 0, 0,0);
    }

    @Override
    public void draw(Canvas canvas){
        if(isDragging)
            super.draw(canvas);
    }

    @Override
    public boolean handleTouch(MotionEvent event){
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        if(action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN){
            if(!isDragging){
                float x = event.getX(actionIndex);
                float y = event.getY(actionIndex);

                if(area.contains(x, y)){
                    isDragging = true;
                    pointerID = event.getPointerId(actionIndex);
                    centerX = x; centerY = y;
                    updatePosition(x, y);
                    return true;
                }
            }
            return false;
        }
        return super.handleTouch(event);
    }

    public void setArea(float left, float top, float right, float bottom){
        setArea(new RectF(left, top, right, bottom));
    }
    public void setArea(RectF area){ this.area = area; }

    //------------------------GEÇERSİZ METOTLAR------------------------
    @Override public void setPosition(Position position){}
    @Override public void updateScreenSize(int width, int height){}
    @Override public void setPadding(int left, int top, int right, int bottom){}
}
