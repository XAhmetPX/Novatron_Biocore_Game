package com.novatron.novatronbiocore.tools;

public class Viewport{

    public enum Type{ FILL_THE_SCREEN, SHRINK_TO_FIT }
    public enum FillStyle{ CENTER, LEFT, RIGHT, TOP, BOTTOM }
    public enum Origin{ BOTTOM_LEFT, TOP_LEFT }

    private float pixelsPerMeter;
    private float offsetX, offsetY;
    private final float defaultWidth, defaultHeight;
    private int screenWidth, screenHeight;
    private Type type;
    private FillStyle fillStyle = FillStyle.CENTER;
    private Origin origin = Origin.BOTTOM_LEFT;

    public Viewport(float notScaledWidth, float notScaledHeight){
        this(notScaledWidth, notScaledHeight, Type.SHRINK_TO_FIT);
    }
    public Viewport(float notScaledWidth, float notScaledHeight, Type type){
        if(notScaledWidth <= 0 || notScaledHeight <= 0)
            throw new IllegalArgumentException("The ViewPort sizes must be greater than 0!");

        this.defaultWidth = notScaledWidth;
        this.defaultHeight = notScaledHeight;
        this.type = type;

        //ekrana henüz basılmadan önce default bir değerle başlat
        updateScreenSize((int)notScaledWidth, (int)notScaledHeight);
    }

    public void updateScreenSize(int width, int height){
        if(width <= 0 || height <= 0) return;

        this.screenWidth = width;
        this.screenHeight = height;

        float scaleX = (float)width / defaultWidth;
        float scaleY = (float)height / defaultHeight;
        this.pixelsPerMeter = (type == Type.SHRINK_TO_FIT) ? Math.min(scaleX, scaleY) : Math.max(scaleX, scaleY);
        //                                    sahayı ekrana sığdıracak ölçeği hesapla : ekranı dolduracak ölçeği hesapla


        switch(fillStyle){
            case CENTER:
                this.offsetX = (width - (defaultWidth * pixelsPerMeter)) / 2f;
                this.offsetY = (height - (defaultHeight * pixelsPerMeter)) / 2f;
                break;
            case TOP:
                this.offsetX = (width - (defaultWidth * pixelsPerMeter)) / 2f;
                this.offsetY = 0;
                break;
            case BOTTOM:
                this.offsetX = (width - (defaultWidth * pixelsPerMeter)) / 2f;
                this.offsetY = height - (defaultHeight * pixelsPerMeter);
                break;
            case LEFT:
                this.offsetX = 0;
                this.offsetY = (height - (defaultHeight * pixelsPerMeter)) / 2f;
                break;
            case RIGHT:
                this.offsetX = width - (defaultWidth * pixelsPerMeter);
                this.offsetY = (height - (defaultHeight * pixelsPerMeter)) / 2f;
                break;
        }

    }

    public void setType(Type newType){
        this.type = newType;
        updateScreenSize(screenWidth, screenHeight);
    }
    public void setFillStyle(FillStyle fillStyle){
        this.fillStyle = fillStyle;
        updateScreenSize(screenWidth, screenHeight);
    }
    public void setOrigin(Origin origin){ this.origin = origin; }
    public float toPixelX(float meterX){ return offsetX + (meterX * pixelsPerMeter); }
    public float toPixelY(float meterY){
        if(origin == Origin.BOTTOM_LEFT)
            meterY = (defaultHeight - meterY);
        return offsetY + (meterY * pixelsPerMeter);
    }
    public float toPixelSize(float meters){ return meters * pixelsPerMeter;}
    public float getPixelsPerMeter(){ return pixelsPerMeter; }

    public float toMeterX(float pixelX){ return (pixelX - offsetX) / pixelsPerMeter; }
    public float toMeterY(float pixelY) {
        float meterY = (pixelY - offsetY) / pixelsPerMeter;
        if(origin == Origin.BOTTOM_LEFT)
            meterY = defaultHeight - meterY;
        return meterY;
    }
}