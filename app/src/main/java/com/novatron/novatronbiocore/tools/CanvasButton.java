package com.novatron.novatronbiocore.tools;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;

public class CanvasButton{
    private RectF bounds;
    private String label;
    private final Paint bgPaint;
    private final TextPaint textPaint;
    private OnClickListener upListener, downListener;

    private StaticLayout staticLayout;
    private boolean isPressed = false;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private boolean isEnabled = true;
    private int alphaValue = 0;

    public CanvasButton(float left, float top, float right, float bottom, String label, int color){
        this.label = label;

        this.bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.bgPaint.setColor(color);
        if(Color.alpha(color) < 250)
            alphaValue = Color.alpha(color);

        this.textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        this.textPaint.setColor(Color.WHITE);
        this.textPaint.setTextSize(40f);

        setBounds(left, top, right, bottom);
    }

    private void updateTextLayout(){
        int width = (int) bounds.width();
        if(width <= 0) return;

        //automatically wraps text into multiple lines if it exceeds button width
        staticLayout = StaticLayout.Builder.obtain(label, 0, label.length(), textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build();
    }

    public void setBounds(float left, float top, float right, float bottom){
        this.bounds = new RectF(left, top, right, bottom);
        updateTextLayout();
    }

    public void setLabel(String newLabel){
        this.label = newLabel;
        updateTextLayout();
    }

    public void setOnClickListener(OnClickListener upListener){
        this.upListener = upListener;
    }
    public void setOnDownListener(OnClickListener downListener){
        this.downListener = downListener;
    }

    public void setEnabled(boolean enabled){ this.isEnabled = enabled; }

    public void draw(Canvas canvas){
        //optional visual feedback on press
        int alpha = Math.max(alphaValue - ((isPressed || !isEnabled) ? 75 : 0), 10);
        bgPaint.setAlpha(alpha);

        //draw button box
        canvas.drawRoundRect(bounds, 16f, 16f, bgPaint);

        //draw wrapped multi-line text centered inside the button bounds
        if(staticLayout != null){
            canvas.save();
            float textHeight = staticLayout.getHeight();
            float offsetX = bounds.left;
            float offsetY = bounds.top + (bounds.height() - textHeight) / 2f;

            canvas.translate(offsetX, offsetY);
            staticLayout.draw(canvas);
            canvas.restore();
        }
    }

    public boolean handleTouch(MotionEvent event){
        if(!isEnabled) return false;
        //use getActionMasked() to detect multi-touch events correctly
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();
        int pointerId = event.getPointerId(actionIndex);

        switch(action){
            //first finger or any additional finger touches down
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if(activePointerId == MotionEvent.INVALID_POINTER_ID){
                    float x = event.getX(actionIndex);
                    float y = event.getY(actionIndex);

                    if(bounds.contains(x, y)){
                        activePointerId = pointerId;
                        isPressed = true;
                        if(downListener != null) downListener.onClick();
                        return true;
                    }
                }
                break;

            //movement of any active finger on screen
            case MotionEvent.ACTION_MOVE:
                if(activePointerId != MotionEvent.INVALID_POINTER_ID){
                    int pointerIndex = event.findPointerIndex(activePointerId);
                    if(pointerIndex != -1){
                        float x = event.getX(pointerIndex);
                        float y = event.getY(pointerIndex);
                        //update pressed visual state if finger slides out/in
                        isPressed = bounds.contains(x, y);
                    }
                }
                break;

            // A finger lifts off the screen
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if(pointerId == activePointerId){
                    float x = event.getX(actionIndex);
                    float y = event.getY(actionIndex);

                    if(bounds.contains(x, y) && upListener != null)
                        upListener.onClick();

                    //reset tracking state
                    activePointerId = MotionEvent.INVALID_POINTER_ID;
                    isPressed = false;
                    return true;
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                if(pointerId == activePointerId){
                    activePointerId = MotionEvent.INVALID_POINTER_ID;
                    isPressed = false;
                }
                break;
        }
        return false;
    }

    public void reset(){
        activePointerId = MotionEvent.INVALID_POINTER_ID;
        isPressed = false;
    }

    public interface OnClickListener{
        void onClick();
    }
}
