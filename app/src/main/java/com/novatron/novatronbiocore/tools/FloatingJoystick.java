package com.novatron.novatronbiocore.tools;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;

public class FloatingJoystick extends Joystick{

    private RectF area;

    public FloatingJoystick(){
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

        switch(action){
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if(!isDragging){
                    float x = event.getX(actionIndex);
                    float y = event.getY(actionIndex);

                    //eğer dokunuş belirlenmiş dokunma alanının içinde ise parmağı "sahiplen"
                    //ve merkezi basılan yer yap
                    if(area.contains(x, y)){
                        isDragging = true;
                        pointerID = event.getPointerId(actionIndex);
                        centerX = x;
                        centerY = y;
                        updatePosition(x, y, 0.0f);
                        return true;
                    }
                }
                return false;

            case MotionEvent.ACTION_MOVE:
                if(isDragging){
                    //joystick'i hareket ettiren parmağın index'ini bul
                    int pointerIndex = event.findPointerIndex(pointerID);
                    if(pointerIndex != -1){
                        float x = event.getX(pointerIndex);
                        float y = event.getY(pointerIndex);
                        updatePosition(x, y, getDistance(x, y));
                        return true;
                    }
                }
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                if(isDragging){
                    int id = event.getPointerId(actionIndex);
                    //kalkan parmak, bizim joystick'i kontrol eden parmak is sıfırla
                    if(id == pointerID){
                        reset();
                        return true;
                    }
                }
                return false;
        }
        return false;
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
