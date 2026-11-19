package com.novatron.novatronbiocore.Games;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;

import com.novatron.novatronbiocore.Configs;
import com.novatron.novatronbiocore.GamePanel;
import com.novatron.novatronbiocore.R;
import com.novatron.novatronbiocore.tools.FloatingJoystick;
import com.novatron.novatronbiocore.tools.Joystick;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;

public class MazeEscape implements Game{

    private static final int MAZE_W = 61;
    private static final int MAZE_H = 51;

    private static final float SPEED = 0.05f;
    private static final float EPS = 0.001f; //duvara yapışıp takılmamak için

    //----------------------------DUVAR TILE'LARI----------------------------
    //Bir duvar karosunun 4 komşusuna bakılır. Komşu DUVAR DEĞİLSE (çim/yol) o yön "açık" sayılır
    //ve karo çizimi o kenarda orman kenarı (çime geçiş) olmalıdır.
    //index = (üst açıksa 1) + (sağ açıksa 2) + (alt açıksa 4) + (sol açıksa 8)
    private static final int OPEN_UP    = 1;
    private static final int OPEN_RIGHT = 2;
    private static final int OPEN_DOWN  = 4;
    private static final int OPEN_LEFT  = 8;

    private static final int[] WALL_TILES = {
            R.drawable.maze_forest_center, //0: hiçbir yön açık değil (tam orman / iç)
            R.drawable.maze_forest_1,  //1: sadece ÜST açık
            R.drawable.maze_forest_2,  //2: sadece SAĞ açık
            R.drawable.maze_forest_3,  //3: ÜST + SAĞ açık (dış köşe)
            R.drawable.maze_forest_4,  //4: sadece ALT açık
            R.drawable.maze_forest_5,  //5: ÜST + ALT açık (yatay ince duvar)
            R.drawable.maze_forest_6,  //6: SAĞ + ALT açık (dış köşe)
            R.drawable.maze_forest_7,  //7: ÜST + SAĞ + ALT açık (sol tarafa bağlı uç)
            R.drawable.maze_forest_8,  //8: sadece SOL açık
            R.drawable.maze_forest_9,  //9: ÜST + SOL açık (dış köşe)
            R.drawable.maze_forest_10, //10: SAĞ + SOL açık (dikey ince duvar)
            R.drawable.maze_forest_11, //11: ÜST + SAĞ + SOL açık (alta bağlı uç)
            R.drawable.maze_forest_12, //12: ALT + SOL açık (dış köşe)
            R.drawable.maze_forest_13, //13: ÜST + ALT + SOL açık (sağa bağlı uç)
            R.drawable.maze_forest_14, //14: SAĞ + ALT + SOL açık (üste bağlı uç)
            R.drawable.maze_forest_15, //15: 4 yön de açık (tek başına kalmış blok)
    };

    private static final int[] PLAYER_IDS = {
            R.drawable.maze_boy_left_1, R.drawable.maze_boy_left_2,
            R.drawable.maze_boy_top_1, R.drawable.maze_boy_top_2,
            R.drawable.maze_boy_right_1, R.drawable.maze_boy_right_2,
            R.drawable.maze_boy_bottom_1, R.drawable.maze_boy_bottom_2,
    };

    private static final int[] BATTERY_IDS = {
            R.drawable.maze_battery_left, R.drawable.maze_battery_top,
            R.drawable.maze_battery_right, R.drawable.maze_battery_bottom,
    };

    private static final int[] CARBOY_IDS = {
            R.drawable.maze_plastic_left_1, R.drawable.maze_plastic_left_2,
            R.drawable.maze_plastic_top_1, R.drawable.maze_plastic_top_2,
            R.drawable.maze_plastic_right_1, R.drawable.maze_plastic_right_2,
            R.drawable.maze_plastic_bottom_1, R.drawable.maze_plastic_bottom_2,
    };

    private static final int[] BRUSH_IDS = {
            R.drawable.maze_brush_bottom_1, R.drawable.maze_brush_bottom_2
    };

    private static final int[] BANANA_IDS = {
            R.drawable.maze_banana_trash_left, R.drawable.maze_banana_trash_top,
            R.drawable.maze_banana_trash_right, R.drawable.maze_banana_trash_bottom,
    };

    private static final int[] METAL_CAN_IDS = {
            R.drawable.maze_bear_left_1, R.drawable.maze_bear_left_2,
            R.drawable.maze_bear_top_1, R.drawable.maze_bear_top_2,
            R.drawable.maze_bear_right_1, R.drawable.maze_bear_right_2,
            R.drawable.maze_bear_bottom_1, R.drawable.maze_bear_bottom_2,
    };

    //preload ve destroygame'de resim yüklemeleri daha temiz yazılabilsin diye
    private static final int[][] ALL_IDS = {
            WALL_TILES, PLAYER_IDS, BATTERY_IDS, CARBOY_IDS, BRUSH_IDS,
            BANANA_IDS, METAL_CAN_IDS
    };

    private static float DEFAULT_DARKNESS_RADIUS;

    private Entity player;
    private boolean[] tiles; //true = duvar
    private short[] d;
    private int D;
    private ArrayList<Entity> entities;
    private float darknessRadius;

    private final GamePanel gp;
    private Joystick joystick;
    private PathFinder pathFinder;
    private int time;
    private State state = State.RUNNING;
    private final Paint pathPaint = new Paint();
    private final Paint dPaint = new Paint();
    public MazeEscape(GamePanel gp){
        this.gp = gp;
        pathPaint.setColor(Color.parseColor("#80FF0000")); //yarı saydam kırmızı olmalı
        pathPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void updateScreenSize(){
        DEFAULT_DARKNESS_RADIUS = gp.SCREEN_WIDTH * 1.5f;
        final float uiTile = gp.tileSize * Configs.UI_SCALE;

        if(joystick != null){
            joystick.setOuterRadius(uiTile * 1.7f);
            if(joystick instanceof FloatingJoystick)
                ((FloatingJoystick) joystick).setArea(0, 0, gp.SCREEN_WIDTH, gp.SCREEN_HEIGHT);
        }
    }

    @Override
    public void update(){
        if(joystick == null || player == null) return;

        float dx = (float)(joystick.getValueX() * SPEED);
        float dy = (float)(joystick.getValueY() * SPEED);

        player.speedX = dx;
        player.speedY = dy;

        for(Entity monster : entities)
            monster.update();

        time++;

        if(player.getHitboxTileY() == MAZE_H - 1 && player.getHitboxTileX() == MAZE_W - 1)
            //oyuncu son karoya ulaştı, oyun bitti
            state = State.GAME_OVER;
    }

    @Override
    public void draw(Canvas canvas){
        canvas.drawColor(Color.BLACK);
        if(player == null || tiles == null) return;

        final float ts = gp.tileSize;
        final float cx = gp.SCREEN_WIDTH / 2f;
        final float cy = gp.SCREEN_HEIGHT / 2f;
        final float camX = player.x;
        final float camY = player.y;

        //--------------------------------------DUVARLAR--------------------------------------
        //ekrana sığan karo aralığı
        int minTX = (int) Math.floor(camX - cx / ts);
        int maxTX = (int) Math.floor(camX + cx / ts);
        int minTY = (int) Math.floor(camY - cy / ts);
        int maxTY = (int) Math.floor(camY + cy / ts);

        for(int ty = minTY; ty <= maxTY; ty++){
            //kenarları yuvarlayarak hesaplıyoruz ki komşu karolar arasında ince boşluk kalmasın
            int top = Math.round(cy + (ty     - camY) * ts);
            int bottom = Math.round(cy + (ty + 1 - camY) * ts);

            for(int tx = minTX; tx <= maxTX; tx++){

                int left  = Math.round(cx + (tx     - camX) * ts);
                int right = Math.round(cx + (tx + 1 - camX) * ts);

                int imageId = isWall(tx, ty) ? getWallTile(tx, ty) : R.drawable.maze_grass;
                gp.imageStore.drawScaledPixelImageWithRect(canvas, imageId, left, top, right, bottom);

                int i = ty * MAZE_W + tx;
                if(Configs.DEBUG && !isWall(tx, ty) && d[i] != -1){
                    canvas.drawText(Short.toString(d[i]),
                            (left + right - ts) / 2f, (top + bottom) / 2f,
                            dPaint);
                }
            }
        }

        //--------------------------------------CANLILAR--------------------------------------
        entities.sort((e1, e2) -> Float.compare(e1.y, e2.y));
        //varsa takip ettikleri yolları çiz
        if(Configs.DEBUG)
            for(Entity e : entities) e.drawPath(canvas);
        //canlıları çiz
        for(Entity e : entities) e.draw(canvas);

        //----------------------------------------ÖDÜL----------------------------------------
        gp.imageStore.drawScaledPixelImage(canvas, R.drawable.maze_award_medallion,
                gp.SCREEN_WIDTH/2f  - (player.x + MAZE_W - 1 - (int)Math.floor(player.x) * 2) * ts,
                gp.SCREEN_HEIGHT/2f - (player.y + MAZE_H - 1 - (int)Math.floor(player.y) * 2) * ts, ts, ts);

        //--------------------------------------KARANLIK---------------------------------------
        if(darknessRadius > 0)
            drawFlashlightEffect(canvas, gp.SCREEN_WIDTH/2f, gp.SCREEN_HEIGHT/2f, darknessRadius);

        if(joystick != null) joystick.draw(canvas);
    }

    @Override
    public boolean handleTouch(MotionEvent event){
        return joystick != null && joystick.handleTouch(event);
    }

    @Override
    public void preloadGame(){
        //---------------------------------JOY STICK---------------------------------
        if(Configs.IS_USE_FLOATING_VIEWS){
            joystick = new FloatingJoystick();
        }
        else{
            joystick = new Joystick(gp.SCREEN_WIDTH, gp.SCREEN_HEIGHT);
            joystick.setPosition(Joystick.Position.BOTTOM_LEFT);
        }

        tiles = new boolean[MAZE_W * MAZE_H];
        d = new short[tiles.length];
        pathFinder = new PathFinder(MAZE_W, MAZE_H);
        setEntities();

        gp.imageStore.preloadImage(R.drawable.maze_award_medallion);
        gp.imageStore.preloadImage(R.drawable.maze_grass);
        for(int[] ids : ALL_IDS)
            for(int id : ids) gp.imageStore.preloadImage(id);

        dPaint.setTypeface(gp.ui.pixelFont);
        dPaint.setTextSize(gp.tileSize/4f);
    }

    @Override
    public void destroyGame(){
        joystick   = null;
        player     = null;
        tiles      = null;
        d          = null;
        pathFinder = null;
        entities.clear();
        entities = null;
        gp.imageStore.removeImage(R.drawable.maze_award_medallion);
        gp.imageStore.removeImage(R.drawable.maze_grass);
        for(int[] ids : ALL_IDS)
            for(int id : ids) gp.imageStore.removeImage(id);
    }

    /** (x, y) duvar karosunun komşularına bakıp WALL_TILES'tan uygun resmi seçer. */
    private int getWallTile(int x, int y){
        int mask = 0;
        if(!isWall(x, y - 1)) mask |= OPEN_UP;
        if(!isWall(x + 1, y)) mask |= OPEN_RIGHT;
        if(!isWall(x, y + 1)) mask |= OPEN_DOWN;
        if(!isWall(x - 1, y)) mask |= OPEN_LEFT;
        return WALL_TILES[mask];
    }

    @Override
    public int getTime(){ return -1; }
    @Override
    public int getCurrentScore(){ return time/60; }
    @Override
    public int getMaxScore(){ return 0; }
    @Override
    public State getState(){ return state; }
    @Override
    public int getLogoTextureID(){ return -1; }
    @Override
    public void reset(){
        state = State.RUNNING;
        time = 0;
        if(joystick != null) joystick.reset();
        darknessRadius = DEFAULT_DARKNESS_RADIUS;

        if(tiles == null || player == null) return;
        generateMaze(MAZE_W, MAZE_H, tiles, d);
        D = d[d.length - 1]; //sağ alt köşe

        for(Entity e : entities) e.respawn();
        player.setPosition(0.5f, 0.5f);
    }

    private enum Direction{ UP, DOWN, RIGHT, LEFT }

    private class Entity{

        //konum, hız
        public float x, y;
        public float speedX, speedY;
        public float speed = 0.05f;
        public float tMin = -1, tMax = -1;
        private final ArrayList<Integer> availablePositions = new ArrayList<>();

        //hitbox
        public float hitboxOffsetX, hitboxOffsetY;
        public float hitboxHalfW, hitboxHalfH;
        private final RectF hitbox = new RectF();

        //yön
        public Direction direction = Direction.RIGHT;

        //hasar vurma
        public int damage = 0;

        //takip etme
        protected int minStartFollowTileDistance, maxFollowDistance;
        public boolean onFollow = false;
        public int targetTileX, targetTileY;
        protected ArrayList<PathFinder.Node> path;
        private final RectF catchBox = new RectF();
        //hizalanmış sayılması için izin verilen en büyük sapma (karo cinsinden)
        protected float ALIGN_EPS = 0.01f;
        //oyuncunun hitbox'ının bu kadar içine girilince "yakaladı" sayılır ve durulur (karo cinsinden)
        private static final float CATCH_DEPTH = 0.05f;

        //çizim
        public float drawMultiple;
        public int animationCounter;
        public int targetAnimationTime = 20;
        public boolean sprite2 = false;
        public int alpha = 255;
        public int
                left1=-1, left2=-1, right1=-1, right2=-1,
                bottom1=-1, bottom2=-1, top1=-1, top2=-1;

        public Entity(float x, float y){
            this.x = x;
            this.y = y;

            drawMultiple = 1.3f;

            //hitbox (varsayılan)
            this.hitboxOffsetX = 0.0f;
            this.hitboxOffsetY = 0.0f;
            this.hitboxHalfW = 0.25f;
            this.hitboxHalfH = 0.3f;
        }

        public void setImageIDs(int left1ID, int left2ID, int top1ID, int top2ID,
                                int right1ID, int right2ID, int bottom1ID, int bottom2ID){
            this.left1 = left1ID;   this.top1 = top1ID;
            this.left2 = left2ID;   this.top2 = top2ID;
            this.right1 = right1ID; this.bottom1 = bottom1ID;
            this.right2 = right2ID; this.bottom2 = bottom2ID;
        }
        public void setImageIDs(int[] IDs){
            if(IDs == null) return;

            if(IDs.length == 8)
                setImageIDs(IDs[0], IDs[1], IDs[2], IDs[3], IDs[4], IDs[5], IDs[6], IDs[7]);
            else if(IDs.length == 4)
                setImageIDs(IDs[0], IDs[0], IDs[1], IDs[1], IDs[2], IDs[2], IDs[3], IDs[3]);
            else
                throw new RuntimeException("Verilen dize herhangi bir mantıkta yerleştirlilemiyor. (length 8 ya da 4 değil)");
        }

        public void setPosition(float x, float y){ this.x = x; this.y = y; }

        public void update(){

            if(onFollow){

                onFollowing();

                if(shouldStopFollowing()){
                    onFollow = false;
                    onFollowEnd();
                }
            }
            else{
                onFollow = shouldStartFollow();
                if(onFollow) onFollowStart();
                else setAction();
            }

            move(speedX, speedY);


            if(speedY != 0 || speedX != 0){ //canlı hareket ediyorsa

                //-----------------------YÖN BELİRLEME-----------------------
                //dikeyde daha hızlı gidiyorsa
                if(Math.abs(speedY) > Math.abs(speedX))
                    direction = (speedY > 0) ? Direction.DOWN : Direction.UP;
                else
                    direction = (speedX > 0) ? Direction.RIGHT : Direction.LEFT;

                //-------------------------ANİMASYON-------------------------
                animationCounter++;
                if(animationCounter >= targetAnimationTime){
                    animationCounter = 0;
                    sprite2 = !sprite2;
                }
            }
        }
        protected void setAction(){}

        public void draw(Canvas canvas){

            final float ts = gp.tileSize;
            final float screenCX = gp.SCREEN_WIDTH/2f;
            final float screenCY = gp.SCREEN_HEIGHT/2f;
            int texture = getDrawImageID();

            if(texture == -1) return;

            float drawX = screenCX + (x - player.x)*ts;
            float drawY = screenCY + (y - player.y)*ts;

            float ds = ts * drawMultiple;
            gp.imageStore.drawScaledPixelImageWithAlphaAndCenter(canvas, texture, drawX, drawY, ds, ds, alpha);

            if(Configs.DEBUG){
                //gerçek hitbox (offset dahil) ekran koordinatına çevrilir
                RectF h = getHitbox();
                float left   = screenCX + (h.left   - player.x) * ts;
                float top    = screenCY + (h.top    - player.y) * ts;
                float right  = screenCX + (h.right  - player.x) * ts;
                float bottom = screenCY + (h.bottom - player.y) * ts;
                canvas.drawRect(left, top, right, bottom, gp.ui.fontDebug);
            }
        }
        protected int getDrawImageID(){
            int texture = -1;

            switch(direction){
                case LEFT:  texture = (sprite2) ? left2   : left1; break;
                case UP:    texture = (sprite2) ? top2    : top1; break;
                case RIGHT: texture = (sprite2) ? right2  : right1; break;
                case DOWN:  texture = (sprite2) ? bottom2 : bottom1; break;
            }

            return texture;
        }
        public void drawPath(Canvas canvas){
            if(path == null || path.isEmpty() || !onFollow) return;

            final float ts = gp.tileSize;

            final int ctx = (int)Math.floor(player.x);
            final int cty = (int)Math.floor(player.y);
            final float screenCX = gp.SCREEN_WIDTH/2f - (player.x - ctx) * ts;
            final float screenCY = gp.SCREEN_HEIGHT/2f - (player.y - cty) * ts;

            for(PathFinder.Node node : path){
                int dx = node.x - ctx;
                int dy = node.y - cty;

                float drawX = screenCX + dx * ts ;
                float drawY = screenCY + dy * ts ;

                canvas.drawRect(drawX, drawY, drawX + ts, drawY + ts, pathPaint);
            }
        }

        /** Canlının rastgele bir yöne gitmesini sağlar. Eğer o yönde bir sonraki adım
         * duvara çarpma ise duvara çarpmayacağı bir yöne dönmesini sağlar. */
        public void setRandomDirection(){

            //bir sonraki hareket sonunda duvara çarpmayana kadar tekrarla
            //(ya da duruyorsa)
            int tryCount = 0; //sonsuz döngüyü engellemek için
            while((move(speedX, speedY) || (speedX == 0 && speedY == 0)) && tryCount < 8){
                //hamleyi geri al
                move(-speedX, -speedY);

                int num = r.nextInt(4);
                switch(num){
                    case 0: speedX = -speed; speedY = 0;      break; //sol
                    case 1: speedX = 0;      speedY = -speed; break; //yukarı
                    case 2: speedX = speed;  speedY = 0;      break; //sağ
                    case 3: speedX = 0;      speedY = speed;  break; //aşağı
                }

                tryCount++;
            }

            //döngüden çıktığında ekstra bir defa ilerlemiş olacak
            //o yüzden geri git
            move(-speedX, -speedY);
        }

        /** Canlıyı istenilen yönde hareket ettirir, bu hareketin sonunda
         *  duvara temas olup olmadığını dönürür. (true = duvara çarptı) */
        protected boolean move(float dx, float dy){
            boolean isTouched = false;

            if(dx != 0){
                x += dx;

                if(isTouchingToWall()){
                    //hitbox'ın kenarı duvarın kenarına gelecek şekilde x hesaplanır (offset dahil)
                    isTouched = true;
                    RectF h = getHitbox();
                    if(dx > 0) x = (float) Math.floor(h.right) - EPS - hitboxOffsetX - hitboxHalfW;
                    else x = (float) Math.floor(h.left) + 1f + EPS - hitboxOffsetX + hitboxHalfW;
                }
            }

            if(dy != 0){
                y += dy;

                if(isTouchingToWall()){
                    isTouched = true;
                    RectF h = getHitbox();
                    if(dy > 0) y = (float) Math.floor(h.bottom) - EPS - hitboxOffsetY - hitboxHalfH;
                    else y = (float) Math.floor(h.top) + 1f + EPS - hitboxOffsetY + hitboxHalfH;
                }
            }

            return isTouched;
        }

        /** Entity'nin kutusu şu an herhangi bir duvar karosuna değiyor mu? */
        public boolean isTouchingToWall(){
            RectF h = getHitbox();
            int left   = (int) Math.floor(h.left);
            int right  = (int) Math.floor(h.right);
            int top    = (int) Math.floor(h.top);
            int bottom = (int) Math.floor(h.bottom);

            for(int ty = top; ty <= bottom; ty++)
                for(int tx = left; tx <= right; tx++)
                    if(isWall(tx, ty))
                        return true;

            return false;
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        protected boolean setPath(Entity e){
            return setPath(getHitboxTileX(), getHitboxTileY(), e.getHitboxTileX(), e.getHitboxTileY());
        }
        protected boolean setPath(int startTileX, int startTileY, int targetTileX, int targetTileY){
            if(path == null) path = new ArrayList<>();

            pathFinder.setNodes(startTileX, startTileY, targetTileX, targetTileY, tiles);

            return pathFinder.search(path);
        }

        protected boolean shouldStartFollow(){
            if(player.getShortestDistanceSquare(this) >
                    minStartFollowTileDistance * minStartFollowTileDistance) return false;

            //oyuncuyla aynı karede yol gerekmez, takip sürmeli
            if(isInSameTile(player)){
                if(path != null) path.clear();
                return true;
            }

            //eğer oyuncuya yol yoksa başlama
            if(!setPath(player)) return false;

            return path.size() < minStartFollowTileDistance;
        }
        protected boolean shouldStopFollowing(){
            if(player.getShortestDistanceSquare(this) >
                    maxFollowDistance * maxFollowDistance) return true;

            //aynı karedeyken takibi bırakma
            if(isInSameTile(player)) return false;

            if(!setPath(player)) return true;

            return path.size() > maxFollowDistance;
        }
        protected void onFollowing(){

            //oyuncunun hitbox'ına biraz girmek yeter, merkezine gitmeye gerek yok (titremeyi de önler)
            if(hasCaught(player)){
                goToPoint(0f, 0f);
                return;
            }

            float dx, dy;

            if(getHitboxTileX() == targetTileX && getHitboxTileY() == targetTileY){
                //hedef ile aynı karede A* yol döndürmüyor (başlangıç = hedef), doğrudan oyuncuya git
                dx = player.getHitbox().centerX() - getHitbox().centerX();
                dy = player.getHitbox().centerY() - getHitbox().centerY();
            }
            else{
                if(!setPath(getHitboxTileX(), getHitboxTileY(), targetTileX, targetTileY)){
                    goToPoint(0f, 0f);
                    return;
                }

                PathFinder.Node next = path.get(0);

                //hitbox merkezinden hedef karenin ortasına olan fark
                dx = (next.x + 0.5f) - getHitbox().centerX();
                dy = (next.y + 0.5f) - getHitbox().centerY();

                //köşelerde hitbox duvara girmesin diye önce koridorun ortasına hizalan, sonra ilerle
                if(Math.abs(dx) > Math.abs(dy)){
                    if(Math.abs(dy) > ALIGN_EPS) dx = 0f;
                }
                else{
                    if(Math.abs(dx) > ALIGN_EPS) dy = 0f;
                }
            }

            goToPoint(dx, dy);
        }
        protected void onFollowStart(){}
        protected void onFollowEnd(){
            speedX = 0f;
            speedY = 0f;
        }
        /**
         * Hitbox merkezinden hedef noktaya olan fark (dx, dy) verilir, hızı (speedX, speedY) ayarlar.
         * (0, 0) = dur. `speed` en yüksek hızdır, hedef aşılmaz.
         * Kayma/ivme gibi farklı hareket tarzları için override edilir: speedX/speedY canlının o
         * anki gerçek hızıdır, override istenen hıza (dx,dy yönünde, en fazla speed) doğru
         * kademeli yaklaşabilir. Takip bitince/yakalayınca (0,0) ile çağrıldığı için yavaşlama da
         * aynı yerde halledilir.
         */
        protected void goToPoint(float dx, float dy){
            float dist = (float) Math.sqrt(dx * dx + dy * dy);

            if(dist < 0.001f){
                speedX = 0f;
                speedY = 0f;
                return;
            }

            float k = Math.min(speed, dist) / dist;
            speedX = dx * k;
            speedY = dy * k;
        }

        /**Hitbox'u güncel konuma göre hesaplayıp döndürür (hep taze, eski kalmaz).
         * Dönen RectF Entity'ye aittir: üzerinde intersect()/set() gibi değiştiren işlem YAPMA,
         * gerekirse kopyasını al: new RectF(entity.getHitbox()). */
        public RectF getHitbox(){
            float cx = x + hitboxOffsetX;
            float cy = y + hitboxOffsetY;
            hitbox.set(cx - hitboxHalfW, cy - hitboxHalfH, cx + hitboxHalfW, cy + hitboxHalfH);
            return hitbox;
        }

        /**
         * Hitbox'ımız e'nin hitbox'ının CATCH_DEPTH kadar içine girdi mi?
         * (e'nin kutusu küçültülüp kesişime bakılır, böylece sadece kenarların değmesi yetmez,
         * gerçekten biraz içine girilmiş olur. Oyuncunun hasar kontrolü de zaten kesişim arıyor.)
         */
        protected boolean hasCaught(Entity e){
            catchBox.set(e.getHitbox());
            catchBox.inset(CATCH_DEPTH, CATCH_DEPTH);
            return RectF.intersects(getHitbox(), catchBox);
        }

        /** Başka bir canlıyla hitbox'ları kesişiyor mu? (kenarların sadece değmesi kesişim sayılmaz) */
        public boolean intersects(Entity e){
            return RectF.intersects(getHitbox(), e.getHitbox());
        }
        /** e ile aynı karede miyiz? (A* başlangıç = hedef olunca yol bulamaz) */
        protected boolean isInSameTile(Entity e){
            return getHitboxTileX() == e.getHitboxTileX() && getHitboxTileY() == e.getHitboxTileY();
        }

        public void respawn(){
            speedX = 0f;
            speedY = 0f;
            onFollow = false;
            if(path != null) path.clear();
            if(tMin >= 0 && tMax <= 1)
                setRespawnPos(tMin, tMax);
        }

        public void setRespawnPos(float tMin, float tMax){
            availablePositions.clear();
            //doğabileceği uzuklıkları bilimsel gösterimden çıkar,
            //dirak uzaklığa çevir
            int tMinInt = (int)(tMin * D);
            int tMaxInt = (int)(tMax * D);

            //belirtilen aralıktaki tüm indexleri topla
            for(int i = 0; i < d.length; i++){
                int distance = d[i];
                if(distance == -1) continue;
                if(distance < tMinInt) continue;
                if(distance > tMaxInt) continue;
                availablePositions.add(i);
            }

            //eğer belirtilen aralıkta hiç konum yoksa aralığı biraz genişletip
            //tekrar dene
            if(availablePositions.isEmpty()){
                if(tMin <= 0.0f && tMax >= 1.0f) return; //artık doğacak alan yok
                setRespawnPos(Math.max(0, tMin - 0.01f), Math.min(1, tMax + 0.01f));
                return;
            }

            //rastgele bir tanesini seç
            int selectedI = availablePositions.get(r.nextInt(availablePositions.size()));

            int x = selectedI % MAZE_W;
            int y = selectedI / MAZE_W;

            this.x = x + 0.5f;
            this.y = y + 0.5f;
        }

        public float getXDistance(Entity e){ return Math.abs(x - e.x); }
        public float getYDistance(Entity e){ return Math.abs(y - e.y); }
        public float getShortestDistanceSquare(Entity e){
            return getXDistance(e) * getXDistance(e) + getYDistance(e) * getYDistance(e);
        }
        public int getHitboxTileX(){ return (int)Math.floor(getHitbox().centerX()); }
        public int getHitboxTileY(){ return (int)Math.floor(getHitbox().centerY()); }
    }

    private void setEntities(){
        entities = new ArrayList<>();

        //------------------------------------OYUNCU-------------------------------------
        player = new Entity(0.5f, 0.5f){

            private final int MAX_HEALTH = 10;
            private int health;
            private int hbCounter;
            /** -1 = hasar alabilir */
            private int invincibleCounter = -1;

            private final Paint healthPaint = new Paint();

            {
                //hitbox
                hitboxOffsetX = 0.0f;
                hitboxOffsetY = 0.18f;
                hitboxHalfH   = 0.15f;
                hitboxHalfW   = 0.20f;

                speed = SPEED;

                healthPaint.setStyle(Paint.Style.FILL);

                //resimler
                setImageIDs(PLAYER_IDS);
            }

            @Override
            public void setAction(){
                if(hbCounter > -1) hbCounter--;

                //hasar alamaz
                if(invincibleCounter > -1){
                    invincibleCounter--;
                    alpha = 125;
                    return;
                }
                else{
                    alpha = 255;
                }

                for(Entity e : entities){
                    if(e == this) continue;

                    if(e.damage <= 0) continue; //hasar vurmuyorsa geç
                    if(!e.intersects(this)) continue; //temas yoksa geç

                    //eğer canavar oyuncuya değiyorsa
                    gp.vibration.vibrateOneShot(150, 210);
                    health -= e.damage;
                    invincibleCounter = 60;
                    hbCounter = 360;

                    //oyuncu öldü, oyunu bitir
                    health = Math.max(health, 0);
                    if(health == 0) state = State.GAME_OVER;

                    break;
                }
            }

            @Override
            public void draw(Canvas canvas){
                super.draw(canvas);

                //-------------------------------------------------CAN BARI ÇİZİMİ-------------------------------------------------
                if(hbCounter <= -1) return;

                final float ts = gp.tileSize;
                final float cx = gp.SCREEN_WIDTH/2f;
                final float cy = gp.SCREEN_HEIGHT/2f;

                float width = ts * 0.8f;
                float height = ts * 0.25f;
                float frameW = ts * 0.04f;
                float offsetY = ts * 0.35f;

                float drawX = cx - width/2;
                float drawY = cy - offsetY - height;

                //siyah çerçeve
                healthPaint.setColor(Color.BLACK);
                canvas.drawRect(drawX, drawY, drawX + width, drawY + height, healthPaint);

                drawX += frameW;
                float healthWidth = (width - frameW*2) * health / MAX_HEALTH;
                healthPaint.setColor(Color.RED);
                canvas.drawRect(drawX, drawY + frameW, drawX + healthWidth, drawY + (height - frameW), healthPaint);
            }

            @Override
            public void respawn(){
                super.respawn();
                health = MAX_HEALTH;
                invincibleCounter = -1;
                hbCounter = -1;
                x = 0.5f;
                y = 0.5f;
            }

            @Override public boolean shouldStartFollow(){ return false; }
            @Override public boolean shouldStopFollowing(){ return true; }
            @Override public void onFollowing(){}
        };
        entities.add(player);

        //------------------------------------ÇALI---------------------------------------
        entities.add(new Entity(0.5f, 0.5f){

            {
                speed = 0.01f;
                setImageIDs(BRUSH_IDS[0], BRUSH_IDS[1], BRUSH_IDS[0], BRUSH_IDS[1],
                        BRUSH_IDS[0], BRUSH_IDS[1], BRUSH_IDS[0], BRUSH_IDS[1]);

                minStartFollowTileDistance = 20;
                maxFollowDistance = 35;
                targetAnimationTime = 45;

                this.hitboxOffsetX = 0.0f;
                this.hitboxOffsetY = 0.15f;
                this.hitboxHalfW = 0.35f;
                this.hitboxHalfH = 0.15f;

                tMin = 0.3f;
                tMax = 0.6f;

                damage = 1;
            }

            @Override
            public void setAction(){
                //takipte değilse görünür hale gel
                if(alpha < 255) alpha++;

                setRandomDirection();
            }

            @Override
            public void onFollowing(){
                //takip esnasında gizlen (şeffaflaş)
                if(alpha > 22) alpha--;

                targetTileX = player.getHitboxTileX();
                targetTileY = player.getHitboxTileY();
                super.onFollowing();
            }


            @Override
            public void respawn(){
                super.respawn();
                alpha = 255;
            }
        });

        //-----------------------------------BOZ AYI-------------------------------------
        entities.add(new Entity(0.5f, 0.5f){

            {
                speed = 0.038f;
                setImageIDs(METAL_CAN_IDS);

                this.hitboxOffsetX = 0.0f;
                this.hitboxOffsetY = 0.25f;
                this.hitboxHalfW = 0.25f;
                this.hitboxHalfH = 0.2f;

                minStartFollowTileDistance = 4;
                maxFollowDistance = 10;
                targetAnimationTime = 15;


                tMin = 0.4f;
                tMax = 0.6f;

                damage = 2;
            }

            @Override
            public void update(){
                super.update();
                if(onFollow){
                    if(darknessRadius > gp.tileSize*3.5f)
                        darknessRadius -= gp.tileSize*0.2f;
                }
                //oyuncu 15 bloktan yakınsa
                else if(getShortestDistanceSquare(player) < 225f){
                    float targetRadius = (float)(Math.sqrt(getShortestDistanceSquare(player)) * gp.tileSize * 2);
                    if(darknessRadius > targetRadius)
                        darknessRadius -= gp.tileSize*0.08f;
                    else if(darknessRadius < targetRadius)
                        darknessRadius += gp.tileSize*0.08f;
                }
                else if(darknessRadius < DEFAULT_DARKNESS_RADIUS){
                    darknessRadius += gp.tileSize*0.1f;
                }
            }

            @Override
            public void setAction(){ setRandomDirection(); }

            @Override
            public void onFollowing(){
                targetTileX = player.getHitboxTileX();
                targetTileY = player.getHitboxTileY();
                super.onFollowing();
            }
        });

        //-------------------------------------PİL---------------------------------------
        entities.add(new Entity(0.5f, 0.5f){

            //hızlar (karo/update)
            private static final float WALK_SPEED = 0.04f;   //dolaşırken
            private static final float DASH_SPEED = 0.12f;   //takipte (atılma hızı)
            private static final float ALIGN_SPEED = 0.04f;  //hizalanırken (yavaş)

            //süreler (update sayısı, 60 update/sn varsayımıyla)
            private static final int CHARGE_FRAMES = 50;        //şarj olma (bekleme) süresi
            private static final int ALIGN_MAX_FRAMES = 90;     //hizalanma zaman aşımı
            private static final int DASH_EXTRA_FRAMES = 10;    //atılma zaman aşımına eklenen pay
            private static final int STUCK_FRAMES = 3;          //bu kadar kare hiç ilerlenmezse atılma biter

            //oyuncuyu bu kadar dönüş ART ARDA göremezse (oyuncuya hedeflenemezse) takibi bırakır
            private static final int MAX_UNSEEN_TURNS = 5;

            //toleranslar (karo cinsinden)
            private static final float ALIGN_TOLERANCE = 0.02f; //hizalanmış sayılma
            private static final float REACH_TOLERANCE = 0.02f; //hedefe ulaşılmış sayılma
            private static final float LANE_MARGIN = 0.02f;     //koridor kenarına yaklaşma payı

            //hazırlanma/atılma durumları
            private static final int ALIGNING = 0, CHARGING = 1, DASHING = 2;
            private int pilState = ALIGNING;
            private int stateCounter;

            /** true = hedefe kilitli (hizalanma, şarj ve atılma). Atılma bitince false olur. */
            private boolean isCharged = false;
            /** Kilitlenen hedef (hitbox merkezi koordinatı). Ulaşılana kadar değişmez
             *  (oyuncuya hedeflendiyse sadece atılma yönünde uzayabilir). */
            private float targetX, targetY;
            /** Kilitlenen hedef oyuncunun kendisi mi (true), yoksa köşe karesi mi (false)? */
            private boolean targetsPlayer = false;
            /** Art arda oyuncuya hedeflenemeyen dönüş sayısı. */
            private int unseenTurns = 0;

            //atılma yönü (hizalanma bitince belirlenir)
            private boolean dashAlongX;   //true: yatay, false: dikey
            private float dashSign = 1f;  //+1: sağ/aşağı, -1: sol/yukarı
            private float dashFrames;     //atılmanın kalan zaman aşımı (kare)

            //takılma kontrolü
            private float lastX, lastY;
            private int stuckFrames;

            {
                speed = WALK_SPEED;
                setImageIDs(BATTERY_IDS);

                minStartFollowTileDistance = 25;

                this.hitboxOffsetX = 0.0f;
                this.hitboxOffsetY = 0.15f;
                this.hitboxHalfW = 0.25f;
                this.hitboxHalfH = 0.15f;

                tMin = 0.3f;
                tMax = 0.5f;

                damage = 2;

            }


            @Override
            protected void onFollowStart(){
                speed = DASH_SPEED;
                speedX = 0f;
                speedY = 0f;
                isCharged = false; //ilk onFollowing() hedefi belirleyip hazırlanmayı başlatır
                unseenTurns = 0;
            }
            @Override
            protected void onFollowEnd(){
                super.onFollowEnd();
                speed = WALK_SPEED;
                isCharged = false;
                unseenTurns = 0;
            }
            @Override
            public boolean shouldStartFollow(){
                if(player.getShortestDistanceSquare(this) >
                        minStartFollowTileDistance * minStartFollowTileDistance) return false;

                //aynı karede iken yol dönmez
                if(isInSameTile(player)){
                    if(path != null) path.clear();
                    return true;
                }

                if(!setPath(player)) return false;

                return path.size() <= minStartFollowTileDistance && isPathStraight(path);
            }
            @Override
            public boolean shouldStopFollowing(){
                //hedefe kilitliyken (hizalanma, şarj, atılma) bırakma
                if(isCharged) return false;

                //mesafe artık önemli değil: oyuncuyu art arda MAX_UNSEEN_TURNS dönüş göremediyse bırak
                if(unseenTurns >= MAX_UNSEEN_TURNS) return true;

                //oyuncuyla aynı karedeyken bırakma
                if(isInSameTile(player)) return false;

                //oyuncuya yol yoksa bırak
                return !setPath(player);
            }
            @Override
            public void onFollowing(){

                //hedefe kilitli değilse yeni hedef belirle ve hazırlan
                if(!isCharged){
                    if(!lockTarget()){
                        //yol yok, takip shouldStopFollowing'de bırakılacak
                        goToPoint(0f, 0f);
                        return;
                    }
                    isCharged = true;
                    pilState = ALIGNING;
                    stateCounter = ALIGN_MAX_FRAMES;

                    //oyuncuya hedeflendiyse "gördü" say, değilse görmeden bir dönüş daha yaptı
                    if(targetsPlayer) unseenTurns = 0;
                    else unseenTurns++;
                }

                switch(pilState){
                    case ALIGNING: align();  break;
                    case CHARGING: charge(); break;
                    default:       dash();   break;
                }
            }

            /**
             * Hedefi belirler (ulaşılana kadar değişmez, bkz. extendTargetAhead):
             *  - oyuncu tam karşıdaysa (yol baştan sona tek doğrultuda): oyuncunun şu anki hitbox
             *    merkezi (koridor şeridine sığacak şekilde yan koordinatı kırpılır),
             *  - değilse: yolun ilk adım yönünde giden en uzak düz karenin merkezi.
             * targetsPlayer bu iki durumu ayırır. Yol yoksa false döner.
             */
            private boolean lockTarget(){
                final float playerX = player.getHitbox().centerX();
                final float playerY = player.getHitbox().centerY();

                //oyuncu ile aynı karede, doğrudan oyuncunun hitbox merkezi
                if(isInSameTile(player)){
                    if(path != null) path.clear();
                    targetX = playerX;
                    targetY = playerY;
                    targetsPlayer = true;
                    return true;
                }

                if(!setPath(player)) return false;

                final int tileX = getHitboxTileX();
                final int tileY = getHitboxTileY();

                //ilk adımın yönünde art arda giden son düğüm
                int dirX = path.get(0).x - tileX;
                int dirY = path.get(0).y - tileY;
                int runEnd = 0;
                for(int i = 1; i < path.size(); i++){
                    PathFinder.Node prev = path.get(i - 1);
                    PathFinder.Node cur  = path.get(i);
                    if(cur.x - prev.x != dirX || cur.y - prev.y != dirY) break;
                    runEnd = i;
                }

                if(runEnd < path.size() - 1){
                    //oyuncu düz çizgide değil: düz bölümün son karesinin merkezi
                    PathFinder.Node end = path.get(runEnd);
                    targetX = end.x + 0.5f;
                    targetY = end.y + 0.5f;
                    targetsPlayer = false;
                    return true;
                }

                //oyuncu tam karşıda: hitbox merkezi, ama yan koordinat koridora sığacak şekilde kırpılır
                //(oyuncunun hitbox'ı bizimkinden küçük, yoksa duvara girip hizalanamazdık)
                if(dirX != 0){
                    float lo = tileY + hitboxHalfH + LANE_MARGIN;
                    float hi = tileY + 1f - hitboxHalfH - LANE_MARGIN;
                    targetX = playerX;
                    targetY = Math.max(lo, Math.min(hi, playerY));
                }
                else{
                    float lo = tileX + hitboxHalfW + LANE_MARGIN;
                    float hi = tileX + 1f - hitboxHalfW - LANE_MARGIN;
                    targetX = Math.max(lo, Math.min(hi, playerX));
                    targetY = playerY;
                }
                targetsPlayer = true;
                return true;
            }

            /** Farkın küçük olduğu eksen hizalanır, büyük olduğu eksende atılılacak. */
            private void align(){
                float dx = targetX - getHitbox().centerX();
                float dy = targetY - getHitbox().centerY();

                boolean dashHorizontal = (Math.abs(dx) >= Math.abs(dy));
                float perp = (dashHorizontal) ? dy : dx;

                if(Math.abs(perp) > ALIGN_TOLERANCE && --stateCounter > 0){
                    //speed şu an DASH_SPEED olduğu için fark ALIGN_SPEED ile kırpılıp verilir (yavaş hizalanma)

                    float step = Math.signum(perp) * Math.min(ALIGN_SPEED , Math.abs(perp));
                    if(dashHorizontal) goToPoint(0f, step);
                    else goToPoint(step, 0f);
                    return;
                }

                //hizalandı: atılma yönünü kaydet, yüzünü hedefe çevir ve şarj olmaya başla
                dashAlongX = dashHorizontal;
                dashSign = ((dashHorizontal ? dx : dy) >= 0f) ? 1f : -1f;

                if(dashHorizontal) direction = (dx >= 0) ? Direction.RIGHT : Direction.LEFT;
                else               direction = (dy >= 0) ? Direction.DOWN  : Direction.UP;

                goToPoint(0f, 0f);
                pilState = CHARGING;
                stateCounter = CHARGE_FRAMES;
            }

            private void charge(){
                goToPoint(0f, 0f);

                if(--stateCounter > 0) return;

                //atılma başlıyor: o anki mesafeye göre zaman aşımı belirle
                float dx = targetX - getHitbox().centerX();
                float dy = targetY - getHitbox().centerY();
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                dashFrames = dist / speed + DASH_EXTRA_FRAMES;
                lastX = x;
                lastY = y;
                stuckFrames = 0;
                pilState = DASHING;
            }

            /** Kilitli hedefe doğru DASH_SPEED ile gider. */
            private void dash(){
                //oyuncuya hedeflendiyse ve oyuncu aynı koridorda ileri doğru ilerliyorsa hedefi uzat
                if(targetsPlayer) extendTargetAhead();

                //takılma kontrolü: art arda STUCK_FRAMES karede hiç ilerlenmediyse (duvara dayandı) bitir
                float moved = Math.abs(x - lastX) + Math.abs(y - lastY);
                lastX = x;
                lastY = y;
                stuckFrames = (moved < 0.001f) ? stuckFrames + 1 : 0;

                float dx = targetX - getHitbox().centerX();
                float dy = targetY - getHitbox().centerY();
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                if(dist <= REACH_TOLERANCE || stuckFrames >= STUCK_FRAMES || (dashFrames -= 1f) <= 0f){
                    //hedefe ulaşıldı (ya da takıldı/zaman aşımı): kilit açılır, takip bırakma kontrolü yapılabilir
                    goToPoint(0f, 0f);
                    isCharged = false;
                    return;
                }

                goToPoint(dx, dy);
            }

            /**
             * Atılırken oyuncu hedefi geçip aynı koridorda atılma yönünde ilerlediyse hedefi ona kadar
             * uzatır, böylece Pil eski konumda yarım kalıp durmaz. Sadece İLERİ yönde uzar, yan
             * koordinat (şerit) sabit kalır: oyuncu sağa sola kaçarak hedefi sürekli değiştiremez.
             * Uzatma için: oyuncu hâlâ bizim şeridimizde olmalı ve aramızda duvar olmamalı.
             */
            private void extendTargetAhead(){
                final float playerX = player.getHitbox().centerX();
                final float playerY = player.getHitbox().centerY();

                //oyuncu hâlâ bizim koridor şeridinde mi?
                final int lane = dashAlongX ? getHitboxTileY() : getHitboxTileX();
                final int playerLane = (int) Math.floor(dashAlongX ? playerY : playerX);
                if(playerLane != lane) return;

                //oyuncu hedefin ötesine (atılma yönünde) geçti mi?
                final float playerAlong = dashAlongX ? playerX : playerY;
                final float targetAlong = dashAlongX ? targetX : targetY;
                final float ahead = (playerAlong - targetAlong) * dashSign;
                if(ahead <= 0f) return;

                //aramızda duvar yoksa hedefi uzat
                final int from = dashAlongX ? getHitboxTileX() : getHitboxTileY();
                final int to = (int) Math.floor(playerAlong);
                if(!isLaneClear(lane, from, to, dashAlongX)) return;

                if(dashAlongX) targetX = playerAlong;
                else           targetY = playerAlong;

                dashFrames += ahead / speed; //uzayan mesafe kadar zaman aşımı payı
            }

            /** Şeritte (lane) from ile to karesi arasında (dahil) duvar var mı? */
            private boolean isLaneClear(int lane, int from, int to, boolean alongX){
                int lo = Math.min(from, to);
                int hi = Math.max(from, to);

                for(int t = lo; t <= hi; t++){
                    if(alongX ? isWall(t, lane) : isWall(lane, t)) return false;
                }
                return true;
            }

            /**
             * Yol, canavarın KENDİ karesinden başlayarak tek bir doğrultuda mı ilerliyor?
             * Sadece sondan istenilen kadar adım yönü farklı olabilir (oyuncunun karesi
             * köşeyi dönüyor olabilir). path[0] canavarın bir sonraki karesidir,
             * canavarın kendi karesi listede yoktur. Sadece takibe BAŞLARKEN kullanılır.
             */
            private boolean isPathStraight(ArrayList<PathFinder.Node> path){
                if(path == null || path.isEmpty()) return false;

                int px = getHitboxTileX();
                int py = getHitboxTileY();
                int dirX = 0;
                int dirY = 0;
                int last = path.size() - 1;

                for(int i = 0; i <= last; i++){
                    PathFinder.Node n = path.get(i);
                    int stepX = n.x - px;
                    int stepY = n.y - py;

                    if(i == 0){
                        //ilk adım yönü belirler
                        dirX = stepX;
                        dirY = stepY;
                    }
                    else if((stepX != dirX || stepY != dirY) && i != last){
                        //son adım hariç dönüş yok
                        return false;
                    }

                    px = n.x;
                    py = n.y;
                }

                return true;
            }

            @Override
            public void setAction(){
                if(onFollow) return;
                setRandomDirection();
            }

            @Override
            public void respawn(){
                super.respawn(); //onFollow, hız ve path sıfırlanır
                speed = WALK_SPEED;
                isCharged = false;
                targetsPlayer = false;
                unseenTurns = 0;
                stuckFrames = 0;
                pilState = ALIGNING;
                stateCounter = 0;
            }
        });

        //-------------------------------------MUZ---------------------------------------
        entities.add(new Entity(0.5f, 0.5f){

            private static final float WANDER_SPEED = 0.025f; //boş dolaşırken
            private static final float CHASE_SPEED  = 0.070f; //takip hızı

            private static final float ACCEL    = 0.001f; //hızlanma
            private static final float FRICTION = 0.002f; //yavaşlama

            private float desiredSpeed = WANDER_SPEED;

            //boşta dolaşma yönü (-1, 0, 1) ve duvara çarptı mı
            private float wanderDirX, wanderDirY;
            private boolean hitWall = false;

            {
                setImageIDs(BANANA_IDS);

                minStartFollowTileDistance = 10;
                maxFollowDistance = 15;

                this.hitboxOffsetX = 0.0f;
                this.hitboxOffsetY = 0.0f;
                this.hitboxHalfW = 0.25f;
                this.hitboxHalfH = 0.3f;

                tMin = 0.0f; //4
                tMax = 0.1f; //6

                //hizalanma payı büyük olmalı
                ALIGN_EPS = 0.3f;

                damage = 3;
            }

            @Override
            protected void onFollowStart(){
                desiredSpeed = CHASE_SPEED;
            }

            @Override
            protected void onFollowEnd(){
                desiredSpeed = WANDER_SPEED;
                wanderDirX = 0f;
                wanderDirY = 0f;
                hitWall = false;
            }

            @Override
            protected void onFollowing(){
                targetTileX = player.getHitboxTileX();
                targetTileY = player.getHitboxTileY();
                super.onFollowing();
            }

            /**
             * Hedefe doğru (dx, dy) yönünde istenen hıza KADEMELİ yaklaşır. (0, 0) ya da çok küçük fark =
             * istenen hız sıfır, yani sürtünmeyle yavaşlar. Hedefi aşabilir (kasıtlı, buz gibi kayar).
             * Hızı doğrudan eksen başına değiştirdiği için speedX/speedY canlının gerçek hızıdır.
             */
            @Override
            protected void goToPoint(float dx, float dy){
                float dist = (float)Math.sqrt(dx * dx + dy * dy);

                float targetVx = 0f;
                float targetVy = 0f;
                if(dist > 0.003f){
                    targetVx = dx / dist * desiredSpeed;
                    targetVy = dy / dist * desiredSpeed;
                }

                speedX = approach(speedX, targetVx);
                speedY = approach(speedY, targetVy);
            }

            /** v'yi target'a bir adım yaklaştırır. Aynı yönde hızlanıyorsak ACCEL, yavaşlıyor ya da
             *  yön değiştiriyorsak (daha yavaş) FRICTION kullanılır. */
            private float approach(float v, float target){
                boolean speedingUp = (Math.abs(target) > Math.abs(v) && v * target >= 0f);
                float rate = (speedingUp) ? ACCEL : FRICTION;

                if(v < target) return Math.min(v + rate, target);
                if(v > target) return Math.max(v - rate, target);
                return v;
            }

            /** Takipte değilken: rastgele bir yöne doğru yavaşça gider, duvara çarpınca yön değiştirir. */
            @Override
            protected void setAction(){
                if(hitWall || (wanderDirX == 0f && wanderDirY == 0f)){
                    hitWall = false;

                    switch(r.nextInt(4)){
                        case 0:  wanderDirX = -1f; wanderDirY =  0f; break; //sol
                        case 1:  wanderDirX =  1f; wanderDirY =  0f; break; //sağ
                        case 2:  wanderDirX =  0f; wanderDirY = -1f; break; //yukarı
                        default: wanderDirX =  0f; wanderDirY =  1f; break; //aşağı
                    }
                }

                goToPoint(wanderDirX, wanderDirY);
            }

            /** Duvara çarpınca o eksendeki hız sıfırlanır ve dolaşma yönü yeniden seçilir. */
            @Override
            protected boolean move(float dx, float dy){
                boolean isTouched = false;

                if(dx != 0){
                    x += dx;

                    if(isTouchingToWall()){
                        //hitbox'ın kenarı duvarın kenarına gelecek şekilde x hesaplanır (offset dahil)
                        isTouched = true;
                        RectF h = getHitbox();
                        if(dx > 0) x = (float) Math.floor(h.right) - EPS - hitboxOffsetX - hitboxHalfW;
                        else x = (float) Math.floor(h.left) + 1f + EPS - hitboxOffsetX + hitboxHalfW;

                        speedX = 0f;
                    }
                }

                if(dy != 0){
                    y += dy;

                    if(isTouchingToWall()){
                        isTouched = true;
                        RectF h = getHitbox();
                        if(dy > 0) y = (float) Math.floor(h.bottom) - EPS - hitboxOffsetY - hitboxHalfH;
                        else y = (float) Math.floor(h.top) + 1f + EPS - hitboxOffsetY + hitboxHalfH;

                        speedY = 0f;
                    }
                }

                if(isTouched) hitWall = true;
                return isTouched;
            }

            @Override
            public void respawn(){
                super.respawn(); //hız, takip ve path sıfırlanır
                desiredSpeed = WANDER_SPEED;
                wanderDirX = 0f;
                wanderDirY = 0f;
                hitWall = false;
            }
        });

        //----------------------------------DAMACANA------------------------------------
        entities.add(new Entity(0.5f, 0.5f){

            {
                speed = 0.01f;
                setImageIDs(CARBOY_IDS);

                this.hitboxOffsetX = 0.0f;
                this.hitboxOffsetY = 0.25f;
                this.hitboxHalfW = 0.25f;
                this.hitboxHalfH = 0.2f;

                minStartFollowTileDistance = 8;
                maxFollowDistance = 45;
                targetAnimationTime = 30;

                tMin = 0.7f;
                tMax = 0.9f;

                damage = 10202; //oyuncuya tek atacak
            }

            @Override
            public void setAction(){ setRandomDirection(); }

            @Override
            public void onFollowing(){
                targetTileX = player.getHitboxTileX();
                targetTileY = player.getHitboxTileY();
                super.onFollowing();
            }
        });
    }

    //----------------------------HARİTA / ÇARPIŞMA----------------------------

    /** Harita dışı da duvar sayılır. */
    private static boolean isWallAt(boolean[] tiles, int x, int y){
        if(x < 0 || y < 0 || x >= MAZE_W || y >= MAZE_H) return true;
        return tiles[y * MAZE_W + x];
    }

    private boolean isWall(int x, int y){
        return isWallAt(tiles, x, y);
    }

    //----------------------------MAZE GENERATOR----------------------------

    private static final int[] DX = { 0,  0, -2,  2 };
    private static final int[] DY = {-2,  2,  0,  0 };
    private static final java.util.Random r = new java.util.Random();

    /**
     * Zorluk ayarı (0.0 - 1.0): her adımda yeni yolun, en son açılan hücreden mi yoksa
     * rastgele bir eski hücreden mi devam edeceğini belirler.
     *   0.0 -> uzun, kıvrımlı, az dallı koridorlar (kolay)
     *   0.3 -> 0.5 -> dengeli: hem uzun yollar hem bol çıkmaz dal
     *   1.0 -> çok sayıda kısa çıkmaz dal (Prim benzeri)
     */
    private static final float BRANCH_CHANCE = 0.5f;

    /**
     * Tek bir çıkış yolu olmaması için kaç duvarın rastgele silineceğini belirler.
     * Yalnızca "anlamlı" duvarlar silinir (bkz. MIN_LOOP_LENGTH), uygun duvar yetmezse
     * bu sayıdan daha az duvar açılabilir.
     */
    private static final int OPEN_WALL_COUNT = 15;

    /**
     * Bir duvarın silinmeye değer olması için, duvarın iki yanındaki hücreler arasındaki mevcut
     * en kısa yolun en az kaç hücre adımı (1 adım = 2 karo) olması gerektiği.
     * Küçük değerler, duvarın hemen etrafından dolanan işe yaramaz mini döngüler oluşturur
     * (ör. tek bir sütunun çevresindeki 3 adımlık yol: sütun tek başına kalır, görüntü bozulur).
     */
    private static final int MIN_LOOP_LENGTH = 10;

    /**
     * Rastgele labirenti düzleştirilmiş 1D boolean dizisine üretir
     * (Growing Tree, iteratif; özyineleme olmadığı için StackOverflow olmaz).
     * Sonunda OPEN_WALL_COUNT kadar anlamlı duvarı silerek labirente alternatif yollar ekler.

     * Hücreler çift koordinatlardadır ve (0,0) her zaman açıktır.
     * Duvarlar tek koordinatlardadır. width ve height tek sayı olmalıdır.
     *
     * @param maze true = duvar, false = boş yol (uzunluk: width * height)
     */
    public static void generateMaze(int width, int height, boolean[] maze, short[] dArray){

        //her yer duvar
        java.util.Arrays.fill(maze, true);
        if(width < 1 || height < 1) return;

        //aktif hücre listesi: her hücre en fazla bir kez eklenir
        int cellCount = ((width + 1) / 2) * ((height + 1) / 2);
        int[] active = new int[cellCount];
        int size = 0;

        int[] dirs = new int[4];

        //(0,0) hücresinden başla
        maze[0] = false;
        active[size++] = 0;

        while(size > 0){
            //BRANCH_CHANCE olasılıkla rastgele bir aktif hücre, yoksa en yeni hücre
            int idx = (r.nextFloat() < BRANCH_CHANCE) ? r.nextInt(size) : size - 1;
            int cur = active[idx];
            int cx = cur % width;
            int cy = cur / width;

            //henüz açılmamış (hâlâ duvar olan) komşu hücreleri topla
            int count = 0;
            for(int d = 0; d < 4; d++){
                int nx = cx + DX[d];
                int ny = cy + DY[d];
                if(nx >= 0 && nx < width && ny >= 0 && ny < height && maze[ny * width + nx])
                    dirs[count++] = d;
            }

            if(count == 0){
                //bu hücrenin gidecek yeri kalmadı, listeden çıkar (sıra bozulmasın diye kaydırarak)
                System.arraycopy(active, idx + 1, active, idx, size - idx - 1);
                size--;
                continue;
            }

            int d = dirs[r.nextInt(count)];
            int nx = cx + DX[d];
            int ny = cy + DY[d];

            //aradaki duvarı ve yeni hücreyi aç
            maze[(cy + DY[d] / 2) * width + (cx + DX[d] / 2)] = false;
            maze[ny * width + nx] = false;

            active[size++] = ny * width + nx;
        }

        openExtraWalls(width, height, maze);
        calculateD(width, height, maze, dArray);
    }

    /**
     * Rastgele OPEN_WALL_COUNT kadar anlamlı duvarı siler.

     * Aday duvarlar: iki hücrenin arasındaki kapalı geçitler, yani (tek, çift) ve (çift, tek)
     * koordinatlı duvarlar. (tek, tek) sütunlar ve harita kenarı hiçbir zaman aday olmaz.

     * Bir aday ancak iki yanındaki hücreler arasındaki mevcut en kısa yol en az MIN_LOOP_LENGTH
     * adım ise silinir. Bu şart; sütun etrafındaki mini döngüleri ve ince duvarların uç
     * parçalarını (üç tarafı açık duvar ucu) zaten eler, çünkü onların etrafındaki yol 3 adımdır.
     * Geriye kalanlar gerçekten bir kestirme/alternatif yol açan duvarlardır.
     */
    private static void openExtraWalls(int width, int height, boolean[] maze){
        if(OPEN_WALL_COUNT <= 0) return;

        //---------------------------ADAYLARI TOPLA---------------------------
        int[] candidates = new int[width * height];
        int candidateCount = 0;

        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                boolean oddX = (x & 1) == 1;
                boolean oddY = (y & 1) == 1;
                if(oddX == oddY) continue;           //hücre (çift,çift) ya da sütun (tek,tek)
                if(!maze[y * width + x]) continue;   //zaten açık

                //iki yanındaki hücre de haritanın içinde olmalı
                if(oddX){ if(x + 1 >= width) continue; }
                else    { if(y + 1 >= height) continue; }

                candidates[candidateCount++] = y * width + x;
            }
        }

        //rastgele sıra (Fisher-Yates)
        for(int i = candidateCount - 1; i > 0; i--){
            int j = r.nextInt(i + 1);
            int temp = candidates[i];
            candidates[i] = candidates[j];
            candidates[j] = temp;
        }

        //---------------------------UYGUN OLANLARI AÇ---------------------------
        int[] dist  = new int[width * height];
        int[] queue = new int[width * height];
        int opened = 0;

        for(int i = 0; i < candidateCount && opened < OPEN_WALL_COUNT; i++){
            int slot = candidates[i];
            int sx = slot % width;
            int sy = slot / width;

            //duvarın iki yanındaki hücreler
            int a, b;
            if((sx & 1) == 1){
                a = sy * width + (sx - 1);
                b = sy * width + (sx + 1);
            }
            else{
                a = (sy - 1) * width + sx;
                b = (sy + 1) * width + sx;
            }

            if(!isLoopLongEnough(maze, width, height, a, b, dist, queue)) continue;

            maze[slot] = false;
            opened++;
        }
    }

    /**
     * from ile to hücreleri arasındaki mevcut en kısa yol MIN_LOOP_LENGTH adımdan KISA mı diye
     * bakar (en fazla o derinliğe kadar BFS). Kısaysa false (silmeye değmez), değilse true döner.
     * dist ve queue, tekrar tekrar bellek ayırmamak için dışarıdan gelen çalışma dizileridir.
     */
    private static boolean isLoopLongEnough(boolean[] maze, int width, int height,
                                            int from, int to, int[] dist, int[] queue){

        java.util.Arrays.fill(dist, -1);
        int head = 0;
        int tail = 0;
        queue[tail++] = from;
        dist[from] = 0;

        while(head < tail){
            int cur = queue[head++];
            if(cur == to) return false; //aradaki yol zaten kısa

            int d = dist[cur];
            if(d + 1 >= MIN_LOOP_LENGTH) continue; //daha derine bakmaya gerek yok

            int cx = cur % width;
            int cy = cur / width;

            for(int dir = 0; dir < 4; dir++){
                int nx = cx + DX[dir];
                int ny = cy + DY[dir];
                if(nx < 0 || nx >= width || ny < 0 || ny >= height) continue;

                //aradaki geçit kapalıysa geçilemez
                if(maze[(cy + DY[dir] / 2) * width + (cx + DX[dir] / 2)]) continue;

                int ni = ny * width + nx;
                if(dist[ni] != -1) continue;

                dist[ni] = d + 1;
                queue[tail++] = ni;
            }
        }

        return true;
    }

    private final Paint flashlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PorterDuffXfermode dstOutMode = new PorterDuffXfermode(PorterDuff.Mode.DST_OUT);

    private static void calculateD(int width, int height, boolean[] maze, short[] d){
        java.util.Arrays.fill(d, (short)-1);

        //az önce açılan indexler, yani kenarları hala açılabilir
        Queue<Integer> lastOpened = new ArrayDeque<>();
        lastOpened.add(0);
        d[0] = 0; //başlangıçı 0 olarak işaretle

        while(!lastOpened.isEmpty()){
            Integer temp = lastOpened.poll();
            if(temp == null) break;
            int currentI = temp;

            int x = currentI % width;
            int y = currentI / width;
            short nextValue = (short) (d[currentI] + 1);
            //yukarıya bak

            int topI = (y - 1) * width + x;
            if(y - 1 >= 0 && d[topI] == -1 && !maze[topI]){
                //eğer sınırlar içindeyse, henüz açılmamışsa ve duvar değilse

                d[topI] = nextValue; //aç
                lastOpened.add(topI); //kuyruğa ekle
            }
            //aşağıya bak
            int bottomI = (y + 1) * width + x;
            if(y + 1 < height && d[bottomI] == -1 && !maze[bottomI]){
                d[bottomI] = nextValue;
                lastOpened.add(bottomI);
            }
            //sola bak
            int leftI = y * width + x - 1;
            if(x - 1 >= 0 && d[leftI] == -1 && !maze[leftI]){
                d[leftI] = nextValue;
                lastOpened.add(leftI);
            }
            //sağa bak
            int rightI = y * width + x + 1;
            if(x + 1 < width && d[rightI] == -1 && !maze[rightI]){
                d[rightI] = nextValue;
                lastOpened.add(rightI);
            }
        }
    }

    public void drawFlashlightEffect(Canvas canvas, float centerX, float centerY, float radius) {
        // 1. Çizim katmanını başlatıyoruz (Xfermode'un doğru çalışması için şarttır)
        canvas.saveLayer(0f, 0f, canvas.getWidth(), canvas.getHeight(), null);

        // 2. Öncelikle ekranı/tuvali tamamen siyah (karanlık) katmanla boyuyoruz
        canvas.drawColor(Color.BLACK);

        // 3. Işık (delik) efekti için RadialGradient ve Paint ayarlarını yapıyoruz
        flashlightPaint.setShader(new RadialGradient(
                centerX, centerY, radius,
                new int[]{Color.BLACK, Color.TRANSPARENT},
                new float[]{0.0f, 1.0f},
                Shader.TileMode.CLAMP
        ));
        flashlightPaint.setXfermode(dstOutMode);

        // 4. Belirtilen konuma daireyi çizerek siyahlığı deliyoruz/şeffaflaştırıyoruz
        canvas.drawCircle(centerX, centerY, radius, flashlightPaint);

        // 5. Xfermode etkisini diğer çizimleri bozmasın diye katmanı kapatıyoruz
        canvas.restore();
    }
}

class PathFinder{

    private static final int MAX_STEP_COUNT = 800;

    public static class Node{
        public Node parent;
        public int x, y;
        public int gCost, hCost, fCost;
        public boolean solid, open, checked;

        public Node(int x, int y){
            this.x = x;
            this.y = y;
        }
    }

    private final Node[] nodes;
    private final ArrayList<Node> openList = new ArrayList<>();
    private Node startNode, goalNode, currentNode;
    private boolean goalReached = false;
    private int step = 0;
    private final int width, height;

    public PathFinder(int width, int height){
        this.width  = width;
        this.height = height;

        nodes = new Node[width * height];

        for(int y = 0; y < height; y++)
            for(int x = 0; x < width; x++)
                nodes[y * width + x] = new Node(x, y);
    }

    public void resetNodes(){
        for(Node node : nodes){
            node.open = false;
            node.checked = false;
            node.solid = false;
        }

        openList.clear();
        goalReached = false;
        step = 0;
    }

    public void setNodes(int startCol, int startRow, int goalCol, int goalRow, boolean[] tiles){
        if(tiles.length != width * height)
            throw new RuntimeException("gönderilen karo haritası ile belirtilen genişlik ve yükseklik çarpımı uyuşmuyor!");
        resetNodes();

        //Başlangıç ve hedef node'larını ayarla
        startNode = this.nodes[startRow * width + startCol];
        currentNode = startNode;
        goalNode = this.nodes[goalRow * width + goalCol];
        openList.add(currentNode);

        int x = 0;
        int y = 0;

        while(x < width && y < height){

            //katı node'ları belirle
            this.nodes[y * width + x].solid = tiles[y * width + x];

            //cost'u ayarla
            getCost(this.nodes[y * width + x]);

            x++;
            if(x == width){
                x = 0;
                y++;
            }
        }
    }
    public void getCost(Node node){

        //G cost
        int xDistance = Math.abs(node.x - startNode.x);
        int yDistance = Math.abs(node.y - startNode.y);
        node.gCost = xDistance + yDistance;

        //H cost
        xDistance = Math.abs(node.x - goalNode.x);
        yDistance = Math.abs(node.y - goalNode.y);
        node.hCost = xDistance + yDistance;

        //F cost
        node.fCost = node.gCost + node.hCost;
    }

    public boolean search(ArrayList<Node> nodes){
        if(nodes == null) return false;
        nodes.clear();

        while(!goalReached && step < MAX_STEP_COUNT){

            int x = currentNode.x;
            int y = currentNode.y;

            //mevcut node'u kontrol et
            currentNode.checked = true;
            openList.remove(currentNode);

            //sol node'unu aç
            if(x - 1 >= 0) openNode(this.nodes[y * width + (x - 1)]);
            //yukarı node'unu aç
            if(y - 1 >= 0) openNode(this.nodes[(y - 1) * width + x]);
            //sağ node'unu aç
            if(x + 1 < width) openNode(this.nodes[y * width + (x + 1)]);
            //aşağı node'unu aç
            if(y + 1 < height) openNode(this.nodes[(y + 1) * width + x]);

            //en iyi node'u bul
            int bestNodeI = 0;
            int bestNodeFCost = Integer.MAX_VALUE;
            for(int i = 0; i < openList.size(); i++){
                Node currentNode = openList.get(i);

                //node'un fCost'unun daha iyi olup olmadığını kontrol et
                if(currentNode.fCost < bestNodeFCost){
                    bestNodeI = i;
                    bestNodeFCost = currentNode.fCost;
                }
                else if(currentNode.fCost == bestNodeFCost &&
                currentNode.gCost < openList.get(bestNodeI).gCost){
                    bestNodeI = i;
                }
            }

            if(openList.isEmpty())
                break;

            currentNode = openList.get(bestNodeI);

            if(currentNode == goalNode){
                goalReached = true;
                trackThePath(nodes);
                break;
            }
            step++;
        }

        return goalReached;
    }
    public void openNode(Node node){
        int newG = currentNode.gCost + 1;

        if(node.checked || node.solid) return;
        if(node.open && newG >= node.gCost) return;

        node.gCost = newG; node.fCost = newG + node.hCost;
        node.parent = currentNode;

        if(node.open) return;

        node.open = true;
        openList.add(node);
    }
    public void trackThePath(ArrayList<Node> nodes){
        Node current = goalNode;

        while(current != startNode){
            nodes.add(0, current);
            current = current.parent;
        }

    }
}