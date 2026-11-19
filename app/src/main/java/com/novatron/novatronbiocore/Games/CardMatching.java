package com.novatron.novatronbiocore.Games;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.novatron.novatronbiocore.GamePanel;
import com.novatron.novatronbiocore.R;

import java.util.Arrays;

public class CardMatching implements Game{

    private static final int[] CARD_IDS = {
            R.drawable.card_matching_bottle, R.drawable.card_matching_dna, R.drawable.card_matching_bough, R.drawable.card_matching_frc_logo,
            R.drawable.card_matching_frc_robot, R.drawable.card_matching_leaf, R.drawable.card_matching_plant_inside_lightbulb, R.drawable.card_matching_recycle_bin,
            R.drawable.card_matching_recycle_logo, R.drawable.card_matching_rock, R.drawable.card_matching_virus, R.drawable.card_matching_water_drop
    };

    private static final int CARD_COUNT = CARD_IDS.length*2;
    private static final int ROW_COUNT  = 4;
    private static final int COL_COUNT  = CARD_COUNT / ROW_COUNT;

    private static final int MAX_FLASH_ALPHA = 90;
    private static final int ANIMATION_FRAMES = 30;

    private static final long[] timings1 = {220, 110};
    private static final int[] amplitudes1 = {0, 150};

    private RectF[] defaultCardHitboxes;
    private Integer[] allIDs;
    private Card[] cards;
    private Card selected1, selected2;
    private float cardWidth, cardHeight;
    private boolean turnCardsToBackFace = false;
    private boolean matchFound = false;
    private int removedCount;

    private final GamePanel gp;
    private int time;
    private Game.State state;


    //animations
    private int answerColor;
    private int animationCounter = -1;

    public CardMatching(GamePanel gp){ this.gp = gp; }

    @Override
    public void updateScreenSize(){
        //------------CALCULATE CARD POSITIONS(centers)------------
        float offSetX = gp.tileSize/2f;
        float padding = gp.tileSize*0.3f;
        cardWidth = (gp.SCREEN_WIDTH - offSetX*2 + padding)/COL_COUNT - padding;
        cardHeight = cardWidth / 1.5f;
        float offSetY = (gp.SCREEN_HEIGHT - ((cardHeight + padding) * ROW_COUNT - padding))/2f;

        float left = offSetX;
        float top = offSetY;
        int i = 0;
        for(int y = 0; y < ROW_COUNT; y++){
            for(int x = 0; x < COL_COUNT; x++, i++){
                defaultCardHitboxes[i].set(left, top, left + cardWidth, top + cardHeight);
                left += cardWidth + padding;
            }
            top += cardHeight + padding;
            left = offSetX;
        }
    }

    @Override
    public void update(){
        time++;
        if(animationCounter != -1){
            animationCounter--;
            if(animationCounter == -1 && matchFound)
                removeSelectedCards();
            return;
        }

        if(selected1 != null) turnCard(selected1, turnCardsToBackFace);
        if(selected2 != null) turnCard(selected2, turnCardsToBackFace);
        if(selected1 == null || selected2 == null) return;
        if(isTurnNotDone(selected1) || isTurnNotDone(selected2)) return;

        if(!turnCardsToBackFace){
            if(areTheyRightPair(selected1, selected2)){
                answerColor = Color.GREEN;
                matchFound = true;
            }
            else{
                answerColor = Color.RED;
                gp.vibration.vibrateWaveform(timings1, amplitudes1, -1);
                turnCardsToBackFace = true;
            }
            animationCounter = ANIMATION_FRAMES;
        }
        else{
            turnCardsToBackFace = false;
            selected1 = null;
            selected2 = null;
        }

    }
    private void removeSelectedCards(){
        selected1.removed = true;
        selected2.removed = true;
        selected1 = null;
        selected2 = null;
        matchFound = false;
        removedCount += 2;
        if(removedCount >= CARD_COUNT)
            state = State.GAME_OVER;
    }
    private void turnCard(Card c, boolean toBack){
        float step = cardWidth / 15f;
        if(c.isFacingBack != toBack){ //it isn't facing the target face
            c.width -= step;
            if(c.width <= 0){
                c.width = 0;
                c.isFacingBack = toBack;
            }
        }
        else if(c.width < cardWidth){ //it is facing the target face
            c.width = Math.min(c.width + step, cardWidth);
        }
    }
    private boolean isTurnNotDone(Card c){
        return c.isFacingBack != turnCardsToBackFace || !(c.width >= cardWidth);
    }
    private boolean areTheyRightPair(Card c1, Card c2){ return c1.ID == c2.ID; }

    @Override
    public void draw(Canvas canvas){
        canvas.drawColor(SKY_COLOR);
        for(Card c : cards){
            if(c.removed) continue;
            gp.imageStore.drawScaledPixelImageWithCenter(canvas,
                    (c.isFacingBack) ? R.drawable.card_matching_back : c.ID,
                    c.hitbox.centerX(),
                    c.hitbox.centerY(),
                    c.width, c.height);
        }

        if(animationCounter != -1){
            float half = ANIMATION_FRAMES / 2f;
            float ratio = 1f - Math.abs(animationCounter - half) / half;
            int alpha = (int)(ratio * MAX_FLASH_ALPHA);
            canvas.drawColor((answerColor & 0x00FFFFFF) | (alpha << 24));
        }
    }

    @Override
    public boolean handleTouch(MotionEvent event){
        if(turnCardsToBackFace) return true;
        if(selected1 != null){
            if(selected2 != null) return false;
            if(selected1.isFacingBack || selected1.width < cardWidth) return false;
        }

        float x = event.getX(), y = event.getY();
        for(Card c : cards){
            if(c.removed || selected1 == c || selected2 == c) continue;
            if(c.hitbox.contains(x, y)){
                if(selected1 == null) selected1 = c;
                else if(selected2 == null) selected2 = c;
                return true;
            }
        }
        return false;
    }

    @Override
    public void preloadGame(){
        cards = new Card[CARD_COUNT];
        for(int i = 0; i < cards.length; i++)
            cards[i] = new Card();
        defaultCardHitboxes = new RectF[CARD_COUNT];
        for(int i = 0; i < defaultCardHitboxes.length; i++)
            defaultCardHitboxes[i] = new RectF();

        allIDs = new Integer[CARD_COUNT];
        for(int i = 0; i < allIDs.length; i++)
            allIDs[i] = CARD_IDS[i % CARD_IDS.length];

        gp.imageStore.preloadImage(R.drawable.card_matching_back);
        for(int id : CARD_IDS) gp.imageStore.preloadImage(id);
    }
    @Override
    public void destroyGame(){
        Arrays.fill(cards, null);
        cards = null;
        Arrays.fill(defaultCardHitboxes, null);
        defaultCardHitboxes = null;
        allIDs = null;
        selected1 = null;
        selected2 = null;
        gp.imageStore.removeImage(R.drawable.card_matching_back);
        for(int id : CARD_IDS) gp.imageStore.removeImage(id);
    }

    @Override
    public int getTime(){ return -1; }
    @Override
    public int getCurrentScore(){ return time /60; }
    @Override
    public int getMaxScore(){ return 0; }
    @Override
    public State getState(){ return state; }
    @Override
    public int getLogoTextureID(){ return R.drawable.logo_card_matching_logo; }
    @Override
    public void reset(){
        state = State.RUNNING;

        //kartların yerlerini değiştir
        java.util.Collections.shuffle(Arrays.asList(allIDs));

        for(int i = 0; i < CARD_COUNT; i++){
            Card c = cards[i];
            c.hitbox.set(defaultCardHitboxes[i]);
            c.width = cardWidth;
            c.height = cardHeight;
            c.isFacingBack = true;
            c.removed = false;
            c.ID = allIDs[i];
        }

        turnCardsToBackFace = false;
        selected1 = null;
        selected2 = null;
        removedCount = 0;
        matchFound = false;
        time = 0;
        animationCounter = -1;
        answerColor = -1;
    }

    private static class Card{
        public RectF hitbox = new RectF();
        public int ID;
        public float width, height;
        public boolean isFacingBack = true;
        public boolean removed = false;
    }
}
