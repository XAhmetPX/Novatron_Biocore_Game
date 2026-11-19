package com.novatron.novatronbiocore.tools;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import com.novatron.novatronbiocore.Configs;
import com.novatron.novatronbiocore.GamePanel;
import com.novatron.novatronbiocore.R;

public class UI{

    private final GamePanel gp;
    private final android.content.Context context;

    //------------------PAINTS------------------
    private final Paint fontTitle = new Paint();
    public  final Paint fontScore = new Paint();
    public  final Paint fontDebug = new Paint();
    public  final Paint fontDebugText = new Paint();
    public  final Paint fontCounter = new Paint();
    public  final Paint fontGameOver = new Paint();

    public final android.graphics.Typeface pixelFont;

    //ana ekran
    private int titleY = 0;
    private boolean isTitleOpen = true;

    //state geçiş ekranı
    private int leftLogoX, rightLogoX;
    public boolean isOpening = false;
    private byte counterSTS = -1;

    //sayaç ekranı
    private short counter = 220;
    private int counterX, counterY;

    //oyun bitti yazısı
    private int gameOverX, gameOverY;
    private int gameOverCounter = 150;


    public UI(GamePanel gp){
        this.gp = gp;
        context = gp.getContext();

        pixelFont = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.press_start2p_regular);

        fontTitle.setColor(Color.BLACK);
        fontTitle.setTypeface(pixelFont);

        fontScore.setColor(Color.WHITE);
        fontScore.setTypeface(pixelFont);

        fontCounter.setColor(Color.WHITE);
        fontCounter.setTypeface(pixelFont);

        fontDebug.setColor(Color.GREEN);
        fontDebug.setStyle(Paint.Style.STROKE);
        fontDebug.setStrokeWidth(2.5f);

        fontDebugText.setColor(Color.BLUE);
        fontDebugText.setTypeface(pixelFont);

        fontGameOver.setColor(Color.WHITE);
        fontGameOver.setTypeface(pixelFont);

    }

    public void setFonts(){
        final int tileSize = (int)(gp.tileSize*Configs.UI_SCALE);
        fontTitle.setTextSize(tileSize*1.1f); //80
        fontGameOver.setTextSize(tileSize*1.4f); //100
        fontScore.setTextSize(tileSize/2.6f); //30
        fontCounter.setTextSize(tileSize*2.8f); //160
        fontDebugText.setTextSize(tileSize/2.7f);
        leftLogoX = -gp.SCREEN_WIDTH/2;
        rightLogoX = gp.SCREEN_WIDTH;
        isOpening = false;
    }

    public void update(){
        switch(gp.gameState){
            case TITLE_TO_STATE:
                if(isTitleOpen){
                    //ana ekrandan oyuna
                    titleY -= gp.tileSize/5; //16
                    if(titleY < -gp.SCREEN_HEIGHT){
                        stopTitleToState();
                    }
                }
                else{
                    //bir durumdan ana ekrana
                    titleY += gp.tileSize/5;
                    if(titleY > 0){
                        stopTitleToState();
                    }
                }
                break;

            case STATE_TO_STATE:
                int speed = gp.tileSize/10;
                if(counterSTS > -1){
                    counterSTS++;
                    counterSTS = (counterSTS > 90) ? -1 : counterSTS;
                    break;
                }
                if(!isOpening){
                    //ortaya yaklaşma
                    leftLogoX += speed;
                    rightLogoX -= speed;
                    if(leftLogoX >= 0){
                        isOpening = true;
                        leftLogoX = 0;
                        rightLogoX = gp.SCREEN_WIDTH/2;
                        counterSTS = 0;
                    }
                }
                else{
                    //kenarlara açılma
                    leftLogoX -= speed;
                    rightLogoX += speed;
                    if(leftLogoX <= -gp.SCREEN_WIDTH/2){
                        stopStateToState();
                    }
                }
                break;

            case START_COUNTER:
                counter--;
                if(counter <= 0){
                    counter = 220;
                    gp.gameState = GamePanel.GameState.GAME;
                }
                break;

            case GAME_OVER:
                gameOverCounter--;
                if(gameOverCounter <= 0){
                    gameOverCounter = 150;
                    gp.lastState = gp.gameState;
                    gp.nextState = GamePanel.GameState.GAME_SELECT;
                    gp.gameState = GamePanel.GameState.STATE_TO_STATE;
                }
                break;
        }
    }
    public void stopStateToState(){
        isOpening = false;
        leftLogoX = -gp.SCREEN_WIDTH/2;
        rightLogoX = gp.SCREEN_WIDTH;
        gp.gameState = gp.nextState;
    }
    public void stopTitleToState(){
        if(isTitleOpen){
            //ana ekrandan oyuna
            isTitleOpen = false;
            titleY = -gp.SCREEN_HEIGHT;
        }
        else{
            //bir durumdan ana ekrana
            isTitleOpen = true;
            titleY = 0;
        }
        counterSTS = -1;
        gp.gameState = gp.nextState;
    }

    public void draw(android.graphics.Canvas canvas, GamePanel.GameState state){
        switch(state){
            case TITLE_TO_STATE:
                //en arkaya çizilecek state
                GamePanel.GameState extraState = (isTitleOpen) ? gp.nextState : gp.lastState;
                if(extraState != GamePanel.GameState.TITLE_TO_STATE) //döngüye girmemek için
                    draw(canvas, extraState);
            case TITLE:
                //arkaplan
                gp.imageStore.drawScaledPixelImage(canvas, R.drawable.logo_full, 0, titleY, gp.SCREEN_WIDTH, gp.SCREEN_HEIGHT);
                //play
                if(gp.btnPlay != null)
                    canvas.drawText(context.getString(R.string.play), gp.btnPlay.left, gp.btnPlay.bottom+titleY, fontTitle);
                //settings
                if(gp.btnSettings != null)
                    canvas.drawText(context.getString(R.string.settings), gp.btnSettings.left, gp.btnSettings.bottom+titleY, fontTitle);
                break;

            case GAME:
                final int tileSize = (int)(gp.tileSize* Configs.UI_SCALE);
                if(gp.currentGame == null) return;
                if(gp.currentGame.getCurrentScore() != -1){
                    String scores = String.format(context.getString(R.string.max) + "%06d %06d",
                            gp.currentGame.getMaxScore(),
                            gp.currentGame.getCurrentScore());
                    float x = (gp.SCREEN_WIDTH - fontScore.measureText(scores) - (float)tileSize/2);
                    float y = tileSize*0.75f;
                    canvas.drawText(scores, x,y, fontScore);
                }
                if(gp.currentGame.getTime() != -1){
                    String time = String.format(context.getString(R.string.time)+"%06d",
                            gp.currentGame.getTime());
                    float x = tileSize/2.0f;
                    float y = tileSize*0.75f;
                    canvas.drawText(time, x, y, fontScore);
                }
                break;

            case GAME_SELECT:
                for(int i = 0; i < gp.games.length; i++){
                    int id = gp.games[i].getLogoTextureID();
                    Rect r = gp.gameButtons[i];
                    if(r == null) continue;
                    if(id == -1){
                        canvas.drawRect(r, fontDebug);
                        continue;
                    }
                    gp.imageStore.drawScaledImage(canvas, id, r.left, r.top, r.width(), r.height());

                }
                break;

            case GAME_OVER:
                draw(canvas, GamePanel.GameState.GAME);
                //oyun bitti yazısı
                canvas.drawText(context.getString(R.string.game_over), gameOverX, gameOverY, fontGameOver);
                break;

            case STATE_TO_STATE:
                if(isOpening){ if(gp.nextState != GamePanel.GameState.STATE_TO_STATE) draw(canvas, gp.nextState); }
                else{ if(gp.lastState != GamePanel.GameState.STATE_TO_STATE) draw(canvas, gp.lastState); }

                //sol yarım logo
                gp.imageStore.drawScaledPixelImage(canvas, R.drawable.logo_left, leftLogoX, 0, gp.SCREEN_WIDTH/2, gp.SCREEN_HEIGHT);
                //sağ yarım logo
                gp.imageStore.drawScaledPixelImage(canvas, R.drawable.logo_right, rightLogoX, 0, gp.SCREEN_WIDTH/2, gp.SCREEN_HEIGHT);
                break;

            case START_COUNTER:
                byte num = 1;
                if(counter > 140) num = 3;
                else if(counter > 80) num = 2;
                canvas.drawText(""+num, counterX, counterY, fontCounter);
                draw(canvas, GamePanel.GameState.GAME);
                break;

        }
    }

    public void setButtons(){
        Rect r = new Rect();

        //PLAY
        String text = context.getString(R.string.play);
        fontTitle.getTextBounds(text, 0, text.length(), r);
        gp.btnPlay.right = gp.btnPlay.left + r.width();
        gp.btnPlay.top = gp.btnPlay.bottom - r.height();

        //SETTINGS
        text = context.getString(R.string.settings);
        fontTitle.getTextBounds(text, 0, text.length(), r);
        gp.btnSettings.right = gp.btnSettings.left + r.width();
        gp.btnSettings.top = gp.btnSettings.bottom - r.height();

        //COUNTER
        text = "3";
        fontCounter.getTextBounds(text, 0, text.length(), r);
        counterX = (gp.SCREEN_WIDTH - r.width())/2;
        counterY = (gp.SCREEN_HEIGHT + r.height())/2;

        //GAME OVER
        text = context.getString(R.string.game_over);
        fontGameOver.getTextBounds(text, 0, text.length(), r);
        gameOverX = (gp.SCREEN_WIDTH - r.width())/2;
        gameOverY = (gp.SCREEN_HEIGHT + r.height())/2;

    }

}
