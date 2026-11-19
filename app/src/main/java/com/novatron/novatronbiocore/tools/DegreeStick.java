package com.novatron.novatronbiocore.tools;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

public class DegreeStick{

    public enum Position{ TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private Position position = Position.BOTTOM_LEFT;

    private final Paint basePaint, stickPaint;
    private float radius;
    private float radian;
    private int paddingLeft=50, paddingTop=50, paddingRight=50, paddingBottom=50;
    private int screenWidth, screenHeight;
    protected float centerX, centerY;
    private double value;

    protected boolean isDragging = false;
    protected int pointerID = -1;

    //ibrenin(okun) birden -1'den 1'e dönmemesi için
    private double currentDegree = 0.0; //ibrenin o anki gerçek ve sınırlandırılmış açısı
    private double prevRawDegree = 0.0; //parmağın bir önceki hamledeki ham açısı
    protected boolean isFirstTouch = true; //dokunmanın yeni başlayıp başlamadığını kontrol eder

    public DegreeStick(int screenWidth, int screenHeight){
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setColor(Color.parseColor("#44000000"));
        basePaint.setStyle(Paint.Style.FILL);

        stickPaint = new Paint();
        stickPaint.setColor(Color.parseColor("#44FFFFFF"));
        stickPaint.setStyle(Paint.Style.STROKE);
        stickPaint.setStrokeWidth(10f);

        setOuterRadius(150f);

        calculateCenter();
    }

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
        this.radius = radius;
        calculateCenter();
    }

    private void calculateCenter(){
        switch(position){
            case TOP_LEFT:
                centerX = paddingLeft + radius;
                centerY = paddingTop + radius;
                break;

            case TOP_RIGHT:
                centerX = screenWidth - paddingRight - radius;
                centerY = paddingTop + radius;
                break;

            case BOTTOM_LEFT:
                centerX = paddingLeft + radius;
                centerY = screenHeight - paddingBottom - radius;
                break;

            case BOTTOM_RIGHT:
                centerX = screenWidth - paddingRight - radius;
                centerY = screenHeight - paddingBottom - radius;
                break;
        }

        if(isDragging) return;
        value = 0.0;
        currentDegree = 0.0;
        radian = (float)Math.PI/2;
    }

    public void draw(Canvas canvas){
        float stopX = centerX + (float)(Math.cos(radian)*radius);
        float stopY = centerY - (float)(Math.sin(radian)*radius);
        canvas.drawCircle(centerX, centerY, radius, basePaint);
        canvas.drawLine(centerX, centerY, stopX, stopY, stickPaint);
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
                    float distance = (float)Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));

                    if(distance <= radius){
                        isDragging = true;
                        pointerID = event.getPointerId(actionIndex);
                        isFirstTouch = true;
                        updatePosition(x, y);
                        return true;
                    }
                }
                return false;

            case MotionEvent.ACTION_MOVE:
                if(isDragging){
                    int pointerIndex = event.findPointerIndex(pointerID);
                    if(pointerIndex == -1) return false;
                    float x = event.getX(pointerIndex);
                    float y = event.getY(pointerIndex);
                    updatePosition(x, y);
                    return true;
                }
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                if(isDragging){
                    int id = event.getPointerId(actionIndex);

                    if(id != pointerID) return false;
                    isDragging =  false;
                    pointerID = -1;

                    radian = (float)Math.PI/2;
                    value = 0.0f;
                    currentDegree = 0.0;
                    return true;
                }
                return false;
        }
        return false;
    }

    protected void updatePosition(float x, float y){
        float dx = x - centerX;
        float dy = centerY - y;

        //parmağın mevcut mutlak açısını bul (-180 ile 180 arası)
        float rawRadian = (float) Math.atan2(dy, dx);
        double rawDegree = 90.0 - Math.toDegrees(rawRadian);
        if(rawDegree > 180) rawDegree -= 360;

        if(isFirstTouch){
            //ilk dokunulduğunda ibreyi doğrudan parmağa yasla
            currentDegree = rawDegree;
            prevRawDegree = rawDegree;
            isFirstTouch = false;
        }
        else{
            //parmağın bir önceki konumuna göre ne kadar döndüğünü hesapla
            double delta = rawDegree - prevRawDegree;

            //eğer delta > 180 veya < -180 ise, alt dikiş (-180 / 180) sınırından geçilmiştir
            //bu zıplamayı engellemek için delta'yı düzelt
            if (delta > 180) delta -= 360;
            else if (delta < -180) delta += 360;

            //değişimi ibrenin kendi açısına ekle
            currentDegree += delta;

            //ibrenin dönüşünü -180 ile 180 arasına indirge
            if(currentDegree > 180.0) currentDegree = 180.0;
            if(currentDegree < -180.0) currentDegree = -180.0;

            //bir sonraki hesaplama için ham açıyı kaydet
            prevRawDegree = rawDegree;
        }

        value = currentDegree / 180.0;
        radian = (float) Math.toRadians(90.0 - currentDegree);
    }

    public float getRadius(){ return radius; }
    public double getDegree(){ return Math.toDegrees(radian); }
    public double getRadian(){ return radian; }
    public double getValue(){ return value; }
    public void reset(){
        value = 0.0;
        currentDegree = 0.0;
        radian = (float)Math.PI/2;
        pointerID = -1;
    }

}
