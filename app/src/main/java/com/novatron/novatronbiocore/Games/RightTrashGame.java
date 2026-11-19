package com.novatron.novatronbiocore.Games;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;

import com.novatron.novatronbiocore.GamePanel;
import com.novatron.novatronbiocore.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class RightTrashGame implements Game{

    private static final int[] IMAGE_IDS = {
            R.drawable.trash_plastic_0, R.drawable.trash_plastic_1, R.drawable.trash_plastic_2, R.drawable.trash_plastic_3,
            R.drawable.trash_plastic_4, R.drawable.trash_plastic_5, R.drawable.trash_plastic_bag,

            R.drawable.trash_metal_0, R.drawable.trash_metal_1, R.drawable.trash_metal_2, R.drawable.trash_metal_3,
            R.drawable.trash_metal_4, R.drawable.trash_metal_5, R.drawable.trash_metal_6,

            R.drawable.trash_paper_0, R.drawable.trash_paper_1, R.drawable.trash_paper_2, R.drawable.trash_paper_3,
            R.drawable.trash_paper_4,

            R.drawable.trash_glass_0, R.drawable.trash_glass_1, R.drawable.trash_glass_2, R.drawable.trash_glass_3,
            R.drawable.trash_glass_4, R.drawable.trash_glass_5,

            R.drawable.trash_organic_0, R.drawable.trash_organic_1, R.drawable.trash_organic_2, R.drawable.trash_organic_3,
            R.drawable.trash_organic_4, R.drawable.trash_organic_5, R.drawable.trash_organic_6, R.drawable.trash_organic_7,

            R.drawable.trash_other_0, R.drawable.trash_other_1, R.drawable.trash_other_2, R.drawable.trash_other_3,
            R.drawable.trash_other_4, R.drawable.trash_other_5, R.drawable.trash_other_6,
    };
    private static float MAX_TRASH_SPEED;
    private static final float MAX_TRASH_ROTATE_SPEED = 5;
    private static final byte PLUS_SCORE = 100;

    private final GamePanel gp;
    private State state;

    private volatile boolean isPreLoaded = false;

    List<Particle> particles;
    private TrashBin[] trashBins;
    private Trash[] trashes;
    private int trashPointer = 0;
    private Random r;

    private short score;
    private short time;

    public RightTrashGame(GamePanel gp){
        this.gp = gp;
        reset();
    }

    @Override
    public void updateScreenSize(){
        if(trashBins != null){
            float padding = 0.25f;
            float w = gp.tileSize*2.8f;
            float left = (gp.SCREEN_WIDTH - (trashBins.length * (w + padding) - padding))/2;
            int top = (int)(gp.SCREEN_HEIGHT - w*0.5f);
            for(TrashBin tb : trashBins){
                tb.hitbox.set((int)left, top, (int)(left+w), (int)(top+w));
                left += w+padding;
            }
        }

        MAX_TRASH_SPEED = gp.tileSize*0.025f;
    }

    @Override
    public void update(){
        if(!isPreLoaded) return;
        time--;
        if(time % 1200 == 0)
            trashPointer++;
        trashPointer = Math.min(trashPointer, trashes.length);
        for(int i = 0; i < trashPointer; i++){
            Trash t = trashes[i];
            if(!t.inScreen()) reuseTrash(t);
            t.update();

            //----------------------ÇARPIŞMA KONTROLÜ----------------------
            TrashBin closestBin = null;
            double minDistance = Double.MAX_VALUE;

            //en yakın çöp kutusunu bul
            for(TrashBin tb : trashBins){
                if(!Rect.intersects(t.hitbox, tb.hitbox)) continue;

                double distance = getDistanceBetweenRectangles(t.hitbox, tb.hitbox);

                if(distance < minDistance){
                    minDistance = distance;
                    closestBin = tb;
                }
            }

            if(closestBin == null) continue;

            int sX = closestBin.hitbox.left;
            int eX = closestBin.hitbox.right;
            int eY = (int)(closestBin.hitbox.top - gp.tileSize*0.75f);
            int sY = eY - gp.tileSize/5;
            if(t.trashType == closestBin.trashType){
                score += PLUS_SCORE;

                //particle üretme
                createParticles(R.drawable.particle_plus, sX, eX, sY, eY, 12, 200);
            }
            else{
                score -= PLUS_SCORE;
                gp.vibration.vibrateOneShot(150, 210);
                if(score < 0) score = 0;

                //particle üretme
                createParticles(R.drawable.particle_cross, sX, eX, sY, eY, 11, 180);
            }
            reuseTrash(t);

        }

        for(int i = particles.size()-1; i >= 0; i--){
            Particle p = particles.get(i);
            if(p.update())
                particles.remove(p);
        }

        if(time <= 0) state = State.GAME_OVER;
    }
    private void createParticles(int imgID, int sX, int eX, int sY, int eY,int maxCount, int maxLife){
        int w = gp.tileSize/5;
        int count = r.nextInt(maxCount)+1;
        for(int i = 0; i < count; i++){
            int x = r.nextInt(eX - sX) + sX;
            int y = r.nextInt(eY - sY) + sY;
            particles.add(new Particle(imgID, x, y, r.nextInt(maxLife)+1, w));
        }
    }
    private void reuseTrash(Trash t){
        float x = (r.nextFloat()*(gp.SCREEN_WIDTH - gp.tileSize*2) + gp.tileSize);
        float y = -(r.nextFloat()*gp.tileSize*4) - gp.tileSize*2;
        float a = r.nextFloat()*MAX_TRASH_SPEED+2;
        float degree = r.nextFloat()*360, degreeA = r.nextFloat()*MAX_TRASH_ROTATE_SPEED;
        t.reuse(IMAGE_IDS[r.nextInt(IMAGE_IDS.length)], x, y, a, degree, degreeA);
        setSizes(t, gp);
    }
    private static double getDistanceBetweenRectangles(Rect rect1, Rect rect2){
        //merkez koordinatlarını al
        float centerX1 = rect1.exactCenterX();
        float centerY1 = rect1.exactCenterY();

        float centerX2 = rect2.exactCenterX();
        float centerY2 = rect2.exactCenterY();

        //uzaklığı Math.hypot ile hesapla
        return Math.hypot(centerX1 - centerX2, centerY1 - centerY2);
    }

    @Override
    public void draw(Canvas canvas){
        if(!isPreLoaded) return;
        canvas.drawColor(SKY_COLOR);

        for(int i = 0; i < trashPointer; i++){
            Trash t = trashes[i];
            if(t.inScreen())
                t.draw(canvas);
        }

        for(Particle p : particles){
            int w = (int)p.width;
            int x = p.x - w/2;
            int y = p.y - w/2;
            gp.imageStore.drawScaledPixelImageWithAlpha(canvas, p.imgID, x, y, w, w, (int)p.alpha);
        }

        for(TrashBin tb : trashBins)
            gp.imageStore.drawScaledPixelImage(canvas, tb.imgID, tb.hitbox.left, tb.hitbox.top - gp.tileSize*1.5f, tb.hitbox.width(), tb.hitbox.height());

    }

    @Override
    public boolean handleTouch(MotionEvent event){
        if(!isPreLoaded) return false;
        boolean isConsumed = false;
        for(int i = 0; i < trashPointer; i++){
            Trash t = trashes[i];
            if(t.handleTouch(event))
                isConsumed = true;
        }

        return isConsumed;
    }

    @Override
    public void preloadGame(){
        if(isPreLoaded) return;
        //-------------------------ÇÖP KUTULARI-------------------------
        trashBins = new TrashBin[]{
                new TrashBin(R.drawable.trash_bin_plastic, Type.PLASTIC),
                new TrashBin(R.drawable.trash_bin_paper, Type.PAPER),
                new TrashBin(R.drawable.trash_bin_glass, Type.GLASS),
                new TrashBin(R.drawable.trash_bin_metal, Type.METAL),
                new TrashBin(R.drawable.trash_bin_organic, Type.ORGANIC),
                new TrashBin(R.drawable.trash_bin_other, Type.OTHER),
        };
        for(TrashBin tb : trashBins)
            gp.imageStore.preloadImage(tb.imgID);

        //----------------ÇÖPLER----------------
        trashes = new Trash[5];
        for(int i = 0; i < trashes.length; i++)
            trashes[i] = new Trash();
        for(int id : IMAGE_IDS)
           gp.imageStore.preloadImage(id);

        //--------------DİĞERLERİ--------------
        r = new Random();
        particles = new ArrayList<>();
        gp.imageStore.preloadImage(R.drawable.particle_plus);
        gp.imageStore.preloadImage(R.drawable.particle_cross);

        isPreLoaded = true;
    }
    @Override
    public void destroyGame(){
        if(!isPreLoaded) return;
        //---------------ÇÖP kUTULARI---------------
        if(trashBins != null){
            for(int i = 0; i < trashBins.length; i++){
                gp.imageStore.removeImage(trashBins[i].imgID);
                trashBins[i] = null;
            }
            trashBins = null;
        }

        //--------------ÇÖPLER--------------
        if(trashes != null)
            Arrays.fill(trashes, null);
        trashes = null;

        for(int id : IMAGE_IDS)
            gp.imageStore.removeImage(id);

        //--------------DİĞERLERİ--------------
        r = null;
        particles.clear();
        particles = null;
        gp.imageStore.removeImage(R.drawable.particle_plus);
        gp.imageStore.removeImage(R.drawable.particle_cross);

        isPreLoaded = false;
    }
    @Override
    public void reset(){
        if(!isPreLoaded) return;
        state = State.RUNNING;
        score = 0;
        time = 7200;
        trashPointer = 1;
        if(particles != null)
            particles.clear();
        if(trashes != null)
            for(Trash t : trashes)
                t.removeInScreen();

    }

    @Override
    public int getTime(){ return time/60; }
    @Override
    public int getCurrentScore(){ return score; }
    @Override
    public int getMaxScore(){ return 0; }
    @Override
    public State getState(){ return state; }
    @Override
    public int getLogoTextureID(){ return R.drawable.logo_throw_to_right_trash; }

    private enum Type{ PLASTIC, PAPER, GLASS, METAL, ORGANIC, OTHER }

    private static final class TrashBin{
        public final int imgID;
        public final Rect hitbox;
        public final Type trashType;
        TrashBin(int imgID, Type trashType){
            this.trashType = trashType;
            this.imgID = imgID;
            hitbox = new Rect();
        }
    }

    private final class Trash{
        int imgID;
        final Rect hitbox = new Rect();
        Type trashType;
        float x, y, a;
        float degree, da;
        int pointerID = -1;
        int imgSize, hitboxSize;

        Trash(){
            removeInScreen();
        }

        void reuse(int imgID, float x, float y, float a, float degree, float degreeA){
            pointerID = -1;
            this.imgID = imgID;
            this.x = x; this.y = y; this.a = a;
            this.degree = degree; this.da = degreeA;
            setHitboxPosition();
        }

        void update(){
            y += a;
            setHitboxPosition();
            degree += da;
        }

        private void setHitboxPosition(){
            int left = (int)(x - hitboxSize/2f);
            int top = (int)(y - hitboxSize/2f);
            hitbox.set(left, top, left+hitboxSize, top+hitboxSize);
        }

        boolean inScreen(){
            float left = x - imgSize/2f;
            float top = y - imgSize/2f;
            float right = left + imgSize;
            //yukarıda olma kontrolü yapmadık çünkü zaten başta yukarıdan düşecek
            return !(right < 0 || left > gp.SCREEN_WIDTH || top > gp.SCREEN_HEIGHT);
        }

        void removeInScreen(){ x = -1000; }
        boolean isDragging(){ return pointerID != -1; }

        boolean handleTouch(MotionEvent event){
            int action = event.getActionMasked();
            int actionIndex = event.getActionIndex();

            switch(action){
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if(!isDragging()){
                        int x = (int) event.getX(actionIndex);
                        int y = (int) event.getY(actionIndex);
                        if (hitbox.contains(x, y)) {
                            pointerID = event.getPointerId(actionIndex);
                            this.x = x;
                            this.y = y;
                            this.da = 0;
                            return true;
                        }
                    }
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if(isDragging()){
                        //joystick'i hareket ettiren parmağın index'ini bul
                        int pointerIndex = event.findPointerIndex(pointerID);
                        if(pointerIndex == -1) return false;

                        this.x = (int) event.getX(pointerIndex);
                        this.y = (int) event.getY(pointerIndex);
                        return true;
                    }
                    return false;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    if(isDragging()){
                        int id = event.getPointerId(actionIndex);
                        if(id != pointerID) return false;
                        pointerID = -1;
                        setHitboxPosition();
                        return true;
                    }
                    return false;
            }
            return false;
        }

        void draw(Canvas canvas){
            canvas.save();
            canvas.rotate(degree, x, y);


            int left = (int) (x - imgSize / 2f);
            int top = (int) (y - imgSize / 2f);

            gp.imageStore.drawScaledPixelImage(canvas, imgID, left, top, imgSize, imgSize);

            canvas.restore();
        }
    }

    private static final class Particle{
        private short life;
        final int x, y;
        final int imgID;
        final float width;
        float alpha = 255.0f;
        private final float alphaDelta;
        Particle(int imgID, int x, int y, int life, int width){
            this.imgID = imgID;
            this.x = x;
            this.y = y;
            this.life = (short)life;
            this.width = width;
            alphaDelta = alpha/Math.max((life - 20), 1f);
        }

        boolean update(){
            life--;
            alpha -= alphaDelta;
            return life <= 0 || width <= 0 || alpha <= 0;
        }
    }

    static void setSizes(final Trash t, final GamePanel gp){
        int i = t.imgID;

        //----------------------------------PLASTIC----------------------------------
        if(i == R.drawable.trash_plastic_0){
            t.trashType = Type.PLASTIC;
            t.hitboxSize = (int)(gp.tileSize*0.6);
        }
        else if(i == R.drawable.trash_plastic_1){
            t.trashType = Type.PLASTIC;
            t.hitboxSize = (int)(gp.tileSize*0.68);
        }
        else if(i == R.drawable.trash_plastic_2){
            t.trashType = Type.PLASTIC;
            t.hitboxSize = (int)(gp.tileSize*0.7);
        }
        else if(i == R.drawable.trash_plastic_3){
            t.trashType = Type.PLASTIC;
            t.hitboxSize = (int)(gp.tileSize*0.75);
        }
        else if(i == R.drawable.trash_plastic_4 || i == R.drawable.trash_plastic_bag){
            t.trashType = Type.PLASTIC;
            t.hitboxSize = (int)(gp.tileSize*0.8);
        }
        else if(i == R.drawable.trash_plastic_5){
            t.trashType = Type.PLASTIC;
            t.hitboxSize = gp.tileSize;
        }
        //-----------------------------------METAL-----------------------------------
        else if(i == R.drawable.trash_metal_0){
            t.trashType = Type.METAL;
            t.hitboxSize = (int)(gp.tileSize*0.67);
        }
        else if(i == R.drawable.trash_metal_1){
            t.trashType = Type.METAL;
            t.hitboxSize = (int)(gp.tileSize*0.67);
        }
        else if(i == R.drawable.trash_metal_2){
            t.trashType = Type.METAL;
            t.hitboxSize = gp.tileSize;
        }
        else if(i == R.drawable.trash_metal_3){
            t.trashType = Type.METAL;
            t.hitboxSize = (int)(gp.tileSize*0.85);
        }
        else if(i == R.drawable.trash_metal_4){
            t.trashType = Type.METAL;
            t.hitboxSize = (int)(gp.tileSize*0.85);
        }
        else if(i == R.drawable.trash_metal_5){
            t.trashType = Type.METAL;
            t.hitboxSize = (int)(gp.tileSize*0.6);
        }
        else if(i == R.drawable.trash_metal_6){
            t.trashType = Type.METAL;
            t.hitboxSize = (int)(gp.tileSize*0.68);
        }
        //-----------------------------------PAPER-----------------------------------
        else if(i == R.drawable.trash_paper_0){
            t.trashType = Type.PAPER;
            t.hitboxSize = (int)(gp.tileSize*0.7);
        }
        else if(i == R.drawable.trash_paper_1){
            t.trashType = Type.PAPER;
            t.hitboxSize = (int)(gp.tileSize*0.8);
        }
        else if(i == R.drawable.trash_paper_2){
            t.trashType = Type.PAPER;
            t.hitboxSize = (int)(gp.tileSize*0.65);
        }
        else if(i == R.drawable.trash_paper_3){
            t.trashType = Type.PAPER;
            t.hitboxSize = gp.tileSize;
        }
        else if(i == R.drawable.trash_paper_4){
            t.trashType = Type.PAPER;
            t.hitboxSize = (int)(gp.tileSize*0.68);
        }
        //-----------------------------------GLASS-----------------------------------
        else if(i == R.drawable.trash_glass_0){
            t.trashType = Type.GLASS;
            t.hitboxSize = (int)(gp.tileSize*0.7);
        }
        else if(i == R.drawable.trash_glass_1){
            t.trashType = Type.GLASS;
            t.hitboxSize = (int)(gp.tileSize*0.68);
        }
        else if(i == R.drawable.trash_glass_2){
            t.trashType = Type.GLASS;
            t.hitboxSize = (int)(gp.tileSize*0.7);
        }
        else if(i == R.drawable.trash_glass_3){
            t.trashType = Type.GLASS;
            t.hitboxSize = (int)(gp.tileSize*0.7);
        }
        else if(i == R.drawable.trash_glass_4){
            t.trashType = Type.GLASS;
            t.hitboxSize = (int)(gp.tileSize*0.6);
        }
        else if(i == R.drawable.trash_glass_5){
            t.trashType = Type.GLASS;
            t.hitboxSize = (int)(gp.tileSize*0.8);
        }
        //----------------------------------ORGANIC----------------------------------
        else if(i == R.drawable.trash_organic_0 || i == R.drawable.trash_organic_2){
            t.trashType = Type.ORGANIC;
            t.hitboxSize = (int)(gp.tileSize*0.65);
        }
        else if(i == R.drawable.trash_organic_1){
            t.trashType = Type.ORGANIC;
            t.hitboxSize = (int)(gp.tileSize*0.75);
        }
        else if(i == R.drawable.trash_organic_3){
            t.trashType = Type.ORGANIC;
            t.hitboxSize = (int)(gp.tileSize*0.8);
        }
        else if(i == R.drawable.trash_organic_4 || i == R.drawable.trash_organic_5 || i == R.drawable.trash_organic_6){
            t.trashType = Type.ORGANIC;
            t.hitboxSize = (int)(gp.tileSize*0.6);
        }
        else if(i == R.drawable.trash_organic_7){
            t.trashType = Type.ORGANIC;
            t.hitboxSize = gp.tileSize;
        }
        //------------------------------------OTHER------------------------------------
        else if(i == R.drawable.trash_other_0
                || i == R.drawable.trash_other_5 || i == R.drawable.trash_other_6){
            t.trashType = Type.OTHER;
            t.hitboxSize = gp.tileSize;
        }
        else if(i == R.drawable.trash_other_1){
            t.trashType = Type.OTHER;
            t.hitboxSize = (int)(gp.tileSize*0.6);
        }
        else if(i == R.drawable.trash_other_2){
            t.trashType = Type.OTHER;
            t.hitboxSize = (int)(gp.tileSize*0.7);
        }
        else if(i == R.drawable.trash_other_3 || i == R.drawable.trash_other_4){
            t.trashType = Type.OTHER;
            t.hitboxSize = (int)(gp.tileSize*0.9);
        }

        else{
            t.trashType = Type.OTHER;
            t.hitboxSize = (int)(gp.tileSize*0.7);
        }
        t.imgSize = gp.tileSize;
        t.setHitboxPosition();
    }
}