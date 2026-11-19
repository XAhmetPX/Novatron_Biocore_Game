package com.novatron.novatronbiocore.tools;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.LruCache;

public class ImageStore{

    private final Context context;
    private final LruCache<Integer, Bitmap> bitmapCache;

    private final Paint pixelArtPaint; //pixel art için kritik Paint nesnesi(bulanıklaştırmayı önler)
    private final Paint defaultPaint; //normal resimler için(bulanıklaştırmalı)

    //çizim sırasında nesne oluşturmamak içın yeniden kullanılabilir Rect'ler (Performans için)
    private final Rect srcRect;
    private final RectF dstRect;

    public ImageStore(Context context){
        this.context = context;

        //cihazın kullanılabilir belleğinin 8'e 1'ini resim önbelleğine ayır
        final int maxMemory = (int)(Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;

        bitmapCache = new LruCache<>(cacheSize){
            @Override
            protected int sizeOf(Integer key, Bitmap bitmap){
                //cache boyutunu eleman sayısı değil, kilobayt(KB) cinsinden hesapla
                return bitmap.getByteCount() / 1024;
            }
        };

        //Pixel art'ın bozulmadan (bulanıklaşmadan) büyütülmesi için AntiAlias kapatılmalı ve Filter kapalı olmalı
        pixelArtPaint = new Paint();
        pixelArtPaint.setAntiAlias(false);
        pixelArtPaint.setFilterBitmap(false);

        defaultPaint = new Paint();
        defaultPaint.setFilterBitmap(true);

        srcRect = new Rect();
        dstRect = new RectF();
    }

    //level başlamadan önce çağrılmalı (oyun donmasın diye)
    public void preloadImage(int ID){
        if(bitmapCache.get(ID) != null) return;

        //Android'in resimleri otomatik scale etmesini engelle (Önemli!)
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;

        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), ID, options);
        if(bitmap != null)
            bitmapCache.put(ID, bitmap);
    }

    //resimi orjinal boyutta almak için
    public Bitmap getImage(int ID){
        //önce cache'e bak
        Bitmap bitmap = bitmapCache.get(ID);

        //resim varsa direkt döndür
        if(bitmap != null) return bitmap;

        //resim cache'te yok(sistem silmiş ya da preload edilmemiş)
        //mecburen diskten oku
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        bitmap = BitmapFactory.decodeResource(context.getResources(), ID, options);
        if(bitmap != null)
            bitmapCache.put(ID, bitmap); //bir dahaki sefere hızlı olması için RAM'e geri koy

        return bitmap;
    }

    public void drawScaledPixelImage(Canvas canvas, int ID, float x, float y, float width, float height){
        Bitmap bitmap = getImage(ID);
        if(bitmap == null || canvas == null) return;

        //srcRect: resmin tamamını al (0,0'dan orjinal genişlik ve yüksekliğe kadar)
        srcRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());

        //dstRect: ekrana nereye ve hangi boyutta çizileceği
        dstRect.set(x, y, x + width, y + height);

        //pixel art paint ile bulanıklaşmadan çiz
        canvas.drawBitmap(bitmap, srcRect, dstRect, pixelArtPaint);
    }

    public void drawScaledPixelImageWithRect(Canvas canvas, int ID, float left, float top, float right, float bottom){
        drawScaledPixelImage(canvas, ID, left, top, right - left, bottom - top);
    }

    public void drawScaledPixelImageWithCenter(Canvas canvas, int ID, float centerX, float centerY, float width, float height){
        drawScaledPixelImage(canvas, ID, centerX - width/2f, centerY - height/2, width, height);
    }

    public void drawScaledPixelImageWithAlpha(Canvas canvas, int ID, int x, int y, int width, int height, int alpha){
        pixelArtPaint.setAlpha(alpha);
        drawScaledPixelImage(canvas, ID, x, y, width, height);
        pixelArtPaint.setAlpha(255);
    }

    public void drawScaledPixelImageWithAlphaAndCenter(Canvas canvas, int ID, float centerX, float centerY, float width, float height, int alpha){
        pixelArtPaint.setAlpha(alpha);
        drawScaledPixelImageWithCenter(canvas, ID, centerX, centerY, width, height);
        pixelArtPaint.setAlpha(255);
    }

    public void drawScaledImage(Canvas canvas, int ID, int x, int y, int width, int height){
        Bitmap bitmap = getImage(ID);
        if(bitmap == null || canvas == null) return;

        //srcRect: resmin tamamını al (0,0'dan orjinal genişlik ve yüksekliğe kadar)
        srcRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());

        //dstRect: ekrana nereye ve hangi boyutta çizileceği
        dstRect.set(x, y, x + width, y + height);

        //pixel art paint ile bulanıklaşmadan çiz
        canvas.drawBitmap(bitmap, srcRect, dstRect, defaultPaint);
    }

    //uygulama kapandığında veya seviye bittiğinde belleği temizle
    public void clearMemory(){ bitmapCache.evictAll(); }
    public void removeImage(int ID){ bitmapCache.remove(ID); }
}
