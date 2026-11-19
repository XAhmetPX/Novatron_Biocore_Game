package com.novatron.novatronbiocore.tools;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

public class Joystick{

    public enum Position{ TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private Position position = Position.BOTTOM_LEFT;

    private Paint basePaint, stickPaint;
    private float outerRadius, innerRadius;
    private int paddingLeft=50, paddingTop=50, paddingRight=50, paddingBottom=50;
    private int screenWidth, screenHeight;
    protected float centerX, centerY, stickX, stickY;
    protected double valueX, valueY;

    protected boolean isDragging = false;
    protected int pointerID = -1; //çoklu dokunmada joystick'i tutan parmağın kimliği

    public Joystick(int screenWidth, int screenHeight){
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        //varsayılan yarıçap'ı ayarla
        setOuterRadius(150f);

        initPaints();
        calculateCenter();
    }

    private void initPaints(){
        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setColor(Color.parseColor("#44000000"));
        basePaint.setStyle(Paint.Style.FILL);

        stickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stickPaint.setColor(Color.parseColor("#FFFFFFFF"));
        stickPaint.setStyle(Paint.Style.FILL);
    }

    /* Ekran veya alan boyutu değiştiğinde güncellemesi için */
    public void updateScreenSize(int width, int height){
        this.screenWidth = width;
        this.screenHeight = height;
        calculateCenter();
    }

    public void setPosition(Position position){
        this.position = position;
        calculateCenter();
    }

    public void setPadding(int left, int top, int right, int bottom){
        this.paddingLeft = left;
        this.paddingTop = top;
        this.paddingRight = right;
        this.paddingBottom = bottom;
        calculateCenter();
    }

    public void setOuterRadius(float radius){
        this.outerRadius = radius;
        this.innerRadius = radius / 2.5f;
        calculateCenter();
    }

    private void calculateCenter(){
        switch(position){
            case TOP_LEFT:
                centerX = paddingLeft + outerRadius;
                centerY = paddingTop + outerRadius;
                break;

            case TOP_RIGHT:
                centerX = screenWidth - paddingRight - outerRadius;
                centerY = paddingTop + outerRadius;
                break;

            case BOTTOM_LEFT:
                centerX = paddingLeft + outerRadius;
                centerY = screenHeight - paddingBottom - outerRadius;
                break;

            case BOTTOM_RIGHT:
                centerX = screenWidth - paddingRight - outerRadius;
                centerY = screenHeight - paddingBottom - outerRadius;
                break;
        }

        //merkez yeniden hesaplandığında çubuğu merkeze koy
        if(!isDragging){
            stickX = centerX;
            stickY = centerY;
            valueX = 0.0;
            valueY = 0.0;
        }
    }

    public void draw(Canvas canvas){
        canvas.drawCircle(centerX, centerY, outerRadius, basePaint); //dış daire
        canvas.drawCircle(stickX, stickY, innerRadius, stickPaint); //hareketli iç daire
    }

    public boolean handleTouch(MotionEvent event){
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        switch(action){
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if(!isDragging){
                    float x = event.getX(actionIndex);
                    float y = event.getY(actionIndex);
                    float distance = getDistance(x, y);

                    //eğer dokunuş dış daire alanındaysa, joystick bu parmağı "sahiplenir"
                    if(distance <= outerRadius){
                        isDragging = true;
                        pointerID = event.getPointerId(actionIndex);
                        updatePosition(x, y, distance);
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
    protected float getDistance(float x, float y){
        return (float) Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));
    }

    protected void updatePosition(float x, float y, float distance){
        if(distance <= outerRadius){
            //sınırlar içindeyse parmağı takip et
            stickX = x;
            stickY = y;
        }
        else{
            //sınır dışına çıkıldıysa, daireyi maksimum sınırda (çizgi üzerinde) tut
            float ratio = outerRadius / distance;
            stickX = centerX + (x - centerX) * ratio;
            stickY = centerY + (y - centerY) * ratio;
        }

        valueX = (stickX - centerX) / outerRadius;
        valueY = (stickY - centerY) / outerRadius;
        //kayan nokta (float precision) hatalarını önlemek için sınırlandırma
        valueX = Math.max(-1.0, Math.min(1.0, valueX));
        valueY = Math.max(-1.0, Math.min(1.0, valueY));
    }

    public float getOuterRadius(){ return outerRadius; }
    public double getValueX(){ return valueX; }
    public double getValueY(){ return valueY; }
    public void reset(){
        isDragging = false;
        pointerID = -1;
        updatePosition(centerX, centerY, 0);
    }
}
