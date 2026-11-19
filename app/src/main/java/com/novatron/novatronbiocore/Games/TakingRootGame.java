package com.novatron.novatronbiocore.Games;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.novatron.novatronbiocore.Configs;
import com.novatron.novatronbiocore.GamePanel;
import com.novatron.novatronbiocore.R;
import com.novatron.novatronbiocore.tools.CanvasButton;
import com.novatron.novatronbiocore.tools.FloatingJoystick;
import com.novatron.novatronbiocore.tools.Joystick;
import com.novatron.novatronbiocore.tools.Viewport;

public class TakingRootGame implements Game{

    private static final int[] imgIDs = new int[]{
            R.drawable.rooting_oxygen, R.drawable.rooting_water,
            R.drawable.rooting_salt, R.drawable.rooting_rock
    };

    private static final int WIDTH = 250;
    private static final int HEIGHT = 100;
    private static final int REEL_HEIGHT = 150;

    private static final float DEFAULT_SPEED = 8.0f / 60f;
    private static final int ROOT_COLOR = Color.parseColor("#63492a");
    //animation
    private static final int ANIMATION_COLOR = Color.WHITE;
    private static final float ANIMATION_TP_START_RADIUS = 9.5f;
    private static final float ANIMATION_TP_RADIUS_SMALLING_SPEED = 18 / 60f;

    private static final int MAX_NO_ROOT_CHANGE_TIME = 2400; //30 saniye
    private static final int ROOT_CHANGE_WAIT_TIME = 570; //5 saniye
    private static final int TOLERANCE_FRAMES = 2;

    private final GamePanel gp;
    private final Viewport viewport;
    private Joystick joystick;
    private CanvasButton btnNewRoot, btnGoToMarked, btnMark;

    //pixel verilerini tutacak buffer Bitmap ve Rect
    private Bitmap rootBitmap, animationBitmap;
    private Rect srcRect;
    private RectF dstRect, rootArea;
    private short[] actives;
    private int activesLength;

    //paintler
    private Paint bitmapPaint;

    private int lastI;
    private int markedI = -1;
    private float x, y;
    //rastgele bir köke ışınlanma
    private int noNewRootCounter = 0;
    private java.util.Random r;
    //animasyon(yanıp sönme)
    private int animationCounter = 0;
    private boolean inAnimation = false;
    //animasyon(ışınlanma çemberi)
    private float animationRadius = -1f;
    private int tpX, tpY;
    //animasyon(kök geri çekilme)
    private int animationBackCount = -1;
    private int animationBackCounter = 0;
    //yeniden aşınlanma
    private int newRootCounter = 0;
    //kökte hızlı gitme
    private int offRootFrames;

    private int time, score, maxScore;
    private State state;
    private boolean isPreloaded = false;


    private TakingRootGame.Object[] objects;

    public TakingRootGame(GamePanel gp){
        this.gp = gp;
        this.viewport = new Viewport(WIDTH, REEL_HEIGHT, Viewport.Type.FILL_THE_SCREEN);
        viewport.setFillStyle(Viewport.FillStyle.BOTTOM);
        viewport.setOrigin(Viewport.Origin.TOP_LEFT);

    }

    @Override
    public void updateScreenSize(){
        viewport.updateScreenSize(gp.SCREEN_WIDTH, gp.SCREEN_HEIGHT);

        final float tileSize = gp.tileSize * Configs.UI_SCALE;

        if(joystick != null){
            joystick.setOuterRadius(tileSize * 1.7f);
            if(joystick instanceof FloatingJoystick){
                FloatingJoystick js = (FloatingJoystick) joystick;
                js.setArea(0, 0, gp.SCREEN_WIDTH/2.0f, gp.SCREEN_HEIGHT);
            }
        }

        float btnWidth = tileSize*1.7f;
        float padding = tileSize/2.0f;
        float left = gp.SCREEN_WIDTH - btnWidth - padding;
        float top  = gp.SCREEN_HEIGHT - btnWidth - padding;
        if(btnNewRoot != null)
            btnNewRoot.setBounds(left, top, left+btnWidth, top+btnWidth);

        top -= (padding + btnWidth);
        if(btnMark != null)
            btnMark.setBounds(left, top, left+btnWidth, top+btnWidth);

        top -= (padding + btnWidth);
        if(btnGoToMarked != null)
            btnGoToMarked.setBounds(left, top, left+btnWidth, top+btnWidth);
    }

    @Override
    public void update(){
        if(joystick == null || objects == null) return;

        //ışınlanma dairesi animasyonu
        if(animationRadius > 0){
            animationRadius -= ANIMATION_TP_RADIUS_SMALLING_SPEED;
            if(animationRadius <= 0){
                this.x = tpX + 0.5f;
                this.y = tpY + 0.5f;
                animationRadius = -1;
            }
            return;
        }

        //kökü geri çekme animasyonu
        if(animationBackCount > 0){
            animationBackCounter--;
            if(animationBackCounter < 1){
                animationBackCounter = 6;
                animationBackCount--;

                //mevcut kök pikselini sil
                rootBitmap.setPixel((int)this.x, (int)this.y, Color.TRANSPARENT);

                if(markedI == (int)this.y * WIDTH + (int)this.x){
                    //eğer mevcut kök işaretli ise işareti kaldır
                    //çünkü bu kök silenecek
                    markedI = -1;
                    if(btnGoToMarked != null)
                        btnGoToMarked.setEnabled(false);
                }
                //bir önceki kök pikseline geç
                int i = actives[(activesLength--) - 1];
                this.x = (i % WIDTH) + 0.5f;
                this.y = (int)(i / WIDTH) + 0.5f;
                gp.vibration.vibrateOneShot(25, 30);
            }
            return;
        }

        float CURRENT_SPEED = DEFAULT_SPEED;

        //kök üzerinde daha hızlı hareket etsin,
        //bu oyuncunun kökleri kalınlaştırmasını sağlar
        if(offRootFrames <= TOLERANCE_FRAMES)
            CURRENT_SPEED *= 1.50f;

        x += (float)(joystick.getValueX() * CURRENT_SPEED);
        y += (float)(joystick.getValueY() * CURRENT_SPEED);

        //imleçi sınıfların içinde tut
        x = Math.max(0, Math.min(x, WIDTH-1));
        y = Math.max(0, Math.min(y, HEIGHT-1));

        int px = (int)x, py = (int)y;
        int currentI = py * WIDTH + px;

        if(currentI != lastI){
            if(rootBitmap.getPixel(px, py) != Color.TRANSPARENT){
                offRootFrames = 0;
            }
            else{
                offRootFrames++;

                rootBitmap.setPixel(px, py, ROOT_COLOR);
                //short olarak yazıyorum çünkü maks 25000 olacak ve short zaten ~32000 tutabiliyor
                actives[activesLength++] = (short)currentI;
            }
        }
        lastI = currentI;

        //rastgele bir köke ışınlanma
        noNewRootCounter++;
        if(noNewRootCounter >= MAX_NO_ROOT_CHANGE_TIME){
            tpRandomRoot();
            noNewRootCounter = 0;
        }

        //yanıp sönme animasyonu
        animationCounter++;
        if(animationCounter % 20 == 0){
            animationCounter = 0;
            inAnimation = !inAnimation;
        }

        //yeniden kök salma beklemesi
        newRootCounter++;
        if(newRootCounter == ROOT_CHANGE_WAIT_TIME)
            btnNewRoot.setEnabled(true);

        for(TakingRootGame.Object o : objects){
            if(o == null) continue;

            //eğer ekranın içinde değilse
            if(o.isNotInScreen()){
                if(setObjectToRandomPlace(o)) continue;
                else return; //obje konulacak köksüz yer kalmadı, oyunu bitir
            }

            //eğer köke temas varsa
            if(isObjectTouchToRoot(o)){
                if(isHealthyObject(o.imgID)){
                    //eğer yararlı nesne ise
                    score += 50;
                }
                else{
                    //eğer zararlı nesne ise
                    score -= 100;
                    animationBackCount = 22;
                    animationBackCounter = 40;
                    inAnimation = true;
                    gp.vibration.vibrateOneShot(150, 210);

                }

                do{ //her hangi bir köke değmeyene kadar küçült
                    o.reducingSize(0.95f);
                }while(isObjectTouchToRoot(o));
            }
            //yaşam süresi dolduğunda gittikçe küçül
            else{
                o.life--;
                if(o.life < 1)
                    o.reducingSize(0.999f);
            }

            //eğer obje yeterince küçüldüyse nesneyi sil
            if(Math.max(o.hitbox.height(), o.hitbox.width()) < 3f)
                o.removeInScreen();
        }

        score = Math.max(score, 0);

        time--;
        if(time < 1) state = State.GAME_OVER;
    }
    private boolean setObjectToRandomPlace(TakingRootGame.Object o){
        int counter = 0;
        //iyi olanlar ağırlıklı olacak şekilde id seç
        int i = r.nextInt(2);
        int id = imgIDs[(r.nextInt(4) == 0) ? i+2 : i];

        int life = 780 + (int)(r.nextFloat() * 1020);
        int size = 8;
        do{
            o.set(id, r.nextFloat()*WIDTH, r.nextFloat()*HEIGHT, size, size, life);
            counter++;
            if(counter < 3_500) continue;

            //eğer üç bin beş yüzden fazla denendiyse muhtemelen
            //oluşacak yer kalmamıştır, oyunu bitir.
            state = State.GAME_OVER;
            return false;
        }while(isObjectTouchToRoot(o) || o.isNotInScreen() || RectF.intersects(rootArea, o.hitbox));
        return true;
    }
    private boolean isObjectTouchToRoot(TakingRootGame.Object o){
        int startX = (int)Math.max(Math.floor(o.hitbox.left), 0);
        int startY = (int)Math.max(Math.floor(o.hitbox.top), 0);
        int endX   = (int)Math.min(Math.ceil(o.hitbox.right), WIDTH);
        int endY   = (int)Math.min(Math.ceil(o.hitbox.bottom), HEIGHT);
        for(int y = startY; y < endY; y++)
            for(int x = startX; x < endX; x++)
                if(rootBitmap.getPixel(x, y) != Color.TRANSPARENT) return true;

        return false;
    }
    private void tpRandomRoot(){
        if(r.nextInt(4) == 0){
            //en eski kökler ağırlıklı olacak şekilde rastgele bir yerden başla
            int randomIndex = (int)(Math.pow(Math.random(), 5.0) * activesLength);
            int newI = actives[randomIndex];
            x = (newI % WIDTH) + 0.5f;
            y = (int)(newI / WIDTH) + 0.5f;
        }
        else{
            //en yakın objeye en yakın olan kıdımdan başla
            float maxDistance = Float.MAX_VALUE;
            int selectedI = -1;
            for(TakingRootGame.Object o : objects){
                if(o.isNotInScreen() || !isHealthyObject(o.imgID)) continue;

                for(int i = 0; i < activesLength; i++){
                    int rootI = actives[i];
                    int x = rootI % WIDTH;
                    int y = rootI / WIDTH;
                    float xDistance = o.hitbox.centerX() - x;
                    float yDistance = o.hitbox.centerY() - y;
                    float currentDistance = xDistance * xDistance + yDistance * yDistance;

                    if(currentDistance < maxDistance){
                        maxDistance = currentDistance;
                        selectedI = rootI;
                    }
                }
            }
            if(selectedI == -1){
                //en eski kökler ağırlıklı olacak şekilde rastgele bir yerden başla
                //(yukarıdakinin aynısı) TODO belki tek bir metoda yazarım kod kısaltmak için
                int randomIndex = (int)(Math.pow(Math.random(), 5.0) * activesLength);
                int newI = actives[randomIndex];
                x = (newI % WIDTH) + 0.5f;
                y = (int)(newI / WIDTH) + 0.5f;
            }
            else{
                x = (selectedI % WIDTH) + 0.5f;
                y = (int) (selectedI / WIDTH) + 0.5f;
            }
        }
        btnNewRoot.setEnabled(false);
        newRootCounter = 0;
        noNewRootCounter = 0;

        tpX = (int)this.x;
        tpY = (int)this.y;
        animationRadius = ANIMATION_TP_START_RADIUS;
    }

    @Override
    public void draw(Canvas canvas){
        gp.imageStore.drawScaledPixelImage(canvas, R.drawable.plant_background,
                viewport.toPixelX(0), viewport.toPixelY(0), viewport.toPixelSize(WIDTH), viewport.toPixelSize(REEL_HEIGHT));

        float left   = viewport.toPixelX(0);
        float right  = viewport.toPixelX(WIDTH);
        float bitmapTop = viewport.toPixelY(REEL_HEIGHT - HEIGHT);
        float bottom = viewport.toPixelY(REEL_HEIGHT);

        dstRect.set(left, bitmapTop, right, bottom);

        canvas.drawBitmap(rootBitmap, srcRect, dstRect, bitmapPaint);

        //---------------------ANIMATION BITMAP DRAW---------------------
        animationBitmap.eraseColor(Color.TRANSPARENT);
        int x = (int)this.x, y = (int)this.y;
        //tp circle
        if(animationRadius > 0f) setCircle(animationBitmap, tpX, tpY, animationRadius);
        //pointer
        if(inAnimation && animationRadius <= 0f) animationBitmap.setPixel(x, y, ANIMATION_COLOR);
        canvas.drawBitmap(animationBitmap, srcRect, dstRect, bitmapPaint);

        //-----------------------OBJECTS DRAW-----------------------
        for(TakingRootGame.Object o : objects){
            if(o.isNotInScreen()) continue;//ekran dışındakileri atla

            gp.imageStore.drawScaledPixelImageWithCenter(canvas, o.imgID,
                    viewport.toPixelX(o.hitbox.centerX()),
                    viewport.toPixelSize(o.hitbox.centerY()) + bitmapTop,
                    viewport.toPixelSize(o.hitbox.width()*1.5f),
                    viewport.toPixelSize(o.hitbox.width()*1.5f));
        }

        //---------------------UI DRAW---------------------
        if(joystick != null)      joystick.draw(canvas);
        if(btnNewRoot != null)    btnNewRoot.draw(canvas);
        if(btnMark != null)       btnMark.draw(canvas);
        if(btnGoToMarked != null) btnGoToMarked.draw(canvas);
    }

    private void setCircle(Bitmap bitmap, int x, int y, float radius){
        final float REPEAT = 30;
        for(float degree = 0; degree < 360; degree += 360f / REPEAT){
            double radian = Math.toRadians(degree);
            float xOffset = (float)(Math.cos(radian) * radius);
            float yOffset = (float)(Math.sin(radian) * radius);

            int x2 = (int)(x + xOffset);
            int y2 = (int)(y + yOffset);
            if(x2 < 0 || x2 >= bitmap.getWidth() || y2 < 0 || y2 >= bitmap.getHeight()) continue;

            bitmap.setPixel(x2, y2, TakingRootGame.ANIMATION_COLOR);
        }
    }

    @Override
    public boolean handleTouch(MotionEvent event){
        return btnNewRoot.handleTouch(event) | btnMark.handleTouch(event)| btnGoToMarked.handleTouch(event) | joystick.handleTouch(event);
    }

    @Override
    public void preloadGame(){
        if(isPreloaded) return;
        //---------------------------------JOY STICK---------------------------------
        if(Configs.IS_USE_FLOATING_VIEWS){
            joystick = new FloatingJoystick();
        }
        else{
            joystick = new Joystick(gp.SCREEN_WIDTH, gp.SCREEN_HEIGHT);
            joystick.setPosition(Joystick.Position.BOTTOM_LEFT);
        }

        //------------------------------------------------BUTTONS------------------------------------------------
        final int tileSize = (int)(gp.tileSize*Configs.UI_SCALE);
        final android.content.Context context = gp.getContext();
        float btnWidth = tileSize*1.5f;
        float padding = tileSize/3f;
        float left = gp.SCREEN_WIDTH - btnWidth - padding;
        float top  = gp.SCREEN_HEIGHT - btnWidth - padding;
        int color = Color.parseColor("#66000000");
        String string = context.getString(R.string.root);
        btnNewRoot = new CanvasButton(left, top, left+btnWidth, top+btnWidth, string, color);
        btnNewRoot.setOnClickListener(() -> {
            if(animationBackCount > 0 || animationRadius > 0) return;
            tpRandomRoot();
        });

        top -= (padding + btnWidth);
        string = context.getString(R.string.mark);
        btnMark = new CanvasButton(left, top, left+btnWidth, top+btnWidth, string, color);
        btnMark.setOnClickListener(() -> {
            if(animationBackCount > 0 || animationRadius > 0) return;
            markedI = this.lastI;
            if(btnGoToMarked != null)
                btnGoToMarked.setEnabled(true);
        });

        top -= (padding + btnWidth);
        string = context.getString(R.string.go_to_marked);
        btnGoToMarked = new CanvasButton(left, top, left+btnWidth, top+btnWidth, string, color);
        btnGoToMarked.setOnClickListener(() -> {
            if(markedI == -1 || animationRadius > 0 || animationBackCount > 0) return;
            tpX = markedI % WIDTH;
            tpY = markedI / WIDTH;
            animationRadius = ANIMATION_TP_START_RADIUS;
        });

        //---------------------------------OTHERS---------------------------------
        rootBitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        animationBitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        srcRect = new Rect(0, 0, WIDTH, HEIGHT);
        dstRect = new RectF();
        rootArea = new RectF(75f, 0f, 165f, 45f);
        bitmapPaint = new Paint();
        bitmapPaint.setFilterBitmap(false);
        actives = new short[WIDTH * HEIGHT];
        r = new java.util.Random();

        objects = new Object[10];
        for(int i = 0; i < objects.length; i++)
            objects[i] = new Object();

        gp.imageStore.preloadImage(R.drawable.plant_background);
        for(int id : imgIDs) gp.imageStore.preloadImage(id);
        isPreloaded = true;
    }
    @Override
    public void destroyGame(){
        joystick        = null;
        btnNewRoot      = null;
        rootBitmap      = null;
        animationBitmap = null;
        srcRect         = null;
        rootArea        = null;
        dstRect         = null;
        bitmapPaint     = null;
        actives         = null;
        r               = null;
        java.util.Arrays.fill(objects, null);
        objects         = null;
        gp.imageStore.removeImage(R.drawable.plant_background);
        for(int id : imgIDs) gp.imageStore.removeImage(id);
        isPreloaded = false;
    }

    @Override
    public int getTime(){ return time/60; }
    @Override
    public int getCurrentScore(){ return score; }
    @Override
    public int getMaxScore(){ return maxScore; }
    @Override
    public State getState(){ return state; }
    @Override
    public int getLogoTextureID(){ return R.drawable.rooting_logo; }
    @Override
    public void reset(){
        state = State.RUNNING;
        rootBitmap.eraseColor(Color.TRANSPARENT);
        animationBitmap.eraseColor(Color.TRANSPARENT);
        activesLength = 0;
        noNewRootCounter = 0;
        loadRootsFromBitmap(gp.imageStore.getImage(R.drawable.plant_background));
        for(TakingRootGame.Object o : objects) setObjectToRandomPlace(o);
        tpRandomRoot();

        btnMark.setEnabled(true);
        btnGoToMarked.setEnabled(false);
        joystick.reset();
        markedI = -1;
        animationRadius = -1;
        animationBackCount = -1;
        offRootFrames = TOLERANCE_FRAMES + 1;
        time = 14_400;
        score = 0;
    }
    private void loadRootsFromBitmap(Bitmap bgBitmap){
        final int targetColor = Color.WHITE;
        int bgWidth = bgBitmap.getWidth();
        int bgHeight = bgBitmap.getHeight();
        int[] bgPixels = new int[bgWidth * bgHeight];

        bgBitmap.getPixels(bgPixels, 0, bgWidth, 0, 0, bgWidth, bgHeight);

        int yMin = -1;
        for(int y = 0; y < bgHeight; y++){
            for(int x = 0; x < bgWidth; x++){
                int color = bgPixels[y * bgWidth + x];
                if(color == targetColor){
                    yMin = y;
                    break;
                }
            }
            if(yMin != -1) break;
        }

        if(yMin == -1) return;

        int xOffset = (bgWidth - WIDTH) / 2;

        for(int srcY = yMin; srcY < bgHeight; srcY++){
            int localY = srcY - yMin; //en üstteki beyaz piksel localY = 0 olur!

            //pixelBitmap sınırını geçerse alt kısmı taramayı kes
            if(localY >= HEIGHT) break;

            for(int srcX = 0; srcX < bgWidth; srcX++){
                int localX = srcX - xOffset;

                //x sınırları dışındaysa atla
                if(localX < 0 || localX >= WIDTH) continue;

                int color = bgPixels[srcY * bgWidth + srcX];
                if(color == targetColor){
                    //bitmap üzerine boya
                    rootBitmap.setPixel(localX, localY, ROOT_COLOR);

                    //actives dizisine ekle
                    int currentI = localY * WIDTH + localX;
                    actives[activesLength++] = (short) currentI;
                }
            }
        }
    }

    private class Object{
        public final RectF hitbox = new RectF();
        public int imgID;
        public int life;

        public Object(){ removeInScreen(); }

        public void set(int imgID, float x, float y, float width, float height, int life){
            this.imgID = imgID; this.life = life;
            float hw = width / 2f, hh = height / 2f;
            hitbox.set(x-hw, y-hh, x+hw, y+hh);
        }

        public void reducingSize(float scale){ set(imgID, hitbox.centerX(), hitbox.centerY(), hitbox.width() * scale, hitbox.height() * scale, this.life); }
        public boolean isNotInScreen(){ return !(hitbox.right > 0) || !(hitbox.left < gp.SCREEN_WIDTH) || !(hitbox.top < gp.SCREEN_HEIGHT) || !(hitbox.bottom > 0); }
        public void removeInScreen(){ set(-1, -1000, 0, 0, 0, 0); }
    }

    private boolean isHealthyObject(int id){ return id == R.drawable.rooting_oxygen || id == R.drawable.rooting_water; }
}
