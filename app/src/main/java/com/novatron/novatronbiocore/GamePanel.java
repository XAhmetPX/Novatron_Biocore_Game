package com.novatron.novatronbiocore;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.NonNull;

import com.novatron.novatronbiocore.Games.CardMatching;
import com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.FRCGameRebuilt;
import com.novatron.novatronbiocore.Games.Game;
import com.novatron.novatronbiocore.Games.MazeEscape;
import com.novatron.novatronbiocore.Games.RightTrashGame;
import com.novatron.novatronbiocore.Games.TakingRootGame;
import com.novatron.novatronbiocore.tools.AudioSystem;
import com.novatron.novatronbiocore.tools.ImageStore;
import com.novatron.novatronbiocore.tools.UI;
import com.novatron.novatronbiocore.tools.VibrationManager;

public class GamePanel extends SurfaceView implements Runnable, SurfaceHolder.Callback{

    private static final int GAME_SELECT_UP_ROW = 4;

    public int SCREEN_WIDTH, SCREEN_HEIGHT;
    public int tileSize = 80; //varsayılan değer, boş kalmasın diye
    private Thread gameThread;
    private boolean isRunning = false;
    private boolean isPaused = false;
    private final SurfaceHolder holder;
    private final PerfStats perf = new PerfStats();

    public final ImageStore imageStore;
    public final AudioSystem audioSystem;
    public final VibrationManager vibration;
    public final UI ui;
    public Game currentGame;
    public final Game[] games = new Game[]{
           new FRCGameRebuilt(this), new RightTrashGame(this), new TakingRootGame(this), new CardMatching(this),
            new MazeEscape(this)
    };

    public enum GameState{
        TITLE, TITLE_TO_STATE, STATE_TO_STATE, GAME, GAME_SELECT, START_COUNTER, GAME_OVER
    }
    public GameState gameState = GameState.TITLE;
    public GameState nextState, lastState;

    //butonlar
    public Rect btnPlay, btnSettings; //title
    public Rect[] gameButtons = new Rect[games.length];

    public GamePanel(Context context){
        super(context);

        //ekrana çizim yapmamızı sağlayan tuvali yönetir
        holder = getHolder();
        holder.addCallback(this);

        imageStore = new ImageStore(getContext());
        audioSystem = new AudioSystem(getContext());
        vibration = new VibrationManager(getContext());
        ui = new UI(this);
    }

    //SurfaceView hazır olduğunda oyun döngüsünü başlatır
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder){
        if(gameThread == null || !gameThread.isAlive()){
            //eğer Thread hiç oluşturulmadıysa veya bir şekilde öldüyse sıfırdan başlat
            isRunning = true;
            isPaused = false;
            if(gameState == GameState.GAME && currentGame != null){
                currentGame.preloadGame();
                currentGame.reset();
            }
            gameThread = new Thread(this);
            gameThread.start();
        }
        else{
            //Thread zaten arka planda yaşıyorsa, onu uykusundan uyandır
            synchronized(this){
                isPaused = false;
                this.notify(); //wait() durumundaki thread'i uyandırır
            }
        }
    }

    //uygulama arka plana atıldığında tetiklenir
    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder){
        synchronized(this){
            isPaused = true;
        }
    }

    //ekran döndürüldüğünde veya boyutu değiştiğinde burası tetiklenir(ilk kez üretildiğinde de)
    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height){
        SCREEN_WIDTH = width;
        SCREEN_HEIGHT = height;
        tileSize = SCREEN_HEIGHT / 9;

        //--------HER ZAMAN YÜKLÜ OLACAK RESİMLER--------
        imageStore.preloadImage(R.drawable.logo_full);
        imageStore.preloadImage(R.drawable.logo_left);
        imageStore.preloadImage(R.drawable.logo_right);

        //-------------------------------------BUTON KONUMU AYARLAMALARI------------------------------------=
        int rectX = (int)(SCREEN_WIDTH*0.55);
        int rectW = SCREEN_WIDTH/4;
        int middleY = SCREEN_HEIGHT/2;
        int distanceY = SCREEN_HEIGHT/7;

        btnPlay = new Rect(rectX, middleY-(distanceY*4/3), rectX+rectW, middleY-(distanceY/3));
        btnSettings = new Rect(rectX, middleY+(distanceY/3), rectX+rectW, middleY+(distanceY*4/3));

        //-------------DİĞERLERİ-------------
        ui.setFonts();
        ui.setButtons();

        setupGamesButtons();
    }
    private void setupGamesButtons(){
        int tileSize = (int) (this.tileSize*Configs.UI_SCALE);

        int upRow = (games.length - GAME_SELECT_UP_ROW > 0) ? GAME_SELECT_UP_ROW : games.length;
        int downRow = games.length - upRow;

        int rectW = (int)(tileSize*2.5f);
        int rectH = (int)(tileSize*2.5f);
        int padding = tileSize;
        int rowCount = (downRow > 0) ? 2 : 1;

        int startY = (SCREEN_HEIGHT - (rectH + padding) * rowCount + padding) / 2;
        int startX = (SCREEN_WIDTH - (rectW + padding) * upRow + padding) / 2;
        int i = 0;
        for(;i < upRow; i++){
            gameButtons[i] = new Rect(startX, startY, startX + rectW, startY + rectH);
            startX += rectW + padding;
        }

        if(downRow == 0) return;

        startX = (SCREEN_WIDTH - (rectW + padding) * downRow + padding) / 2;
        startY += rectH + padding;
        for(; i < games.length; i++){
            gameButtons[i] = new Rect(startX, startY, startX + rectW, startY + rectH);
            startX += rectW + padding;
        }
    }

    @Override
    public void run(){
        int FPS = 60;
        final long STEP = 1_000_000_000L / FPS;
        long last = System.nanoTime();
        long acc = 0;

        while(isRunning){

            //oyun arka plandaysa Thread burada beklemeye alınır
            synchronized(this){
                while(isPaused && isRunning){
                    //Thread burada tamamen durur ve işlemci tüketmeyi bırakır.
                    //surfaceCreated içindeki notify() çağrılana kadar bu satırda bekler.
                    try{ this.wait(); }catch(InterruptedException ignored){}
                    last = System.nanoTime();
                    acc = 0;
                }

                if(!isRunning) break;

                long now = System.nanoTime();
                acc += now - last;
                last = now;
                //çok gerideyse biriktirme
                if(acc > STEP * 5) acc = STEP * 5;

                long t0 = System.nanoTime();
                while(acc >= STEP){ update(); acc -= STEP; }
                long t1 = System.nanoTime();
                draw();
                long t2 = System.nanoTime();

                if(Configs.DEBUG) perf.record(t1 - t0, t2 - t1);
            }

            //kilit dışında uyu: bir sonraki update zamanına kadar
            long sleepNs = (last + STEP - acc) - System.nanoTime();
            if(sleepNs > 0){
                try{ Thread.sleep(sleepNs / 1_000_000, (int)(sleepNs % 1_000_000)); }
                catch(InterruptedException ignored){}
            }
        }
    }

    private void update(){
        if(currentGame != null)
            if(gameState == GameState.GAME){
                currentGame.update();

                if(currentGame.getState() == Game.State.GAME_OVER){
                    gameState = GameState.GAME_OVER;
                    vibration.vibrateOneShot(150, 210);
                }
            }
        ui.update();
    }

    private void draw(){
        //tuvalin (Canvas) kilitli ve erişilebilir olduğundan emin ol
        if(!holder.getSurface().isValid()) return;

        //tuvali çizim için kilitle
        Canvas canvas = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                //API 26 ve üzeri cihazlarda GPU devrede
                ? holder.lockHardwareCanvas()
                //eski cihazlar için eski güvenli yöntem
                : holder.lockCanvas();

        if(canvas == null) return;

        try{

            //arkaplanı siyah yap
            canvas.drawColor(android.graphics.Color.BLACK);

            if(currentGame != null)
                if(gameState == GameState.GAME || gameState == GameState.START_COUNTER || gameState == GameState.GAME_OVER ||
                        (nextState == GameState.GAME && gameState == GameState.TITLE_TO_STATE) ||
                        ((lastState == GameState.GAME || lastState == GameState.GAME_OVER) && gameState == GameState.STATE_TO_STATE && !ui.isOpening) ||
                        (nextState == GameState.GAME && gameState == GameState.STATE_TO_STATE && ui.isOpening) ||
                        (nextState == GameState.START_COUNTER && gameState == GameState.STATE_TO_STATE && ui.isOpening))
                    currentGame.draw(canvas);

            ui.draw(canvas, gameState);

            if(Configs.DEBUG) canvas.drawText(perf.text, 20, 60, ui.fontDebugText);
        }
        finally{
            //hata olsa da olmasa da çizimi ekrana bas (kiliti bırak)
            holder.unlockCanvasAndPost(canvas);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        int action = event.getAction();
        int x = (int)(event.getX());
        int y = (int)(event.getY());

        if(gameState == GameState.GAME && currentGame != null && currentGame.handleTouch(event))
            return true;

        if(action != MotionEvent.ACTION_DOWN) return super.onTouchEvent(event);

        switch(gameState){
            case TITLE:
                if(btnPlay.contains(x, y)){
                    preLoadGameSelectImages();
                    gameState = GameState.TITLE_TO_STATE;
                    nextState = GameState.GAME_SELECT;
                    lastState = GameState.TITLE;
                    vibrateClick();
                }
                else if(btnSettings.contains(x, y)){
                    //TODO gelecekte ayarlara geçecek
                }
                return true;

            case GAME_SELECT:
                for(int i = 0; i < gameButtons.length; i++)
                    if(gameButtons[i] != null && gameButtons[i].contains(x,y)){
                        currentGame = games[i];
                        nextState = GameState.START_COUNTER;
                        lastState = GameState.GAME_SELECT;
                        gameState = GameState.STATE_TO_STATE;
                        removeGameSelectImages();
                        currentGame.preloadGame();
                        currentGame.updateScreenSize();
                        currentGame.reset();
                        vibrateClick();
                        break;
                    }
                return true;

            case STATE_TO_STATE:
                ui.stopStateToState();
                return true;

            case TITLE_TO_STATE:
                ui.stopTitleToState();
                return true;

        }

        return super.onTouchEvent(event);
    }

    public void preLoadGameSelectImages(){
        for(Game g : games)
            if(g.getLogoTextureID() != -1)
                imageStore.preloadImage(g.getLogoTextureID());

    }
    public void removeGameSelectImages(){
        for(Game g : games)
            if(g.getLogoTextureID() != -1)
                imageStore.removeImage(g.getLogoTextureID());
    }

    private void vibrateClick(){
        vibration.vibrateOneShot(40, 45);
    }

    private static class PerfStats{
        private long updateSum, drawSum, windowStart = System.nanoTime();
        private int frames;
        public volatile String text = "";

        void record(long updateNs, long drawNs){
            updateSum += updateNs;
            drawSum += drawNs;
            frames++;
            long now = System.nanoTime();
            if(now - windowStart >= 1_000_000_000L){
                double u = updateSum / 1e6 / frames;
                double d = drawSum / 1e6 / frames;
                text = String.format(java.util.Locale.US,
                        "FPS %d | upd %.2f ms | draw %.2f ms | yuk %%%d",
                        frames, u, d, (int)((u + d) / 16.67 * 100));
                updateSum = drawSum = 0;
                frames = 0;
                windowStart = now;
            }
        }
    }
}
