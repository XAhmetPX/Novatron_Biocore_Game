package com.novatron.novatronbiocore.Games.FRCGames;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

import com.novatron.novatronbiocore.GamePanel;
import com.novatron.novatronbiocore.tools.Viewport;


/**
 * Bir fizik dünyası. Her oyun kendi sahasının ölçüleriyle bir Physic3D örneği oluşturur:

 *   Physic3D physic = new Physic3D(fieldWidthMeters, fieldDepthMeters);
 *   physic.addEntity(new Physic3D.Robot(...));
 *   ...
 *   //her karede:
 *   for(Physic3D.PhysicsEntity e : physic.entities) e.update(dt);
 *   physic.solveCollisions();

 * Eksenler (FRC kılavuzuyla uyumlu): WIDTH = x ekseni (sahanın KISA kenarı, ~8 m),
 * HEIGHT = y ekseni (yükseklik), DEPTH = z ekseni (sahanın UZUN kenarı, ~16 m).
 * Konum ve hız libGDX Vector3'tür: (x, y, z) = (width, height, depth). Sıra her yerde x, y, z'dir.
 * Çarpışma çözücü yer düzleminde (x-z) çalışır; yükseklik (y) bir FİLTREDİR: her cisim, tabanı position.y ve
 * yüksekliği height olan bir prizmadır ve iki cisim ancak x-z'de kesişiyorsa VE [y, y + height] aralıkları
 * örtüşüyorsa çarpışır (üst üste değmek örtüşmek sayılmaz: bir cismin tepesi diğerinin tabanına eşitse çarpışmazlar).
 * Açılar: 0 = +x yönü, +x'ten +z'ye doğru artar.
 * <br>
 * Saha (0,0) - (fieldWidth, fieldDepth) dikdörtgenidir (x: 0..fieldWidth, z: 0..fieldDepth);
 * duvarlar bu dikdörtgenin kenarlarıdır.
 */
public class Physic3D{

    /** Saha genişliği (x ekseni, kısa kenar) ve derinliği (z ekseni, uzun kenar), metre cinsinden */
    public final float fieldWidth, fieldDepth;

    /** Bu dünyadaki tüm cisimler. Okuyup dolaşabilirsin; eklemek/silmek için addEntity/removeEntity kullan. */
    public final List<PhysicsEntity> entities = new ArrayList<>();

    /** Üstüne cisim konabilen cisimler (PhysicsEntity.canSupport() = true olanlar: robot, engeller). addEntity/removeEntity yönetir. */
    private final List<PhysicsEntity> surfaces = new ArrayList<>();

    /** Dünyanın tohumlu rastgele sayı üreteci (örn. yüzeyin tam ortasında duran topun hangi yöne kayacağı için). */
    public final java.util.Random random = new java.util.Random(102022026L);

    //fizik motoru için geçici değişkenler
    //DİKKAT: libGDX'in 2D tipleri (MinimumTranslationVector.normal, Rectangle, Polygon) yer düzleminde (x-z) çalışır;
    //bunların "y" alanı dünyanın z eksenidir (örn. mtv.normal.y = z yönü, Rectangle.y = minimum z).
    private final Intersector.MinimumTranslationVector mtv = new Intersector.MinimumTranslationVector();

    //----------------------------IZGARA SİSTEMİ----------------------------
    public static final float DEFAULT_GRID_SIZE = 0.3f; //her ızgara 30 cm: topun (çap 15 cm + marj) 'küçük cisim' sayılabildiği en küçük kolay değer, yoğun top kümesinde hücre başına ~3 top
    public final float gridSize;
    public final int gridCols, gridRows;
    /* Sahayı gridSize genişliğinde eşit aralıklarda ızgaralara ayırır,
    merkezi içinde bulunan canlıları tutar, canlı haraket ettikçe değişir. */
    public final List<List<PhysicsEntity>> grids;


    //-------------------------DİKEY HAREKET AYARLARI-------------------------
    /** Yerçekimi ivmesi (m/s²). Yalnızca havadaki (y > 0) cisimlere uygulanır, bkz. PhysicsEntity.updateVertical */
    public static final float GRAVITY = 9.81f;
    /** Yere çarpış hızı (m/s) bunun altındaysa cisim sekmeyi bırakıp yere yapışır */
    public static final float MIN_BOUNCE_SPEED = 0.6f;
    /** Cismin tabanı bir yüzeyin tepesinden en fazla bu kadar aşağıdaysa (m) yüzeyin üstünde sayılır (küçük sayısal sapmalar için) */
    private static final float LAND_TOLERANCE = 0.02f;

    //yerdeki (y ≈ 0) cisimler çizimde ayrı, ucuz bir geçişte çizilir (bkz. drawEntities)
    private static final float GROUND_EPSILON = 1e-4f;
    private final List<PhysicsEntity> elevated = new ArrayList<>(); //çizim için geçici liste (her karede yeniden kullanılır)
    private static final java.util.Comparator<PhysicsEntity> BY_BOTTOM_Y =
            (a, b) -> Float.compare(a.position.y, b.position.y);

    //-------------------------ÇARPIŞMA ÇÖZÜCÜ AYARLARI-------------------------
    private static final int   POSITION_ITERATIONS = 8;      //konum düzeltme tekrar sayısı (zincir uzunluğu + 2 kadar yeterli)
    private static final int   VELOCITY_ITERATIONS = 4;      //hız (impulse) tekrar sayısı
    private static final float PENETRATION_SLOP    = 0.002f; //2 mm iç içe geçmeye izin ver (temas her karede kararlı kalsın)
    private static final float WALL_SKIN           = 0.005f; //duvara 5 mm kala "duvara dayalı" say
    private static final float PIN_FREE_FRACTION   = 0.2f;   //bir yönde hareket edebilme oranı bunun altındaysa cisim o yönde "sıkışmış" sayılır
    private static final float BROADPHASE_MARGIN   = 0.05f;
    private static final float MIN_MOVE            = 1e-5f;  //bu kadar bile düzeltme olmadıysa çözücü durur

    //çözücünün geçici verileri (her karede yeniden kullanılır, GC'ye çöp bırakmaz)
    private PhysicsEntity[] bodies = new PhysicsEntity[64];
    private int bodyCount = 0;
    private int[] pairA = new int[256];
    private int[] pairB = new int[256];
    private int pairCount = 0;
    private boolean[] pairTouch = new boolean[256];
    private float[] pairImpact = new float[256];
    private float[] bMinX = new float[64], bMaxX = new float[64], bMinZ = new float[64], bMaxZ = new float[64];
    private boolean[] bSmall = new boolean[64];   //cisim "küçük" mü (bkz. buildPairs)
    private int[] largeBodies = new int[16];      //büyük cisimlerin 'bodies' indeksleri (artan sırada)
    private int largeCount = 0;
    private long[] pairKeys = new long[512];      //çiftler (i << 32 | j), sıralanıp pairA/pairB'ye açılır
    private int pairKeyCount = 0;

    public Physic3D(float fieldWidth, float fieldDepth){
        this(fieldWidth, fieldDepth, DEFAULT_GRID_SIZE);
    }

    public Physic3D(float fieldWidth, float fieldDepth, float gridSize){
        if(fieldWidth <= 0f || fieldDepth <= 0f)
            throw new IllegalArgumentException("Saha ölçüleri pozitif olmalı: " + fieldWidth + " x " + fieldDepth);
        if(!(gridSize > 0f))
            throw new IllegalArgumentException("gridSize 0'dan büyük olmalı: " + gridSize);
        this.fieldWidth = fieldWidth;
        this.fieldDepth = fieldDepth;

        //ızgara sistemi
        this.gridSize = gridSize;
        this.gridCols = (int)Math.ceil(fieldDepth / gridSize);
        this.gridRows = (int)Math.ceil(fieldWidth / gridSize);
        this.grids = new ArrayList<>(gridCols * gridRows);
        for(int i = 0; i < gridCols * gridRows; i++)
            grids.add(new ArrayList<>());
    }

    /** z konumunun ızgara sütunu (0..gridCols-1). Saha dışındaki konumlar kenar sütuna sıkıştırılır. */
    int cellCol(float z){
        int c = (int) Math.floor(z / gridSize);
        return (c < 0) ? 0 : Math.min(c, gridCols - 1);
    }
    /** x konumunun ızgara satırı (0..gridRows-1). Saha dışındaki konumlar kenar satıra sıkıştırılır. */
    int cellRow(float x){
        int r = (int) Math.floor(x / gridSize);
        return (r < 0) ? 0 : Math.min(r, gridRows - 1);
    }

    /** Cismi bu dünyaya ekler (duvar sınırları ve çarpışmalar artık bu dünyaya göre işlenir) */
    public <T extends PhysicsEntity> T addEntity(T entity){
        if(entity == null) throw new IllegalArgumentException("entity null olamaz");
        if(entity.world != null && entity.world != this)
            throw new IllegalStateException("Bu cisim zaten başka bir Physic dünyasında");
        if(entity.world == this) return entity; //zaten ekli

        entity.world = this;
        entities.add(entity);
        if(entity.canSupport() && !entity.isSensor) surfaces.add(entity);
        entity.updateGridIndex();
        return entity;
    }

    /** Cismi dünyadan çıkarır. Dinleyici (listener) içinden de çağrılabilir. */
    public boolean removeEntity(PhysicsEntity entity){
        if(entity == null || entity.world != this) return false;
        entity.world = null;
        surfaces.remove(entity); //yüzey değilse zararsız (isSensor sonradan değişmiş olabilir)
        if(entity.gridIndex != -1){ //ızgaralardan çıkar
            grids.get(entity.gridIndex).remove(entity);
            entity.gridIndex = -1;
        }
        return entities.remove(entity);
    }

    //-------------------------------ALAN SORGULARI (ızgara tabanlı)-------------------------------
    /** findNearest / countNear için süzgeç: true dönen cisimler sorguya dahil olur. İçinde dünyayı değiştirme. */
    public interface EntityFilter{
        boolean accept(PhysicsEntity e);
    }

    /** Izgaranın cisim konumlarından en fazla bu kadar (m) eski olabileceği varsayılır (kare içi hareket); böylece sorgular
     *  update döngüsünün ortasında (örn. bot beyni) da güvenlidir. */
    private static final float QUERY_SLACK = 0.25f;

    /**
     * (x, z)'ye yer düzleminde (y yok sayılır) en yakın, süzgeçten geçen cismi döndürür; maxRadius içinde yoksa null.
     * Merkezler arası mesafe ölçülür. Izgarada halka halka dışa doğru aranır; bulunan en iyi mesafe, henüz bakılmamış
     * halkaların alt sınırından küçük kalınca durur, yani cisim sayısına değil yakınlığa bağlı çalışır. Sonuç tüm cisimleri
     * tarayan sorguyla aynı MESAFEYİ verir (eşit mesafede hangisinin döneceği tanımsızdır).
     * Saha dışına taşmış (kenar hücreye sıkıştırılmış) cisimler için alt sınır garanti edilmez.
     */
    public PhysicsEntity findNearest(float x, float z, float maxRadius, EntityFilter filter){
        if(!(maxRadius >= 0f)) return null; //negatif ve NaN
        final int row = cellRow(x), col = cellCol(z);
        final float maxD2 = maxRadius * maxRadius;
        final int maxRing = Math.max(gridRows, gridCols);

        PhysicsEntity best = null;
        float bestD2 = Float.MAX_VALUE;

        for(int k = 0; k <= maxRing; k++){
            //k. halkadaki (ve ötesindeki) bir cisim en az (k-1)*gridSize - pay kadar uzaktadır
            float lower = k * gridSize - gridSize - QUERY_SLACK;
            if(lower < 0f) lower = 0f;
            if(lower > maxRadius) break;
            if(best != null && lower * lower >= bestD2) break;

            final int r0 = row - k, r1 = row + k, c0 = col - k, c1 = col + k;
            if(r0 < 0 && c0 < 0 && r1 >= gridRows && c1 >= gridCols) break; //halka tamamen saha dışında

            final int rFrom = Math.max(r0, 0), rTo = Math.min(r1, gridRows - 1);
            for(int r = rFrom; r <= rTo; r++){
                final boolean edgeRow = (r == r0 || r == r1);
                if(edgeRow){
                    final int cFrom = Math.max(c0, 0), cTo = Math.min(c1, gridCols - 1);
                    for(int c = cFrom; c <= cTo; c++){
                        List<PhysicsEntity> cell = grids.get(r * gridCols + c);
                        for(int i = 0; i < cell.size(); i++){
                            PhysicsEntity e = cell.get(i);
                            if(!filter.accept(e)) continue;
                            float dx = e.position.x - x, dz = e.position.z - z;
                            float d2 = dx * dx + dz * dz;
                            if(d2 <= maxD2 && d2 < bestD2){ bestD2 = d2; best = e; }
                        }
                    }
                }
                else{ //yalnızca halkanın sol ve sağ ucu
                    for(int side = 0; side < 2; side++){
                        final int c = (side == 0) ? c0 : c1;
                        if(c < 0 || c >= gridCols) continue;
                        List<PhysicsEntity> cell = grids.get(r * gridCols + c);
                        for(int i = 0; i < cell.size(); i++){
                            PhysicsEntity e = cell.get(i);
                            if(!filter.accept(e)) continue;
                            float dx = e.position.x - x, dz = e.position.z - z;
                            float d2 = dx * dx + dz * dz;
                            if(d2 <= maxD2 && d2 < bestD2){ bestD2 = d2; best = e; }
                        }
                    }
                }
            }
        }
        return best;
    }

    /** (x, z) merkezli, yarıçapı 'radius' olan dairenin içindeki (merkezler arası mesafe <= radius), süzgeçten geçen cisimlerin sayısı.
     *  Yalnızca daireyi örten ızgara hücrelerine bakar. Top yoğunluğu / küme sorguları için. */
    public int countNear(float x, float z, float radius, EntityFilter filter){
        if(!(radius >= 0f)) return 0;
        final float reach = radius + QUERY_SLACK;
        final int r0 = cellRow(x - reach), r1 = cellRow(x + reach);
        final int c0 = cellCol(z - reach), c1 = cellCol(z + reach);
        final float r2 = radius * radius;

        int count = 0;
        for(int r = r0; r <= r1; r++){
            for(int c = c0; c <= c1; c++){
                List<PhysicsEntity> cell = grids.get(r * gridCols + c);
                for(int i = 0; i < cell.size(); i++){
                    PhysicsEntity e = cell.get(i);
                    if(!filter.accept(e)) continue;
                    float dx = e.position.x - x, dz = e.position.z - z;
                    if(dx * dx + dz * dz <= r2) count++;
                }
            }
        }
        return count;
    }

    /**
     * 'e' cisminin düşerken üstüne basacağı yüzeyi bulur: (x, z) merkezi yüzeyin üstünde olan ve tepesi, cismin
     * bu karenin başındaki tabanına (oldBottom) göre "aşağıda kalan" en yüksek yüzey. Yoksa null (zemin, y = 0).
     * Tepesi cismin tabanından yüksek olan yüzey (cisim onun yanında/içinde) destek sayılmaz: orada normal
     * yan çarpışma çözülür. Üstüne düşmenin sınırı merkezdir: merkezi kenarı geçen top yüzeyden düşer.
     */
    PhysicsEntity findSupport(PhysicsEntity e, float oldBottom){
        PhysicsEntity best = null;
        float bestTop = -Float.MAX_VALUE;
        for(int i = 0; i < surfaces.size(); i++){
            PhysicsEntity o = surfaces.get(i);
            if(o == e || o.hitbox == null || o.isSensor) continue;
            float top = o.position.y + o.height;
            if(top > oldBottom + LAND_TOLERANCE || top <= bestTop) continue;
            if(!o.hitbox.contains(e.position.x, e.position.z)) continue;
            best = o;
            bestTop = top;
        }
        return best;
    }

    /** Tüm cisimleri başlangıç konumlarına döndürür */
    public void resetAll(){
        //her cisim resetState içinde kendi ızgara hücresini de günceller
        for(int i = 0; i < entities.size(); i++) entities.get(i).resetState();
    }

    /**
     * Tüm cisimleri çizer; tabanı daha yüksek olan cisim sonra çizilir, yani üstte görünür (örn. robotun üstüne
     * düşen top robotun altında kalmaz). İki geçişlidir: önce yerdeki (y ≈ 0) cisimler liste sırasıyla, sonra
     * havadaki/yüksekteki az sayıda cisim y'ye göre sıralanarak. Yüzlerce yerdeki top her karede sıralanmaz.
     */
    public void drawEntities(Canvas canvas, Paint paint){
        elevated.clear();

        //1) havadaki cisimleri ayır ve gölgelerini en alta çiz (yerdeki cisimlerin de altında kalsın)
        for(int i = 0; i < entities.size(); i++){
            PhysicsEntity e = entities.get(i);
            if(e.position.y > GROUND_EPSILON){
                elevated.add(e);
                e.drawShadow(canvas);
            }
        }

        //2a) zemine gömülü cisimler (tabanı y < 0, örn. tümsek): en altta çizilir, listedeki sıraları fark etmez
        for(int i = 0; i < entities.size(); i++){
            PhysicsEntity e = entities.get(i);
            if(e.position.y < -GROUND_EPSILON) e.draw(canvas, paint);
        }

        //2b) yerdeki cisimler (liste sırasıyla)
        for(int i = 0; i < entities.size(); i++){
            PhysicsEntity e = entities.get(i);
            if(e.position.y >= -GROUND_EPSILON && e.position.y <= GROUND_EPSILON) e.draw(canvas, paint);
        }

        //3) havadaki cisimler, tabanı yüksek olan sonra
        if(elevated.isEmpty()) return;

        elevated.sort(BY_BOTTOM_Y); //kararlı sıralama: aynı yükseklikte liste sırası korunur
        for(int i = 0; i < elevated.size(); i++) elevated.get(i).draw(canvas, paint);
        elevated.clear();
    }

    public static abstract class PhysicsEntity{
        //Fiziksel Özellikler (Metrik)
        public final Vector3 position = new Vector3(); //(x, y, z) metre cinsinden: x = width, y = height, z = depth
        public final Vector3 velocity = new Vector3(); //(vx, vy, vz) m/s
        public float angularVelocity = 0f;
        protected float heading; //radyan cinsinden açı (0 = +x yönü, +x'ten +z'ye doğru artar)
        public Polygon hitbox;
        protected Physic3D world; //eklendiği dünya (saha ölçüleri buradan okunur); addEntity ayarlar
        public final float height; //yükseklik (m): cisim [position.y, position.y + height] aralığını kaplar (prizma)
        public final float mass; //kütle
        public final float invMass; //işlem hızı için 1/mass (0 ise sonsuz kütle/sabit obje)
        public final float restitution; //sekme katsayısı (0.0 = sekmez, 1.0 tam yansır)

        //çarpışma kontrolü yapılması gerekip gerekmediğini anlamak için
        public android.graphics.RectF preHitbox;
        protected float maxBoundingSize; //objeyi tamamen içine alan çap

        //canlının içinde bulunduğu ızgara indeksi
        private int gridIndex = -1; // -1 = henüz belli değil

        //bu karede çözücünün 'bodies' dizisindeki sırası (solveCollisions doldurur; -1 = bu karede çözücüde değil)
        private int solveIndex = -1;

        //reset için başlangıç değerleri
        public final float startX, startY, startZ, startHeading;

        //-----ENGELLENMİŞ YÖNLER (çarpışma çözücü her karede sıfırlayıp doldurur)-----
        //Bir cisim duvara / sabit bir cisme / kendisi de sıkışmış başka bir cisme dayanıyorsa,
        //o yöne (birim vektör) hareket edemez. Bu bilgi temas zinciri boyunca yayılır.
        private static final int MAX_BLOCKS = 4;
        private final float[] blockX = new float[MAX_BLOCKS];
        private final float[] blockZ = new float[MAX_BLOCKS];
        private int blockCount = 0;

        /** (dx, dz) birim yönünde itilirse bu cisim gerçekten yer değiştirebilir mi? false = hareket edebilir */
        private boolean isBlockedAlong(float dx, float dz){
            if(invMass == 0f) return true; //sabit cisim hiçbir yöne hareket etmez
            if(blockCount == 0) return false;

            //engellenen yönlerin bileşenini at, geriye kalan hareket kabiliyetine bak
            //(tek duvar: yüzeye paralel kayabilir, köşe: iki bileşen de gider ve tamamen sıkışır)
            float px = dx, pz = dz;
            for(int pass = 0; pass < 2; pass++){
                for(int i = 0; i < blockCount; i++){
                    float d = px * blockX[i] + pz * blockZ[i];
                    if(d > 0f){
                        px -= d * blockX[i];
                        pz -= d * blockZ[i];
                    }
                }
            }
            return (px * dx + pz * dz) < PIN_FREE_FRACTION;
        }

        /** Bu cisim (dx, dz) birim yönüne artık hareket edemez */
        private void addBlock(float dx, float dz){
            for(int i = 0; i < blockCount; i++){
                if(blockX[i] * dx + blockZ[i] * dz > 0.995f) return; //zaten kayıtlı
            }
            if(blockCount < MAX_BLOCKS){
                blockX[blockCount] = dx;
                blockZ[blockCount] = dz;
                blockCount++;
            }
        }


        /**
         * @param startY başlangıç tabanı (m); zemin y = 0
         * @param height cismin yüksekliği (m, 0'dan büyük). Cisim [y, y + height] aralığını kaplar.
         */
        public PhysicsEntity(float startX, float startY, float startZ, float startHeading, float mass, float restitution, float height){
            if(!(height > 0f)) //0, negatif ve NaN
                throw new IllegalArgumentException("height 0'dan büyük olmalı: " + height);
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.startHeading = startHeading;
            this.height = height;

            this.position.set(startX, startY, startZ);
            this.heading = startHeading;
            this.mass = mass;
            this.invMass = (mass > 0f) ? 1f / mass : 0f;
            this.restitution = restitution;
        }

        //-----------------------------ÇARPIŞMA OLAYLARI-----------------------------
        //Dinleyiciler çözücü (Physic.solveCollisions) bittikten SONRA, pozisyonlar kesinleşince çağrılır.
        //entityIntersectListener temasın başladığı karede bir kez tetiklenir (temas sürerken her karede tekrar tetiklenmez; onun için entityStayListener),
        //o yüzden "sayıyı arttır" gibi işlemler için güvenlidir. Callback içinde entity silmek/taşımak da güvenlidir.
        public EntityIntersectListener entityIntersectListener;
        public WallIntersectListener wallIntersectListener;
        /** Temas SÜRDÜĞÜ her karede çağrılır (başladığı kare hariç: o kareyi entityIntersectListener alır). Örn. tümsek:
         *  değdiği sürece her karede ivme uygular. Sensörlerde de çalışır. Yalnızca gerçek temas sayılır (histerezis yok). */
        public EntityStayListener entityStayListener;

        public interface EntityIntersectListener{
            /**
             * Bu cisim 'other' ile temas etmeye BAŞLADI.
             * @param other       temas edilen cisim
             * @param impactSpeed temas anındaki birbirine yaklaşma hızı (m/s). Yavaşça dayanmalarda ~0'dır;
             *                    "yeterince sert çarptı mı?" gibi kontroller için kullanılabilir
             */
            void onIntersectWithEntity(PhysicsEntity other, float impactSpeed);
        }
        public interface EntityStayListener{
            /**
             * Bu cisim 'other' ile temas etmeye DEVAM EDİYOR (önceki karede de temastaydı, bu karede de). Çözücüden sonra çağrılır;
             * burada hız/konum değiştirmek serbest, ama etkisi bir sonraki karenin update/çözümünde görülür.
             * Temasın ilk karesinde bunun yerine onIntersectWithEntity çağrılır, yani temas boyunca her karede tam bir çağrı gelir.
             * Çift içinde 'a' ve 'b' ikisinin de dinleyicisi varsa ikisi de çağrılır (karşılıklı).
             * @param other temas edilen cisim
             */
            void onStayWithEntity(PhysicsEntity other);
        }
        public interface WallIntersectListener{
            //x ekseni duvarları: x = 0 (X_MIN) ve x = fieldWidth (X_MAX); z ekseni duvarları: z = 0 (Z_MIN) ve z = fieldDepth (Z_MAX)
            int X_MIN = 1, X_MAX = 2, Z_MIN = 4, Z_MAX = 8;

            /**
             * Bu cisim saha duvarına temas etmeye BAŞLADI.
             * @param wall        hangi duvar: X_MIN, X_MAX, Z_MIN, Z_MAX
             * @param impactSpeed duvara çarpış hızı (m/s)
             */
            void onIntersectWithWall(int wall, float impactSpeed);
        }

        //olay takibi için iç durum (çözücü yönetir)
        private PhysicsEntity[] contacts = new PhysicsEntity[8];     //bu karedeki temaslar
        private PhysicsEntity[] prevContacts = new PhysicsEntity[8]; //önceki karedeki temaslar
        private int contactCount = 0, prevContactCount = 0;
        private int wallMask = 0, prevWallMask = 0;
        private final float[] wallImpact = new float[4]; //duvar başına bu karedeki çarpış hızı (0=X_MIN, 1=X_MAX, 2=Z_MIN, 3=Z_MAX)

        private boolean wasTouching(PhysicsEntity o){
            for(int i = 0; i < prevContactCount; i++) if(prevContacts[i] == o) return true;
            return false;
        }
        private void addContact(PhysicsEntity o){
            if(contactCount == contacts.length) contacts = java.util.Arrays.copyOf(contacts, contactCount * 2);
            contacts[contactCount++] = o;
        }
        protected void noteWallImpact(int idx, float speed){
            if(speed > wallImpact[idx]) wallImpact[idx] = speed;
        }
        /** Kare bitti: bu karenin temaslarını "önceki kare" yap */
        private void endContactFrame(){
            PhysicsEntity[] t = prevContacts;
            java.util.Arrays.fill(t, 0, prevContactCount, null); //eski referansları bırak (silinen cisimler GC'ye gidebilsin)
            prevContacts = contacts;
            prevContactCount = contactCount;
            contacts = t;
            contactCount = 0;
            prevWallMask = wallMask;
            wallMask = 0;
            java.util.Arrays.fill(wallImpact, 0f);
        }

        public abstract void update(float dt);
        public abstract void draw(Canvas canvas, Paint paint);

        /** Cisim havadayken, zeminde (x, z) konumuna çizilen gölge. Varsayılan: gölge yok. drawEntities çağırır. */
        public void drawShadow(Canvas canvas){}

        //-------------------------------YÜZEYLER-------------------------------
        /** Şu an bu cismin üstünde duran/basan yüzey (robot, engel). null = zemin ya da havada. updateVertical yönetir. */
        protected PhysicsEntity supportedBy = null;

        public PhysicsEntity getSupport(){ return supportedBy; }

        /** Bu cismin tepesine başka cisimler konabilir mi? Varsayılan true (robot, engel); toplar override edip false verir.
         *  Sensör (isSensor = true) cisimler her zaman geçilebilir olduğu için üstüne de konulmaz (Physic3D bunu ayrıca eler). */
        public boolean canSupport(){ return true; }

        //-------------------------------SENSÖR (GEÇİLEBİLİR) MODU-------------------------------
        /**
         * true ise cisim bir SENSÖRDÜR: hitbox'ı hiçbir şeyi itmez, hiçbir şey onu itmez, kimse ona çarpıp sekmez, içinden
         * geçilir ve üstüne konulmaz. Buna rağmen temas başlayınca entityIntersectListener'lar (iki tarafta da) normal
         * şekilde tetiklenir, y aralığı filtresi de aynen geçerlidir. Örn. tümsek, bölge/hedef alanı.
         * Sensör, başka bir sensörle ya da sabit cisimle de olay üretebilir (ikisi de sabit değilse).
         * Havadaki bir temasta (ör. üstünden uçan top) y aralıkları örtüşmüyorsa olay da yoktur.
         * İstediğin zaman değiştirilebilir (çözücü her kare bakar).
         */
        public boolean isSensor = false;

        /** Şu an (son solveCollisions'dan sonra) bu cisim 'other' ile temas ediyor mu? Sensörün "içinde mi?" sorusu için:
         *  olay yalnızca temasın başında gelir, temas sürerken bunu her karede sorabilirsin.
         *  Kısa kopmalar (broadphase marjı içinde) temas sayılmaya devam eder (olayla aynı histerezis). */
        public boolean isTouching(PhysicsEntity other){ return wasTouching(other); }

        /** Bu cismin tepesine 'landed' indi. Yüzeyde duran cisim için her karede de çağrılır (yerçekimi her karede
         *  cismi yüzeye indirir). update döngüsü içinden çağrılır: dünyadan cisim ekleme/silme YAPMA, sadece not al ve
         *  çözücüden sonra işle (örn. Robot.updateIntake). */
        protected void onEntityLanded(PhysicsEntity landed){}

        /**
         * Dikey hareket: yerçekimi ve zemin (y = 0) sekmesi. Yalnızca havadaki (y > 0) veya dikey hızı olan cisim
         * için çalışır, yani yerde duran yüzlerce top için maliyeti bir karşılaştırmadır.
         * Yere inince yatay hıza dokunmaz; dikey hızın restitution'ı kadarı geri verilir, MIN_BOUNCE_SPEED altında durur.
         * Zemin y = 0'dır; ancak cismin (x, z) merkezi bir yüzeyin (canSupport) üstündeyse ve cisim yüzeyin tepesinin
         * üstündeyken düşüyorsa yüzeyin tepesine iner, sekmeyi yüzeyle ortak restitution (küçük olan) ile yapar ve
         * 'supportedBy' o yüzey olur. Yüzey altından çekilirse (robot gidince) cisim düşmeye devam eder.
         * Havadaki cisme yatay sürtünme uygulanmaz: bunu çağıran sınıf yerde mi diye (position.y <= 0 ve velocity.y <= 0) bakmalıdır.
         */
        protected void updateVertical(float dt){
            if(invMass == 0f) return; //sabit (sonsuz kütleli) cisim yerçekiminden etkilenmez: hendeğin üstündeki çubuk düşmez
            if(position.y <= 0f && velocity.y == 0f && supportedBy == null) return; //yerde duruyor

            final float oldY = position.y;
            velocity.y -= GRAVITY * dt;
            float newY = oldY + velocity.y * dt;

            PhysicsEntity support = (world != null) ? world.findSupport(this, oldY) : null;
            float floor = (support != null) ? support.position.y + support.height : 0f;

            if(newY <= floor){
                newY = floor;
                float e = (support != null) ? Math.min(restitution, support.restitution) : restitution;
                float impact = -velocity.y;
                velocity.y = (impact > MIN_BOUNCE_SPEED) ? impact * e : 0f;

                if(support != null) support.onEntityLanded(this);
                //yüzeyde "durmak" = sekmesi bitmiş olmak; hâlâ sekiyorsa havada sayılır
                supportedBy = (velocity.y == 0f) ? support : null;
            }
            else supportedBy = null;

            position.y = newY;
        }

        //hitbox'u objenin pozisyon ve açısıyla senkronize eder
        protected void updateHitboxPosition(){
            if(hitbox == null) return;
            hitbox.setPosition(position.x, position.z);
            hitbox.setRotation((float) Math.toDegrees(heading));
        }

        protected void handleWallCollisions(){
            if(hitbox == null || world == null) return; //dünyaya eklenmemiş cismin sahası yoktur

            float[] vertices = hitbox.getTransformedVertices();
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

            for(int i = 0; i < vertices.length; i += 2){
                float vx = vertices[i];
                float vz = vertices[i + 1];
                if(vx < minX) minX = vx;
                if(vx > maxX) maxX = vx;
                if(vz < minZ) minZ = vz;
                if(vz > maxZ) maxZ = vz;
            }

            boolean collision = false;

            //x ekseni (width)
            if(minX < 0){
                position.x += (0 - minX); //dışarı taşan miktar kadar geri it
                noteWallImpact(0, -velocity.x); //olay için çarpış hızını sekmeden ÖNCE kaydet
                velocity.x = -velocity.x * restitution; //duvara çarpınca sek (ya da 0 ile hızı kes
                collision = true;
            }
            else if(maxX > world.fieldWidth){
                position.x -= (maxX - world.fieldWidth);
                noteWallImpact(1, velocity.x);
                velocity.x = -velocity.x * restitution;
                collision = true;
            }

            //z ekseni (depth)
            if(minZ < 0){
                position.z += (0 - minZ);
                noteWallImpact(2, -velocity.z);
                velocity.z = -velocity.z * restitution;
                collision = true;
            }
            else if(maxZ > world.fieldDepth){
                position.z -= (maxZ - world.fieldDepth);
                noteWallImpact(3, velocity.z);
                velocity.z = -velocity.z * restitution;
                collision = true;
            }

            if(collision) updateHitboxPosition();
        }
        protected void updateBroadphase(){

            //preHitbox'ı yeni konuma taşı
            float halfSize = maxBoundingSize / 2f;
            if(preHitbox != null){
                preHitbox.set(
                        position.x - halfSize,
                        position.z - halfSize,
                        position.x + halfSize,
                        position.z + halfSize
                );
            }
        }
        /** Merkezin bulunduğu ızgara hücresini günceller (hücre değiştiyse listeden çıkıp yenisine girer).
         *  Saha dışına taşan konumlar (duvar düzeltmesinden önceki an) kenar hücreye sıkıştırılır. */
        protected void updateGridIndex(){
            if(world == null) return;
            int currentIndex = world.cellRow(position.x) * world.gridCols + world.cellCol(position.z);

            if(currentIndex == gridIndex) return; //hala aynı ızgarada

            if(gridIndex != -1) world.grids.get(gridIndex).remove(this); //eski ızgaradan çık (ilk girişte eski yok)
            gridIndex = currentIndex;
            world.grids.get(gridIndex).add(this); //yeni ızgaraya gir
        }

        public void resetState(){
            position.set(startX, startY, startZ);
            velocity.setZero();
            supportedBy = null;
            heading = startHeading;

            if(preHitbox != null)
                updateBroadphase();
            updateHitboxPosition();

            updateGridIndex(); //konum başlangıca döndü: hücre değiştiyse eski hücreden çıkıp yenisine girer (çift kayıt olmaz)
        }

        /** Cismi verilen konum ve hızla yeniden "doğurur": desteği ve temas geçmişini sıfırlar, hitbox/yayın alanı/ızgarayı günceller.
         * Fırlatma ve ışınlama için; resetState'ten farkı başlangıç konumuna DÖNMEMESİ. Dünyada olsa da olmasa da çağrılabilir
         * (sonradan addEntity edilecekse önce bunu çağır).
         */
        public void respawn(float x, float y, float z, float vx, float vy, float vz){
            position.set(x, y, z);
            velocity.set(vx, vy, vz);
            supportedBy = null;

            java.util.Arrays.fill(contacts, 0, contactCount, null);
            java.util.Arrays.fill(prevContacts, 0, prevContactCount, null);
            contactCount = 0;
            prevContactCount = 0;
            wallMask = 0;
            prevWallMask = 0;
            java.util.Arrays.fill(wallImpact, 0f);

            if(preHitbox != null) updateBroadphase();
            updateHitboxPosition();
            updateGridIndex();
        }

        //--------------------YER DÜZLEMİ (x-z) HIZ YARDIMCILARI--------------------
        //Yükseklik (y) bileşenine dokunmazlar: yerdeki sürüş/sürtünme yalnızca x ve z hızını etkiler.

        /** velocity'nin (x, z) bileşenini (targetX, targetZ) hedefine en fazla maxStep kadar yaklaştırır. */
        protected static void approachPlanarVelocity(Vector3 current, float targetX, float targetZ, float maxStep){
            float dx = targetX - current.x;
            float dz = targetZ - current.z;
            float dist2 = dx * dx + dz * dz;

            //hedefe olan mesafe adımdan küçükse direkt oturt
            if(dist2 <= maxStep * maxStep){
                current.x = targetX;
                current.z = targetZ;
            }
            else{
                float dist = (float) Math.sqrt(dist2);
                current.x += (dx / dist) * maxStep;
                current.z += (dz / dist) * maxStep;
            }
        }

        /** velocity'nin (x, z) bileşeninin büyüklüğünü step kadar azaltır (0'ın altına inmez). */
        protected static void applyPlanarFriction(Vector3 v, float step){
            float speed = (float) Math.sqrt(v.x * v.x + v.z * v.z);
            if(speed <= 0f) return;
            float scale = Math.max(0f, speed - step) / speed;
            v.x *= scale;
            v.z *= scale;
        }

        /** Cismin tabanı (y) */
        public float getBottom(){ return position.y; }
        /** Cismin tepesi (y + height) */
        public float getTop(){ return position.y + height; }
        /** y aralıkları örtüşüyor mu? Üst üste değmek (biri diğerinin tepesinde) örtüşmek sayılmaz. */
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean overlapsY(PhysicsEntity o){
            return position.y < o.position.y + o.height && o.position.y < position.y + height;
        }

        public float getHeading(){ return heading; }
        public Physic3D getWorld(){ return world; }
        public int getGridIndex(){ return gridIndex; }
        /** Yer düzleminde (x-z) uzaklığın karesi; yükseklik farkı hesaba katılmaz. */
        public float getDistanceSquare(PhysicsEntity e){
            float dx = e.position.x - this.position.x;
            float dz = e.position.z - this.position.z;
            return  dx * dx + dz * dz;
        }

    }


    //=====================================================================================
    //                                  ÇARPIŞMA ÇÖZÜCÜ
    //=====================================================================================
    /*
     * Fikir: "kim hareket edebilir?" sorusunu cisimlerin kendi özelliğine bağlamak yerine, temas zinciri boyunca
     * hesaplıyoruz. Her cisim, bu karede hangi yönlere hareket EDEMEYECEĞİNİ biliyor (PhysicsEntity.blockX/blockZ):
     *
     *   - saha duvarına dayalı cisim      -> duvara doğru hareket edemez
     *   - sabit cisme (invMass == 0) dayalı -> o cisme doğru hareket edemez
     *   - kendisini iten cisim hareket edemeyen birine dayanıyorsa, o da o yönde hareket edemez (zincirleme)
     *
     * Bir temasta iten cisim, ittiği cismin o yönde "engelli" olduğunu görürse ittiği cismi sonsuz kütleli gibi
     * davranır: top iç içe geçmez, iten cisim kendi yönünde durdurulur. Engelli değilse top normal şekilde hareket
     * eder ve kuvveti bir sonraki cisme aktarır. Duvar da, gelecekte ekleyeceğin sabit cisimler de aynı mantıkla çalışır
     * (özel durum yok, sabit cisim = invMass 0).
     *
     * Her karede: 1) konum düzeltme (tekrarlı)  2) hız (impulse) düzeltme.
     */

    /**
     * Bu dünyadaki tüm cisimleri tek seferde çözer. Oyun döngüsünde HER KARE, tüm update(dt) çağrılarından
     * sonra bir kez çağır (broadphase da içinde).
     */
    public void solveCollisions(){
        bodyCount = 0;
        for(int i = 0; i < entities.size(); i++){
            PhysicsEntity e = entities.get(i);
            e.solveIndex = -1;
            e.updateGridIndex(); //ızgara, update'lerden sonraki gerçek konumu görsün (her cisim kendi update'inde çağırmak zorunda kalmasın)
            addBody(e);
        }
        solve();
    }

    private void addBody(PhysicsEntity e){
        if(e == null || e.hitbox == null) return;
        if(bodyCount == bodies.length){
            int n = bodyCount * 2;
            bodies = java.util.Arrays.copyOf(bodies, n);
            bMinX = java.util.Arrays.copyOf(bMinX, n);
            bMaxX = java.util.Arrays.copyOf(bMaxX, n);
            bMinZ = java.util.Arrays.copyOf(bMinZ, n);
            bMaxZ = java.util.Arrays.copyOf(bMaxZ, n);
            bSmall = java.util.Arrays.copyOf(bSmall, n);
        }
        e.solveIndex = bodyCount;
        bodies[bodyCount++] = e;
    }

    private void solve(){
        if(bodyCount == 0) return;

        for(int i = 0; i < bodyCount; i++) bodies[i].blockCount = 0;
        buildPairs();

        //duvarlara dayalı cisimleri önceden öğren (iteme başlamadan engelli yönler bilinsin)
        solveWalls();

        //temasları, düzeltme yapılmadan ÖNCE (çarpışma hızları hâlâ gerçek hâliyle) kaydet
        detectContacts();

        //--------------------------KONUM DÜZELTME--------------------------
        for(int it = 0; it < POSITION_ITERATIONS; it++){
            float moved = solvePairPositions();
            moved += solveWalls(); //duvar en sonda: hiçbir cisim sahanın dışında kalamaz
            if(moved < MIN_MOVE) break;
        }

        //--------------------------HIZ DÜZELTME--------------------------
        for(int it = 0; it < VELOCITY_ITERATIONS; it++){
            solvePairVelocities();
            solveWallVelocities();
        }

        for(int i = 0; i < bodyCount; i++){
            bodies[i].updateHitboxPosition();
            bodies[i].updateBroadphase();
            bodies[i].updateGridIndex(); //konumlar kesinleşti: kareler arasında sorgular (en yakın top vb.) doğru hücreyi görsün
        }

        //pozisyonlar ve hızlar kesinleşti, dinleyicileri şimdi çağır
        fireIntersectEvents();
    }

    /** Her çift için kesişme durumunu ve çarpışma (yaklaşma) hızını kaydeder, cisimlerin temas listelerini doldurur */
    private void detectContacts(){
        for(int p = 0; p < pairCount; p++){
            PhysicsEntity a = bodies[pairA[p]];
            PhysicsEntity b = bodies[pairB[p]];
            boolean touch = computeContact(a, b);
            pairTouch[p] = touch;
            if(!touch){
                //Histerezis: önceki karede temastaydılar ve hâlâ yakınlarsa (broadphase marjı içinde) temas "sürüyor" say.
                //Böylece robotun hafif sekmesi gibi 1-2 karelik kopmalar aynı temas için yeni olay üretmez.
                if(a.wasTouching(b)){
                    a.addContact(b);
                    b.addContact(a);
                }
                continue;
            }

            //normal b'den a'ya doğru; yaklaşma hızı = (b'ye göre a'nın) normalin tersi yönündeki hızı
            float approach = -((a.velocity.x - b.velocity.x) * mtv.normal.x + (a.velocity.z - b.velocity.z) * mtv.normal.y);
            pairImpact[p] = Math.max(0f, approach);
            a.addContact(b);
            b.addContact(a);
        }
    }

    /**
     * Temas BU karede başladıysa (önceki karede yoktuysa) dinleyicileri çağırır, sonra takip durumunu bir kare ilerletir.
     * Not: dinleyici içinden solveCollisions() çağırma (çözücünün geçici verileri bozulur).
     * Cisim ekleyip silmek (addEntity/removeEntity) ve yerini değiştirmek serbest.
     */
    private void fireIntersectEvents(){
        //cisim-cisim
        for(int p = 0; p < pairCount; p++){
            if(!pairTouch[p]) continue;
            PhysicsEntity a = bodies[pairA[p]];
            PhysicsEntity b = bodies[pairB[p]];
            if(a.wasTouching(b)){ //temas zaten sürüyordu: yeni olay değil, "devam ediyor" olayı
                if(a.entityStayListener != null) a.entityStayListener.onStayWithEntity(b);
                if(b.entityStayListener != null) b.entityStayListener.onStayWithEntity(a);
                continue;
            }

            if(a.entityIntersectListener != null) a.entityIntersectListener.onIntersectWithEntity(b, pairImpact[p]);
            if(b.entityIntersectListener != null) b.entityIntersectListener.onIntersectWithEntity(a, pairImpact[p]);
        }

        //cisim-duvar
        for(int i = 0; i < bodyCount; i++){
            PhysicsEntity e = bodies[i];
            int started = e.wallMask & ~e.prevWallMask;
            if(started == 0 || e.wallIntersectListener == null) continue;

            if((started & PhysicsEntity.WallIntersectListener.X_MIN) != 0)   e.wallIntersectListener.onIntersectWithWall(PhysicsEntity.WallIntersectListener.X_MIN, e.wallImpact[0]);
            if((started & PhysicsEntity.WallIntersectListener.X_MAX) != 0)  e.wallIntersectListener.onIntersectWithWall(PhysicsEntity.WallIntersectListener.X_MAX, e.wallImpact[1]);
            if((started & PhysicsEntity.WallIntersectListener.Z_MIN) != 0) e.wallIntersectListener.onIntersectWithWall(PhysicsEntity.WallIntersectListener.Z_MIN, e.wallImpact[2]);
            if((started & PhysicsEntity.WallIntersectListener.Z_MAX) != 0)    e.wallIntersectListener.onIntersectWithWall(PhysicsEntity.WallIntersectListener.Z_MAX, e.wallImpact[3]);
        }

        for(int i = 0; i < bodyCount; i++) bodies[i].endContactFrame();
    }

    /**
     * Broadphase: sınır kutuları kesişen (ve ikisi birden sabit olmayan) çiftleri bir kez listeler. Çiftler HER ZAMAN (i, j)
     * i < j ve artan sırada çıkar (tüm çiftleri deneyen O(n²) yöntemle birebir aynı liste), yani çözücünün sonucu değişmez.
     * <br>
     * Izgara (grids) kullanımı: cisimler iki sınıfa ayrılır.
     *  - KÜÇÜK: sınır kutusunun yarı boyutu (+ marj) gridSize/2'yi aşmayanlar (toplar). İki küçük cismin kutuları kesişiyorsa
     *    merkezleri en çok 1 hücre farklıdır; yani her küçük cisim yalnızca kendi hücresine ve 8 komşusuna bakar.
     *  - BÜYÜK: diğerleri (robot, engeller). Büyük-büyük çiftler küçük bir listede doğrudan denenir; büyük-küçük çiftler için büyük
     *    cismin kutusunun (gridSize/2 genişletilmiş) kapladığı hücrelerdeki küçük cisimlere bakılır.
     * Her çift tam bir kez üretilir (küçük-küçük: i'nin gözünden j > i; büyük-küçük: yalnızca büyük tarafından).
     */
    private void buildPairs(){
        pairCount = 0;
        pairKeyCount = 0;
        largeCount = 0;
        final float smallHalf = gridSize * 0.5f - BROADPHASE_MARGIN - 1e-4f; //küçük cismin merkezden kenara en çok uzaklığı

        for(int i = 0; i < bodyCount; i++){
            PhysicsEntity e = bodies[i];
            Rectangle r = e.hitbox.getBoundingRectangle();
            bMinX[i] = r.x - BROADPHASE_MARGIN;
            bMaxX[i] = r.x + r.width + BROADPHASE_MARGIN;
            bMinZ[i] = r.y - BROADPHASE_MARGIN;
            bMaxZ[i] = r.y + r.height + BROADPHASE_MARGIN;

            float halfX = Math.max(e.position.x - r.x, r.x + r.width - e.position.x);
            float halfZ = Math.max(e.position.z - r.y, r.y + r.height - e.position.z);
            bSmall[i] = halfX <= smallHalf && halfZ <= smallHalf; //NaN -> false (büyük sayılır, güvenli taraf)
            if(!bSmall[i]){
                if(largeCount == largeBodies.length) largeBodies = java.util.Arrays.copyOf(largeBodies, largeCount * 2);
                largeBodies[largeCount++] = i;
            }
        }

        //1) küçük - küçük: kendi hücresi + 8 komşu
        for(int i = 0; i < bodyCount; i++){
            if(!bSmall[i]) continue;
            final int gi = bodies[i].gridIndex;
            final int row = gi / gridCols, col = gi % gridCols;
            final int r0 = Math.max(0, row - 1), r1 = Math.min(gridRows - 1, row + 1);
            final int c0 = Math.max(0, col - 1), c1 = Math.min(gridCols - 1, col + 1);
            for(int r = r0; r <= r1; r++){
                for(int c = c0; c <= c1; c++){
                    List<PhysicsEntity> cell = grids.get(r * gridCols + c);
                    for(int k = 0, n = cell.size(); k < n; k++){
                        int j = cell.get(k).solveIndex;
                        if(j <= i || !bSmall[j]) continue; //j < 0: bu karede çözücüde değil
                        addPairIfClose(i, j);
                    }
                }
            }
        }

        //2) büyük - büyük
        for(int a = 0; a < largeCount; a++)
            for(int b = a + 1; b < largeCount; b++)
                addPairIfClose(largeBodies[a], largeBodies[b]);

        //3) büyük - küçük: büyük cismin kutusunun (küçük bir cismin merkezi en fazla gridSize/2 dışarıda olabilir) hücreleri
        final float reach = gridSize * 0.5f;
        for(int a = 0; a < largeCount; a++){
            final int big = largeBodies[a];
            final int r0 = cellRow(bMinX[big] - reach), r1 = cellRow(bMaxX[big] + reach);
            final int c0 = cellCol(bMinZ[big] - reach), c1 = cellCol(bMaxZ[big] + reach);
            for(int r = r0; r <= r1; r++){
                for(int c = c0; c <= c1; c++){
                    List<PhysicsEntity> cell = grids.get(r * gridCols + c);
                    for(int k = 0, n = cell.size(); k < n; k++){
                        int j = cell.get(k).solveIndex;
                        if(j < 0 || !bSmall[j]) continue;
                        addPairIfClose(big, j);
                    }
                }
            }
        }

        //eski yöntemle aynı sırayı vermek için (i, j) sırala; ardından pairA/pairB'ye aç
        java.util.Arrays.sort(pairKeys, 0, pairKeyCount);
        if(pairKeyCount > pairA.length){
            int n = Math.max(pairKeyCount, pairA.length * 2);
            pairA = new int[n];
            pairB = new int[n];
            pairTouch = new boolean[n];
            pairImpact = new float[n];
        }
        //(normalde her çift bir kez üretilir; ızgara listesinde bir cisim yanlışlıkla iki kez kayıtlıysa aynı çifti iki kez çözmeyelim)
        for(int p = 0; p < pairKeyCount; p++){
            if(p > 0 && pairKeys[p] == pairKeys[p - 1]) continue;
            pairA[pairCount] = (int) (pairKeys[p] >>> 32);
            pairB[pairCount] = (int) pairKeys[p];
            pairCount++;
        }
    }

    /** (i, j) çiftini; ikisi de sabit değilse, y aralıkları örtüşüyorsa ve sınır kutuları (marjlı) kesişiyorsa listeye ekler */
    private void addPairIfClose(int i, int j){
        if(i > j){ int t = i; i = j; j = t; }
        PhysicsEntity a = bodies[i], b = bodies[j];
        if(a.invMass == 0f && b.invMass == 0f) return;
        if(!a.overlapsY(b)) return; //y aralıkları örtüşmüyorsa çarpışamazlar
        if(bMinX[i] > bMaxX[j] || bMinX[j] > bMaxX[i] || bMinZ[i] > bMaxZ[j] || bMinZ[j] > bMaxZ[i]) return;

        if(pairKeyCount == pairKeys.length) pairKeys = java.util.Arrays.copyOf(pairKeys, pairKeyCount * 2);
        pairKeys[pairKeyCount++] = ((long) i << 32) | j;
    }

    /** a ve b kesişiyorsa 'mtv'yi doldurur: normal b'den a'ya doğrudur (a bu yönde itilirse ayrılırlar) */
    private boolean computeContact(PhysicsEntity a, PhysicsEntity b){
        if(a instanceof Ball && b instanceof Ball){
            float dx = a.position.x - b.position.x;
            float dz = a.position.z - b.position.z;

            float dist2 = (dx * dx) + (dz * dz);
            float radiusSum = ((Ball)a).getRadius() + ((Ball)b).getRadius();

            if(dist2 >= radiusSum * radiusSum) return false;

            float dist = (float) Math.sqrt(dist2);

            if(dist == 0){
                mtv.normal.set(1, 0);
                mtv.depth = radiusSum;
            }
            else{
                mtv.normal.set(dx / dist, dz / dist);
                mtv.depth = radiusSum - dist;
            }
            return true;
        }
        //Intersector, iki poligon kesişiyorsa 'mtv' vektörünü doldurur
        return Intersector.overlapConvexPolygons(a.hitbox, b.hitbox, mtv);
    }

    /** Saha duvarları: cismi içeri iter ve duvara dayalı olduğunu (engelli yön) kaydeder. Toplam düzeltmeyi döndürür. */
    private float solveWalls(){
        float moved = 0f;
        for(int i = 0; i < bodyCount; i++){
            PhysicsEntity e = bodies[i];
            if(e.invMass == 0f) continue;

            Rectangle r = e.hitbox.getBoundingRectangle();
            float minX = r.x, maxX = r.x + r.width;
            float minZ = r.y, maxZ = r.y + r.height;
            float dx = 0f, dz = 0f;

            if(minX < WALL_SKIN){
                e.wallMask |= PhysicsEntity.WallIntersectListener.X_MIN;
                e.addBlock(-1f, 0f);
                if(minX < 0f) dx = -minX;
            }
            else if(maxX > fieldWidth - WALL_SKIN){
                e.wallMask |= PhysicsEntity.WallIntersectListener.X_MAX;
                e.addBlock(1f, 0f);
                if(maxX > fieldWidth) dx = fieldWidth - maxX;
            }

            if(minZ < WALL_SKIN){
                e.wallMask |= PhysicsEntity.WallIntersectListener.Z_MIN;
                e.addBlock(0f, -1f);
                if(minZ < 0f) dz = -minZ;
            }
            else if(maxZ > fieldDepth - WALL_SKIN){
                e.wallMask |= PhysicsEntity.WallIntersectListener.Z_MAX;
                e.addBlock(0f, 1f);
                if(maxZ > fieldDepth) dz = fieldDepth - maxZ;
            }

            if(dx != 0f || dz != 0f){
                e.position.x += dx;
                e.position.z += dz;
                e.updateHitboxPosition();
                moved += Math.abs(dx) + Math.abs(dz);
            }
        }
        return moved;
    }

    private float solvePairPositions(){
        float moved = 0f;
        for(int p = 0; p < pairCount; p++){
            PhysicsEntity a = bodies[pairA[p]];
            PhysicsEntity b = bodies[pairB[p]];
            if(a.isSensor || b.isSensor) continue; //sensörler itilmez/itmez (olay detectContacts'ta üretilir)
            if(!computeContact(a, b)) continue;

            float nx = mtv.normal.x, nz = mtv.normal.y;

            //a, +n yönünde; b, -n yönünde itilecek. Bu yönlerde hareket edebiliyorlar mı?
            boolean aPinned = a.isBlockedAlong(nx, nz);
            boolean bPinned = b.isBlockedAlong(-nx, -nz);

            //zincirleme yayılım: itilen cisim hareket edemiyorsa iten cisim de ona doğru hareket edemez
            if(bPinned && a.invMass != 0f) a.addBlock(-nx, -nz);
            if(aPinned && b.invMass != 0f) b.addBlock(nx, nz);

            float depth = mtv.depth - PENETRATION_SLOP;
            if(depth <= 0f) continue;

            //hareket edemeyen cisim sonsuz kütleli gibi davranır: düzeltmenin tamamını karşı taraf alır
            float invA = aPinned ? 0f : a.invMass;
            float invB = bPinned ? 0f : b.invMass;
            float totalInvMass = invA + invB;
            if(totalInvMass == 0f) continue; //ikisi de sıkışmış (iki taraftan ezilme), yapılacak bir şey yok

            float aRatio = invA / totalInvMass;
            float bRatio = invB / totalInvMass;

            a.position.x += nx * depth * aRatio;
            a.position.z += nz * depth * aRatio;
            b.position.x -= nx * depth * bRatio;
            b.position.z -= nz * depth * bRatio;

            a.updateHitboxPosition();
            b.updateHitboxPosition();
            moved += depth;
        }
        return moved;
    }

    private void solvePairVelocities(){
        for(int p = 0; p < pairCount; p++){
            PhysicsEntity a = bodies[pairA[p]];
            PhysicsEntity b = bodies[pairB[p]];
            if(a.isSensor || b.isSensor) continue; //sensörler sekmez
            if(!computeContact(a, b)) continue;

            float nx = mtv.normal.x, nz = mtv.normal.y;

            boolean aPinned = a.isBlockedAlong(nx, nz);
            boolean bPinned = b.isBlockedAlong(-nx, -nz);
            float invA = aPinned ? 0f : a.invMass;
            float invB = bPinned ? 0f : b.invMass;
            float totalInvMass = invA + invB;
            if(totalInvMass == 0f) continue;

            //Hareket edemeyen (sıkışmış) cisim bu temasta duvar gibi davranır:
            //  - engelli yöne doğru olan hızı kesilir,
            //  - çarpışma hesabında hızı 0 sayılır (kendi taşıdığı hızla diğerlerini fırlatmasın).
            float vaN = a.velocity.x * nx + a.velocity.z * nz;
            float vbN = b.velocity.x * nx + b.velocity.z * nz;
            if(aPinned){
                if(vaN > 0f){
                    a.velocity.x -= vaN * nx;
                    a.velocity.z -= vaN * nz;
                }
                vaN = 0f;
            }
            if(bPinned){
                if(vbN < 0f){
                    b.velocity.x -= vbN * nx;
                    b.velocity.z -= vbN * nz;
                }
                vbN = 0f;
            }

            float velAlongNormal = vaN - vbN;

            //eğer objeler zaten birbirinden uzaklaşıyorlarsa hızlarına dokunma
            if(velAlongNormal >= 0f) continue;

            //en düşük sekme oranını baz al (örn robot ile top çarparsa seker, robot robot çarpışırsa itişir)
            float e = Math.min(a.restitution, b.restitution);

            //fizik motoru itme büyüklüğü
            float j = -(1f + e) * velAlongNormal / totalInvMass;

            a.velocity.x += nx * j * invA;
            a.velocity.z += nz * j * invA;
            b.velocity.x -= nx * j * invB;
            b.velocity.z -= nz * j * invB;
        }
    }

    /** Duvara dayalı cisimlerin duvara doğru olan hız bileşenini sektirir/keser */
    private void solveWallVelocities(){
        for(int i = 0; i < bodyCount; i++){
            PhysicsEntity e = bodies[i];
            if(e.invMass == 0f) continue;

            Rectangle r = e.hitbox.getBoundingRectangle();
            float minX = r.x, maxX = r.x + r.width;
            float minZ = r.y, maxZ = r.y + r.height;

            if(minX < WALL_SKIN && e.velocity.x < 0f){ e.noteWallImpact(0, -e.velocity.x); e.velocity.x = -e.velocity.x * e.restitution; }
            else if(maxX > fieldWidth - WALL_SKIN && e.velocity.x > 0f){ e.noteWallImpact(1, e.velocity.x); e.velocity.x = -e.velocity.x * e.restitution; }

            if(minZ < WALL_SKIN && e.velocity.z < 0f){ e.noteWallImpact(2, -e.velocity.z); e.velocity.z = -e.velocity.z * e.restitution; }
            else if(maxZ > fieldDepth - WALL_SKIN && e.velocity.z > 0f){ e.noteWallImpact(3, e.velocity.z); e.velocity.z = -e.velocity.z * e.restitution; }
        }
    }



    /*  ---------------------------------OLUŞTURULAN SINIFLAR---------------------------------
    kodda kullanılan ve PhysicEntity'i miras alan sınıflar, eğer gerekmiyorsa silebilirsin
     */

    /** Genel Robot sınıfı, içinde her robot'da olan öteleme ve dönme hareketleri
     * hazır olarak var, sadece intake ve fırlatma aşamalarında override gerikiyor
     * (ve botlar için robotun oyuncu olup olmadığını anlamak için bir override).
     * Böyle yapmamın sebebi her yıl robotların aynı olan özelliklerini tekrar tekrar
     * yazmanın kod kalabalığı ve kullanım zorluğu çıkartacağını düşünmem.
     */
    public static abstract class Robot extends PhysicsEntity{

        //LİMİT VE PARAMETRELER
        public static final float ROBOT_SIZE        = 0.9017f; //bumper dahil 9x9
        public static final float MAX_SPEED         = 4.5f; //maks hız (m/s)
        public static final float ACCELERATION      = 18.0f; //ivme (m/s²)
        public static final float FRICTION          = 12.0f; //sürtünme (m/s²)
        public static final float MAX_ANGULAR_SPEED = 12f; //maksimum dönme hızı (rad/s)
        public static final float ANGULAR_ACCEL     = 40.0f; //açısal ivme (rad/s²)

        //intake: robotun ÖNÜNDE (robotun kendi +x yönü), bumper'ın dışına uzanan algılama kutusu. Çarpışma YAPMAZ (sadece sensör).
        //(INTAKE_WIDTH/INTAKE_DEPTH robotun kendi eksenlerine göredir: width = yana doğru, depth = ileriye doğru; saha eksenleriyle karışmasın)
        public static final float INTAKE_WIDTH      = 0.6f;  //intake'in genişliği (m), robotun önünde ortalı
        public static final float INTAKE_DEPTH      = 0.12f; //bumper'dan dışarı uzanma miktarı (m)

        //YÖN KONTROLCÜSÜ
        /** setInputs'a "dönme komutu yok" demek için: robot dönmeyi bırakır (açısal hız sıfıra iner) */
        public static final float NO_TURN = Float.NaN;
        private static final float HEADING_BRAKE_FACTOR = 0.8f;  //frenleme ivmesinin ne kadarı kullanılsın (1'e yakın = agresif, küçük = yumuşak)
        private static final float HEADING_TOLERANCE    = 0.004f; //~0.23° altında hata ve yavaş dönüş = hedefe ulaşıldı

        //OTONOM
        private final AutoActions actions = new AutoActions();
        private AutoActions.Script autoScript = null;
        public boolean isAutonomous = false;

        //KONTROL GİRDİLERİ
        protected float inputX, inputZ;
        protected float targetHeading = Float.NaN;

        //INTAKE
        /** Intake algılama kutusu: robotla birlikte hareket eder ve döner. Çarpışmaya katılmaz. */
        public final Polygon intakeHitbox;
        /** true ise intake'e değen yakıtlar alınır. Sadece oyun thread'inden (update döngüsünden) değiştir. */
        public boolean intakeActive = false;
        protected final PhysicsEntity[] stored;                 //alınan yakıtlar (LIFO: en son giren ilk çıkar)
        protected int intakeCount = 0;                     //dizideki top sayısı = bir sonraki boş yerin indeksi
        protected final ArrayList<PhysicsEntity> candidates = new ArrayList<>(); //şu an robota değen (veya yakın) yakıtlar

        //SHOOTER
        /** Robot bir yakıt attıktan sonra bir daha yakıt atmak için kaç saniye beklemeli? */
        public final float shooterDelay;
        /** Robotun shooter'ının topu atma açısı. Sabit. Radian cinsinden */
        public final float shooterAngle;
        /** Robotun shooter'ı robot'tun ne kadar üstünde? */
        public final float shooterHeight;
        private float shooterDelayCounter;

        public final boolean isBlueAlliance;

        //TODO temps
        private final Paint headingIndicator = new Paint();

        public Robot(float startX, float startY, float startZ, float startHeading, float robotHeight, int intakeCapacity, boolean isBlueAlliance,
                     float shooterDelay, float shooterAngle, float shooterHeight){
            //kütle (50kg), sekme katsayısı (0.1 - neredeyse hiç sekmez, iter)
            super(startX, startY, startZ, startHeading, 50.0f, 0.1f, robotHeight);
            if(intakeCapacity < 0)
                throw new IllegalArgumentException("intakeCapacity negatif olamaz: " + intakeCapacity);
            if(shooterDelay < 0)
                throw new IllegalArgumentException("Atış bekleme süresi negatir olamaz: "+shooterDelay);
            this.shooterDelay = shooterDelay;
            if(shooterHeight + 0.02f < robotHeight)
                throw new IllegalArgumentException("Shooter robotan yukarıda olmalı: "+shooterHeight);
            this.shooterHeight = shooterHeight;
            if(!(shooterAngle > 0f && shooterAngle < 90f)) //NaN dahil
                throw new IllegalArgumentException("Atış açısı derece cinsinden 0 ile 90 arasında olmalı: " + shooterAngle);
            this.shooterAngle = (float)Math.toRadians(shooterAngle);

            this.isBlueAlliance = isBlueAlliance;
            this.stored = new PhysicsEntity[intakeCapacity];

            headingIndicator.setColor(Color.WHITE);
            headingIndicator.setStrokeWidth(8f);

            float half = ROBOT_SIZE / 2f;
            float[] vertices = new float[]{
                    -half, -half, //sol alt
                    half, -half, //sağ alt
                    half,  half, //sağ üst
                    -half,  half  //sol üst
            };
            this.hitbox = new Polygon(vertices);
            this.hitbox.setOrigin(0, 0); //dönüşün robotun merkezinden olmasını sağlar
            maxBoundingSize = (float)(ROBOT_SIZE * Math.sqrt(2));
            preHitbox = new android.graphics.RectF();

            //intake
            float halfW = INTAKE_WIDTH / 2f;
            this.intakeHitbox = new Polygon(new float[]{
                    half,                 -halfW, //iç alt
                    half + INTAKE_DEPTH,  -halfW, //dış alt
                    half + INTAKE_DEPTH,   halfW, //dış üst
                    half,                  halfW  //iç üst
            });
            this.intakeHitbox.setOrigin(0, 0); //ana hitbox gibi robotun merkezi etrafında döner
            updateHitboxPosition(); //ilk update'ten önce de doğru yerde olsun

            //Robota bir top değdiği anda onu "aday" olarak kaydet. Asıl alma kararı updateIntake()'te verilir.
            //DİKKAT: entityIntersectListener'ı başka bir şeyle değiştirirsen intake çalışmaz; ikisini birleştirmek için
            //değiştirdiğin dinleyicinin içinden onContactWithEntity(other) çağır.
            this.entityIntersectListener = (other, impactSpeed) -> onContactWithEntity(other);
        }

        /**
         * @param inputX joystick X (-1.0 ile 1.0)
         * @param inputZ joystick Z (-1.0 ile 1.0)
         * @param targetHeading robotun bakmasını istediğin yön, RADYAN (heading ile aynı eksen: 0 = +x yönü, +x'ten +z'ye doğru pozitif).
         *                      NaN (Robot.NO_TURN) verirsen dönme komutu yok sayılır ve robot dönmeyi bırakır.
         *                      Robot bu yöne en kısa yoldan, açısal hız/ivme sınırlarına uyarak ve aşmadan döner.
         */
        public void setInputs(float inputX, float inputZ, float targetHeading){
            setInputX(inputX); setInputZ(inputZ);
            setTargetHeading(targetHeading);
        }
        public void setInputXZ(float inputX, float inputZ){
            setInputX(inputX); setInputZ(inputZ);
        }
        public void setInputX(float inputX){ this.inputX = inputX; }
        public void setInputZ(float inputZ){ this.inputZ = inputZ; }
        public void setTargetHeading(float targetHeading){ this.targetHeading = targetHeading; }

        /** Robotun aksiyon kuyruğu: addAutoTask / interrupt / clear / isEmpty buradan kullanılır. */
        public AutoActions getActions(){ return actions; }

        /** Otonom aksiyonlarını kuran fonksiyon. Her resetState()'te (oyun başında) kuyruk temizlenir ve bu çağrılır.
         * null verirsen robot otonomda hiçbir şey yapmaz. En son kendini döndürür. */
        public Robot setAutoScript(AutoActions.Script autoScript){this.autoScript = autoScript; return this; }

        /** Robotun mevcut yönünden verilen hedef yöne EN KISA yoldan açı farkı, (-PI, PI] aralığında (radyan).
         * Pozitif = +x'ten +z'ye doğru (heading artış yönünde) dönmek gerekir. Hedef hangi aralıkta olursa olsun (örn. atan2'nin
         * -PI..PI çıktısı ya da 0..2PI) doğru çalışır: bu yüzden açıları "a - b" diye doğrudan karşılaştırma,
         * 0 ve 2PI aynı yöndür. */
        public float getHeadingError(float target){
            float err = (target - heading) % MathUtils.PI2;
            if(err > MathUtils.PI) err -= MathUtils.PI2;
            else if(err <= -MathUtils.PI) err += MathUtils.PI2;
            return err;
        }

        /** Otonomu zorla bitirir: çalışan aksiyonun end()'ini çağırır, kalan kuyruğu iptal eder ve robotu durdurur
         * (intake de kapanır). Otonom süresi aksiyonlar bitmeden dolduğunda kullan, yoksa robot son girdisiyle
         * gitmeye devam ederdi. */
        public void stopAutonomous(){
            actions.clear(this);
            intakeActive = false;
            setInputs(0, 0, NO_TURN);
            isAutonomous = false;
        }

        @Override
        protected void updateHitboxPosition(){
            super.updateHitboxPosition();
            if(intakeHitbox == null) return;
            intakeHitbox.setPosition(position.x, position.z);
            intakeHitbox.setRotation((float) Math.toDegrees(heading));
        }

        /** @param dt geçen süre (60 TPS için dt = 1/60f ≈ 0.0166s) */
        @Override
        public void update(float dt){

            //otonomda veya bot ise otonom aksiyonları çalıştır
            if(isAutonomous || !isPlayerRobot()){
                if(actions.execute(this, dt))
                    //kuyruk boş, robotu durdurmak içın inputları sıfırla
                    this.setInputs(0, 0, NO_TURN);
            }

            //atış bekleme süresi
            if(shooterDelayCounter > 0)
                shooterDelayCounter = Math.max(0, shooterDelayCounter - dt);

            //--------------------------ÖTELEME HAREKETİ (yer düzlemi: x-z)--------------------------
            float targetVX = inputX, targetVZ = inputZ;
            float inputLen = (float) Math.sqrt(targetVX * targetVX + targetVZ * targetVZ);

            if(inputLen >= 0.05f){ //ölü bölge dışı
                if(inputLen > 1.0f){ //çapraz gitmede 1.0'ı geçmesin
                    targetVX /= inputLen;
                    targetVZ /= inputLen;
                }
                //ivmeleme ve yumuşatılmış sürüş
                approachPlanarVelocity(velocity, targetVX * MAX_SPEED, targetVZ * MAX_SPEED, ACCELERATION * dt);
            }
            else{
                //girdi yoksa sürtünme ile yavaşla
                applyPlanarFriction(velocity, FRICTION * dt);
            }

            //--------------------------------------DÖNME HAREKETİ--------------------------------------
            float targetAngularVelocity = 0f;
            if(!Float.isNaN(targetHeading) && !Float.isInfinite(targetHeading))
                targetAngularVelocity = headingToAngularVelocity(targetHeading, dt);

            if(angularVelocity < targetAngularVelocity)
                angularVelocity = Math.min(targetAngularVelocity, angularVelocity + ANGULAR_ACCEL * dt);
            else if(angularVelocity > targetAngularVelocity)
                angularVelocity = Math.max(targetAngularVelocity, angularVelocity - ANGULAR_ACCEL * dt);

            //açı güncellemesi
            heading += angularVelocity * dt;
            //açıyı 0 - 2PI arasında tut
            heading = (heading % MathUtils.PI2 + MathUtils.PI2) % MathUtils.PI2;


            //konum güncellemesi
            position.x += velocity.x * dt;
            position.z += velocity.z * dt;

            //---------ÇARPIŞMA KONTROLLERİ---------
            updateHitboxPosition();
            handleWallCollisions();
            updateBroadphase();
        }

        /**
         * Hedef yöne en kısa yoldan, aşmadan varmak için istenen açısal hızı hesaplar.

         * Fikir: robot ANGULAR_ACCEL ile frenleyebiliyorsa, kalan açı 'err' iken güvenle sahip olabileceği en yüksek
         * hız v = sqrt(2 * a * err)'dir (tam o hızda fren yapınca hedefte durur). Uzakta bu hız MAX_ANGULAR_SPEED'i
         * aşar, robot tam hızda döner; yaklaşınca hız karekökle düşer ve robot yumuşakça oturur.
         * Gerçek açısal hıza ulaşma ise aşağıdaki ivme sınırıyla yapılır, yani dönüş "gerçekçi" (anında değil).
         */
        private float headingToAngularVelocity(float target, float dt){
            //en kısa yoldan açı farkı (-PI, PI]
            float err = (target - heading) % MathUtils.PI2;
            if(err > MathUtils.PI) err -= MathUtils.PI2;
            else if(err <= -MathUtils.PI) err += MathUtils.PI2;

            float absErr = Math.abs(err);
            if(absErr < HEADING_TOLERANCE && Math.abs(angularVelocity) < ANGULAR_ACCEL * dt)
                return 0f; //hedefte ve neredeyse duruyor: titremesin

            float speed = (float) Math.sqrt(2f * ANGULAR_ACCEL * HEADING_BRAKE_FACTOR * absErr);
            speed = Math.min(speed, MAX_ANGULAR_SPEED);
            speed = Math.min(speed, absErr / dt); //tek karede hedefi aşmasın
            return err >= 0f ? speed : -speed;
        }

        //----------------------------------INTAKE MANTIĞI-----------------------------------
        /*
         * İşleyiş:
         *  1) Robota bir yakıt DEĞDİĞİ AN (çarpışma olayı) yakıt "aday" listesine yazılır (onContactWithEntity).
         *  2) Her karede, solveCollisions()'tan SONRA updateIntake() çağrılır: adaylardan intake kutusuna değenler,
         *     intake açıksa ve yer varsa dünyadan çıkarılıp diziye (stored) alınır.
         *  Çarpışma olayı yalnızca temas BAŞLARKEN gelir; adaylar sayesinde "dolu iken değen yakıt", "önce yan yüze değip
         *  sonra intake'e kayan yakıt" gibi durumlar da, temas sürdüğü sürece sonradan alınabilir.
         */

        /** Robotun herhangi bir yerine bir cisim değmeye başladı (listener'dan çağrılır). */
        private void onContactWithEntity(PhysicsEntity other){
            if(!isTargetFuel(other)) return;
            for(int i = 0; i < candidates.size(); i++) if(candidates.get(i) == other) return; //zaten aday
            candidates.add(other);
        }

        private void removeCandidateAt(int i){
            int last = candidates.size() - 1;
            if(i != last) candidates.set(i, candidates.get(last));
            candidates.remove(last);
        }

        /**
         * Bu yakıt şu anda intake kutusuna değiyor / içinde mi?
         * Çarpışma çözücü her kare sonunda pozisyonları kesinleştirdiği için solveCollisions()'tan SONRA çağır.
         */
        public boolean isTouchingIntake(PhysicsEntity fuel){
            if(!isTargetFuel(fuel)) return false;
            if(fuel == null || fuel.hitbox == null) return false;
            if(!overlapsY(fuel)) return false; //intake robotun yükseklik aralığında çalışır (örn. robotun tepesindeki/üstündeki yakıt alınmaz)

            Rectangle r = intakeHitbox.getBoundingRectangle();
            float br = getFuelHalfSize();
            if(fuel.position.x + br < r.x || fuel.position.x - br > r.x + r.width ||
                    fuel.position.z + br < r.y || fuel.position.z - br > r.y + r.height) return false;

            return Intersector.overlapConvexPolygons(intakeHitbox, fuel.hitbox);
        }

        /** Robot bu yakıtı almış mı? */
        public boolean isIntakeContains(PhysicsEntity fuel){
            if(!isTargetFuel(fuel)) return false;

            for(int i = 0; i < intakeCount; i++)
                if(stored[i] == fuel) return true;

            return false;
        }

        /**
         * Aday yakıtlardan intake'e değenleri alır. Oyun döngüsünde HER KARE, physic.solveCollisions()'tan hemen sonra
         * (her robot için) bir kez çağır. Aday listesinde yalnızca robota değmiş birkaç yakıt olur, tüm cisimler taranmaz.
         */
        public void updateIntake(){
            if(world == null) return;

            if(candidates.isEmpty()) return;

            //Adaydan düşürme mesafesi: çözücü, sınır kutuları (BROADPHASE_MARGIN payıyla) ayrılana kadar temasın sürdüğünü
            //sayar; bu en kötü köşegen durumda ~1.15 m. Daha yakında düşürürsek, çözücü "temas sürüyor" derken biz yakıtı
            //unutmuş oluruz ve yeni olay gelmez. O yüzden bundan biraz uzağa kadar tut.
            float drop = (maxBoundingSize / 2f + getFuelHalfSize() + BROADPHASE_MARGIN) * 1.4143f + 0.05f;
            float drop2 = drop * drop;

            for(int i = candidates.size() - 1; i >= 0; i--){
                PhysicsEntity fuel = candidates.get(i);

                float dx = fuel.position.x - position.x;
                float dz = fuel.position.z - position.z;
                if(fuel.getWorld() != world || dx * dx + dz * dz > drop2){
                    removeCandidateAt(i); //başkası aldı/silindi ya da robottan uzaklaştı
                    continue;
                }

                if(!intakeActive || intakeCount >= stored.length) continue; //kapalı ya da dolu: aday kalsın, sonra denenir
                if(!isTouchingIntake(fuel)) continue;                        //henüz intake'e girmedi: aday kalsın

                removeCandidateAt(i);
                if(world.removeEntity(fuel)){ //true = yakıt bizim (iki robot aynı yakıtı alamaz)
                    fuel.velocity.setZero();
                    stored[intakeCount++] = fuel;
                }
            }
        }

        /** Robotun taşıdığı yakıt sayısı */
        public int getIntakeCount(){ return intakeCount; }

        /** Robotun maksimum yakıt kapasitesi */
        public int getIntakeCapacity(){ return stored.length; }

        /** Taşınan tüm yakıtları başlangıç konumlarına döndürüp dünyaya geri koyar ve aday listesini temizler */
        protected void releaseAllIntakeFuels(){
            candidates.clear();
            for(int i = 0; i < intakeCount; i++){
                PhysicsEntity fuel = stored[i];
                stored[i] = null;
                fuel.resetState();
                if(world != null) world.addEntity(fuel);
            }
            intakeCount = 0;
        }

        //----------------------------------SHOOTER MANTIĞI----------------------------------

        public boolean shoot(float targetX, float targetY, float targetZ){
            //atış yapılamaz
            if(!canShoot(targetX, targetY, targetZ)) return false;

            float dx = targetX - position.x;
            float dy = targetY - (position.y + shooterHeight);
            float dz = targetZ - position.z;

            //atış yap
            boolean isShootSuccessful = launch(dx, dy, dz);

            //atış başarılı ise bekleme başlasın
            if(isShootSuccessful) shooterDelayCounter = shooterDelay;
            return isShootSuccessful;
        }

        /** Şu an atış yapılabilir mi? Taban kural: dünyada, bekleme süresi bitmiş ve intake'te yakıt var.
         * Alt sınıflar (örn. hedefe ulaşılabilir mi?) super.canShoot'u çağırıp kendi koşullarını ekler. */
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean canShoot(float targetX, float targetY, float targetZ){
            return world != null && shooterDelayCounter <= 0f && intakeCount > 0;
        }

        public void draw(Canvas canvas, Paint robotPaint){
            //--------------------------------robot çizimi--------------------------------
            //ekran yatayı = z, ekran dikeyi = x
            float robotPxX = getViewport().toPixelX(position.z);
            //noinspection SuspiciousNameCombination
            float robotPxY = getViewport().toPixelY(position.x);
            float robotPxSize = getViewport().toPixelSize(Robot.ROBOT_SIZE);

            robotPaint.setColor((isBlueAlliance) ? Color.BLUE : Color.RED);

            canvas.save();
            //heading 0 = +x = ekranda yukarı ve +z'ye (sağa) doğru artar; robot gövdesi "sağa bakacak" çizildiği için
            //ekran açısı = 90° - heading, canvas.rotate ise saat yönünde pozitif: dönüş = heading - 90°
            canvas.rotate((float)Math.toDegrees(heading) - 90f, robotPxX, robotPxY);

            //robot gövdesi
            canvas.drawRect(
                    robotPxX - robotPxSize / 2f, robotPxY - robotPxSize / 2f,
                    robotPxX + robotPxSize / 2f, robotPxY + robotPxSize / 2f, robotPaint
            );
            //Ön Yönünü Gösteren Küçük Bir Çizgi/İşaret
            canvas.drawLine(robotPxX, robotPxY, robotPxX + (robotPxSize / 2f), robotPxY, headingIndicator);

            if(com.novatron.novatronbiocore.Configs.DEBUG){
                float halfIntakeW = getViewport().toPixelSize(INTAKE_WIDTH/2f);
                float intakeDepth = getViewport().toPixelSize(INTAKE_DEPTH);
                canvas.drawRect(robotPxX + robotPxSize / 2f, robotPxY + halfIntakeW,
                        robotPxX + robotPxSize / 2f + intakeDepth, robotPxY - halfIntakeW,
                        getGamePanel().ui.fontDebug);
            }
            canvas.restore();
        }

        /** Robotu başlangıç konumuna öteler, girdileri sıfırlar, aksiyon listesini temizler
         *  intake'teki yakıtları geri sahaya bırakır, intake'i kapatır ve ononom moduna alır. */
        @Override
        public void resetState(){
            actions.clear(this); //önceki maçtan kalan aksiyonlar (end() çağırılır)
            releaseAllIntakeFuels();
            intakeActive = false;
            super.resetState();
            angularVelocity = 0f;
            setInputs(0, 0, NO_TURN);

            if(autoScript != null)
                autoScript.build(this);
            isAutonomous = true;

            shooterDelayCounter = 0;
        }


        //----------------------------ALT SINIFIN TANIMLAYACAKLARI----------------------------
        /** Bu robot mevcut oyundaki oyuncunun kontrol ettiği robot mu? */
        public abstract boolean isPlayerRobot();
        /** Bu robotun intake'ine aldığı yakıtlardan mı? Her sezon yakıt değişiyor. */
        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public abstract boolean isTargetFuel(PhysicsEntity e);
        /** shoot metodunun içinde çağırılır. Sezona özel yakıtın fırlatılma şekli değişebilir.
         * atış beklemesi shoot'un içinde hallediliyor, sadece top nasıl fırlatılacak.
         * @return fırlatma başarılı mı?
         */
        protected abstract boolean launch(float dx, float dy, float dz);

        public abstract float getFuelHalfSize();

        //TODO resim eklendiğinde silinecekler
        protected abstract Viewport getViewport();
        protected abstract GamePanel getGamePanel();
    }

    public static abstract class Ball extends PhysicsEntity{

        public Ball(float startX, float startY, float startZ, float startHeading, float mass, float restitution, float height){
            super(startX, startY, startZ, startHeading, mass, restitution, height);

            //8 noktalı dairesel çokgen hitbox
            int numPoints = 8;
            float[] vertices = new float[numPoints * 2];
            for(int i = 0; i < numPoints; i++){
                float angle = (float)(i * 2 * Math.PI / numPoints);
                vertices[i * 2] = getRadius() * (float)Math.cos(angle); //x
                vertices[i * 2 + 1] = getRadius() * (float)Math.sin(angle); //z
            }

            this.hitbox = new Polygon(vertices);
            this.hitbox.setOrigin(0, 0);
            maxBoundingSize = getRadius()*2;
            preHitbox = new android.graphics.RectF();
        }

        @Override
        public void update(float dt){
            //bir yüzeyin üstündeysek: çok kaygan, yüzeyin merkezinden dışarı doğru kay (kenara varınca düşer)
            if(supportedBy != null && world != null){
                float dx = position.x - supportedBy.position.x;
                float dz = position.z - supportedBy.position.z;
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                if(d < getSlideCenterRadius()){
                    float ang = world.random.nextFloat() * MathUtils.PI2;
                    dx = (float) Math.cos(ang);
                    dz = (float) Math.sin(ang);
                }
                else{
                    dx /= d;
                    dz /= d;
                }
                velocity.x += dx * getSurfaceSlideAccel() * dt;
                velocity.z += dz * getSurfaceSlideAccel() * dt;
            }

            //yerde yuvarlanırken sürtünme ile yavaşlama (havadayken ve yerden fırlatıldığı karede yok)
            if(position.y <= 0f && velocity.y <= 0f)
                applyPlanarFriction(velocity, getGroundFriction() * dt);

            //konum güncellemesi (yatay), sonra dikey (yerçekimi)
            position.x += velocity.x * dt;
            position.z += velocity.z * dt;
            updateHitboxPosition(); //yüzey aramasının güncel (x, z)'yi görmesi için dikeyden önce
            updateVertical(dt);

            //hitbox'ı güncelle ve duvarlardan sekmesini sağla
            updateHitboxPosition();
            handleWallCollisions();
            updateBroadphase();
        }

        @Override //toplar üst üste yığılmaz
        public boolean canSupport(){ return false; }

        public abstract float getRadius();
        /** Yüzey merkezine bu kadar yakınsa (m) kayma yönü belirsizdir: dünyanın rastgelesinden bir yön seçilir */
        public abstract float getSlideCenterRadius();
        /** Bir yüzeyin (robot, engel) üstünde duran topun yüzeyin merkezinden uzağa kayma ivmesi (m/s²): üstte kalmaz */
        public abstract float getSurfaceSlideAccel();
        /** Zemin üzerindeki yuvarlanma sürtünmesi */
        public abstract float getGroundFriction();
    }

    /**
     * Sabit (hareket ettirilemez) dikdörtgen prizma engel: hendek ayağı/çubuğu, hub, tümsek vb.
     * Kütlesi sonsuzdur (invMass = 0): hiçbir şey onu itemez, yerçekiminden de etkilenmez. Üstüne robot/yakıt konabilir (canSupport).
     * <br>
     * KONUM: (startX, startZ) engelin MERKEZİDİR (robot gibi), startY tabanıdır (havada duran çubuk için > 0).
     * Ölçüler: width = x yönündeki ölçü, depth = z yönündeki ölçü, height = yükseklik. startHeading ile yer düzleminde döndürülebilir
     * (0 = width'in x ekseninde olduğu hâl).
     * <br>
     * Çizim: üstten görünüşte, hitbox'ın köşelerinden oluşan dolu bir çokgen (dönük engelde de doğru çizilir).
     */
    public static class Obstacle extends PhysicsEntity{

        public static final float OBSTACLE_RESTITUTION = 0.1f; //engellerin sekme katsayısı (topun inişinde min(top, engel) alınır)

        public final float width, depth; //x ve z yönündeki ölçüler (m)

        protected final Viewport viewport;
        protected final Paint paint;
        private final Path path = new Path(); //çizim için tekrar kullanılan geçici çokgen (her karede yeni nesne yaratmaz)

        /**
         * @param startX engelin merkezinin x konumu (m)
         * @param startY engelin tabanı (m), zemin = 0
         * @param startZ engelin merkezinin z konumu (m)
         * @param width x yönündeki ölçü (m, 0'dan büyük)
         * @param depth z yönündeki ölçü (m, 0'dan büyük)
         * @param height yükseklik (m, 0'dan büyük)
         * @param temp   geçici çizim boyası (texture eklenince silinecek); null verilirse drawEntities'in verdiği boya kullanılır
         */
        public Obstacle(float startX, float startY, float startZ, float startHeading, float width, float height, float depth,
                        Viewport viewport, Paint temp){
            super(startX, startY, startZ, startHeading, 0f, OBSTACLE_RESTITUTION, height);
            if(!(width > 0f) || !(depth > 0f)) //0, negatif ve NaN
                throw new IllegalArgumentException("width ve depth 0'dan büyük olmalı: " + width + " x " + depth);
            this.width = width;
            this.depth = depth;
            this.viewport = viewport;
            this.paint = temp; //TODO texture eklenince silinecekler

            //hitbox: merkezi (0, 0) olan dikdörtgen; (x, z) köşeleri. Robotla aynı mantık: dönüş merkezden olur.
            float halfW = width / 2f, halfD = depth / 2f;
            this.hitbox = new Polygon(new float[]{
                    -halfW, -halfD,
                    halfW, -halfD,
                    halfW,  halfD,
                    -halfW,  halfD
            });
            this.hitbox.setOrigin(0, 0);

            //yayın alanı (broadphase): dönse bile engeli içine alan kare = köşegen uzunluğu
            maxBoundingSize = (float) Math.sqrt(width * width + depth * depth);
            preHitbox = new android.graphics.RectF();

            //ilk resetAll'dan/ilk update'ten önce de doğru yerde olsun (engel hiç update edilmez: update boş)
            updateHitboxPosition();
            updateBroadphase();
        }

        /** Geçilebilir (sensör) moda al/çıkar: hitbox itmez, içinden geçilir ama temas olayları tetiklenir (bkz. PhysicsEntity.isSensor).
         *  Örn. tümsek: addEntity(new Obstacle(...).setSensor(true)). En son kendini döndürür. */
        public Obstacle setSensor(boolean sensor){ this.isSensor = sensor; return this; }

        @Override
        public void update(float dt){} //sabit: hareket etmez, hitbox ve yayın alanı yapıcıda ve resetState'te kurulur

        @Override
        public void draw(Canvas canvas, Paint drawPaint){
            if(hitbox == null) return;

            //ekran yatayı = z, ekran dikeyi = x: köşeleri tek tek ekrana çevirmek, dönüşü ayrıca düşünmeyi gereksiz kılar
            final float[] v = hitbox.getTransformedVertices(); //(x, z) çiftleri, dünya konumunda
            path.reset();
            for(int i = 0; i < v.length; i += 2){
                float px = viewport.toPixelX(v[i + 1]);
                float py = viewport.toPixelY(v[i]);
                if(i == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }
            path.close();

            canvas.drawPath(path, (paint != null) ? paint : drawPaint);
        }
    }
}