package com.novatron.novatronbiocore.Games.FRCGames.Rebuilt.Brains;

import com.novatron.novatronbiocore.Games.FRCGames.Physic3D;

import java.util.Arrays;

/**
 * Botların yol bulması için saha haritası: sahayı CELL (0.1 m) karelere böler, her kare "robotun MERKEZİ buraya gelebilir mi?"
 * sorusunun cevabını tutar (engeller robotun yarı boyu kadar şişirilmiştir, yani robot bir noktaymış gibi planlanır).
 * <br>
 * Harita, fizik dünyasındaki sabit engellerden (Obstacle) OTOMATİK kurulur ve robotun YÜKSEKLİĞİNE göre farklıdır: robot ile
 * engelin y aralıkları örtüşmüyorsa engel o robot için yoktur (örn. kısa robot hendeğin çubuğunun altından geçer, uzun geçemez).
 * Sensör engeller (tümsek) geçilebilirdir, yalnızca biraz pahalı sayılır. Bu yüzden haritayı robot yüksekliği başına bir kez
 * kur (FRCGameRebuilt.getNavGrid) ve paylaş.
 * <br>
 * Koordinatlar fizik dünyasıyla aynı: x = sahanın kısa kenarı, z = uzun kenarı (metre).
 * <br>
 * THREAD: arama tamponları haritanın içinde tutulur ve paylaşılır (bellek ayırmamak için), yani harita tek thread'den
 * (oyun döngüsü) kullanılmalıdır. Her çağrı bir öncekinin sonucunu ezer.
 */
public final class NavGrid{

    public static final float CELL = 0.1f;

    //yarı boy cinsinden: duvara bu kadar yakın kareler kapalı (duvar zaten robotu içeri iter, o yüzden hafif gevşek)
    private static final float WALL_FRACTION = 0.97f;
    //engellerin robot yarı boyuna eklenen güvenlik payı (m). Kare merkezi ile test edildiği için en az CELL/2'ye yakın olmalı
    private static final float OBSTACLE_MARGIN = 0.06f;
    private static final float NEAR_BLOCKED_COST = 0.6f; //kapalı bir kareye bitişik karelerin ek maliyeti (yürüyüş mesafesinin katı)
    private static final float SENSOR_COST = 0.25f;      //tümsek gibi geçilebilir engellerin ek maliyeti
    private static final float LOS_STEP = 0.04f;         //görüş hattı testinde örnekleme aralığı (m)
    private static final float LOS_ENDPOINT_SLACK = 0.30f; //uçlara bu kadar yakın yerde "kapalıya bitişik" kareler tolere edilir
    private static final float INF = Float.POSITIVE_INFINITY;

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};
    private static final float[] STEP = {1f, 1f, 1f, 1f, 1.41421356f, 1.41421356f, 1.41421356f, 1.41421356f};

    public final int nx, nz;           //x ve z yönündeki kare sayısı
    public final float width, depth;   //saha ölçüsü (m)
    public final float robotHeight;

    private final boolean[] blocked;   //robotun merkezi bu karede olamaz
    private final boolean[] tight;     //kapalı ya da kapalıya bitişik (görüş hattı testi için)
    private final float[] extra;       //ek maliyet katsayısı (0 = normal)

    //dinamik (geçici) ceza: diğer robotların etrafı. findPath bunu okur.
    private final float[] dyn;
    private int[] dynTouched = new int[256];
    private int dynCount;

    //arama tamponları (seri numarasıyla "temizlenir")
    private final float[] gCost;
    private final int[] parent;
    private final int[] seen, closed;
    private int serial;
    private int[] heapNode = new int[1024];
    private float[] heapKey = new float[1024];
    private int heapSize;

    //mesafe haritası (distanceFrom'un sonucu)
    private final float[] field;
    private boolean fieldValid;

    /** Bir yol: (x[i], z[i]) noktaları, ilki robotun konumu, sonuncusu hedef. */
    public static final class Route{
        public float[] x = new float[64], z = new float[64];
        public int size;

        public void clear(){ size = 0; }
        public void add(float px, float pz){
            if(size == x.length){
                x = Arrays.copyOf(x, size * 2);
                z = Arrays.copyOf(z, size * 2);
            }
            x[size] = px; z[size] = pz; size++;
        }
        public float length(){
            float len = 0f;
            for(int i = 1; i < size; i++) len += (float) Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
            return len;
        }
    }

    // ------------------------------------------------------------------------------------------ kurulum

    /**
     * @param world fizik dünyası (engeller entities'ten okunur; engeller hareket etmediği için bir kez kurmak yeter)
     * @param robotHeight robotun yüksekliği (engellerle y aralığı karşılaştırması için)
     * @param robotHalfSize robotun yarı boyu (kare gövdenin yarı kenarı)
     */
    public static NavGrid build(Physic3D world, float robotHeight, float robotHalfSize){
        return new NavGrid(world, robotHeight, robotHalfSize);
    }

    private NavGrid(Physic3D world, float robotHeight, float half){
        this.width = world.fieldWidth;
        this.depth = world.fieldDepth;
        this.nx = (int) Math.ceil(width / CELL);
        this.nz = (int) Math.ceil(depth / CELL);
        this.robotHeight = robotHeight;

        final int n = nx * nz;
        blocked = new boolean[n];
        tight = new boolean[n];
        extra = new float[n];
        dyn = new float[n];
        gCost = new float[n];
        parent = new int[n];
        seen = new int[n];
        closed = new int[n];
        field = new float[n];

        //duvarlar
        final float wall = half * WALL_FRACTION;
        for(int ix = 0; ix < nx; ix++){
            float cx = (ix + 0.5f) * CELL;
            for(int iz = 0; iz < nz; iz++){
                float cz = (iz + 0.5f) * CELL;
                if(cx < wall || cx > width - wall || cz < wall || cz > depth - wall) blocked[ix * nz + iz] = true;
            }
        }

        //engeller
        for(Physic3D.PhysicsEntity e : world.entities){
            if(!(e instanceof Physic3D.Obstacle)) continue;
            final Physic3D.Obstacle o = (Physic3D.Obstacle) e;
            //robot [0, robotHeight] aralığını kaplar, engel [y, y + height]
            if(!(0f < o.position.y + o.height && o.position.y < robotHeight)) continue;

            final boolean sensor = o.isSensor;
            final float grow = (sensor) ? 0f : half + OBSTACLE_MARGIN;
            final float hw = o.width / 2f + grow, hd = o.depth / 2f + grow;
            final float c = (float) Math.cos(o.startHeading), s = (float) Math.sin(o.startHeading);
            final float reach = (float) Math.hypot(hw, hd);

            int x0 = Math.max(0, (int) Math.floor((o.position.x - reach) / CELL));
            int x1 = Math.min(nx - 1, (int) Math.floor((o.position.x + reach) / CELL));
            int z0 = Math.max(0, (int) Math.floor((o.position.z - reach) / CELL));
            int z1 = Math.min(nz - 1, (int) Math.floor((o.position.z + reach) / CELL));

            for(int ix = x0; ix <= x1; ix++){
                for(int iz = z0; iz <= z1; iz++){
                    float dx = (ix + 0.5f) * CELL - o.position.x, dz = (iz + 0.5f) * CELL - o.position.z;
                    float lx = dx, lz = dz;
                    if(o.startHeading != 0f){ //dönük engel: kareyi engelin eksenlerine çevir
                        lx = dx * c + dz * s;
                        lz = -dx * s + dz * c;
                    }
                    if(Math.abs(lx) < hw && Math.abs(lz) < hd){
                        if(sensor) extra[ix * nz + iz] = Math.max(extra[ix * nz + iz], SENSOR_COST);
                        else blocked[ix * nz + iz] = true;
                    }
                }
            }
        }

        //kapalıya bitişik kareler: görüş hattında yasak, aramada pahalı
        for(int ix = 0; ix < nx; ix++){
            for(int iz = 0; iz < nz; iz++){
                final int i = ix * nz + iz;
                if(blocked[i]){ tight[i] = true; continue; }
                boolean near = false;
                for(int d = 0; d < 8 && !near; d++){
                    int ax = ix + DX[d], az = iz + DZ[d];
                    if(ax < 0 || az < 0 || ax >= nx || az >= nz || blocked[ax * nz + az]) near = true;
                }
                if(near){
                    tight[i] = true;
                    extra[i] += NEAR_BLOCKED_COST;
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ sorgular

    private int cellX(float x){ return (int) Math.floor(x / CELL); }
    private int cellZ(float z){ return (int) Math.floor(z / CELL); }
    private boolean inGrid(int ix, int iz){ return ix >= 0 && iz >= 0 && ix < nx && iz < nz; }

    /** (x, z) noktası robotun merkezi için yasak bölgede mi? (saha dışı da yasak) */
    public boolean isBlocked(float x, float z){
        int ix = cellX(x), iz = cellZ(z);
        return !inGrid(ix, iz) || blocked[ix * nz + iz];
    }

    /** a'dan b'ye düz çizgi serbest mi? (robotun merkezi için; engel payları ve "kapalıya bitişik" güvenlik şeridi dahil) */
    public boolean lineFree(float ax, float az, float bx, float bz){
        final float len = (float) Math.hypot(bx - ax, bz - az);
        final int steps = Math.max(1, (int) Math.ceil(len / LOS_STEP));
        for(int i = 0; i <= steps; i++){
            float t = (float) i / steps;
            float px = ax + (bx - ax) * t, pz = az + (bz - az) * t;
            int ix = cellX(px), iz = cellZ(pz);
            if(!inGrid(ix, iz)) return false;
            int c = ix * nz + iz;
            if(blocked[c]) return false;
            if(tight[c]){
                //uçlara yakın bitişik kareler tolere edilir (robot duvara/engele yakın başlıyor ya da bitiriyor olabilir)
                float da = t * len, db = (1f - t) * len;
                if(da > LOS_ENDPOINT_SLACK && db > LOS_ENDPOINT_SLACK) return false;
            }
        }
        return true;
    }

    private float dynAt(float x, float z){
        int ix = cellX(x), iz = cellZ(z);
        return inGrid(ix, iz) ? dyn[ix * nz + iz] : 0f;
    }

    /** Düz çizgi boyunca dinamik cezanın toplamı (A*'ın karelere göre topladığı değerle kıyaslanabilir olsun diye örnek aralığına oranlı). */
    private float lineDynCost(float ax, float az, float bx, float bz){
        final float len = (float) Math.hypot(bx - ax, bz - az);
        final int steps = Math.max(1, (int) Math.ceil(len / LOS_STEP));
        final float per = (len / steps) / CELL; //örnek başına kare sayısı
        float sum = 0f;
        for(int i = 0; i <= steps; i++){
            float t = (float) i / steps;
            sum += dynAt(ax + (bx - ax) * t, az + (bz - az) * t) * per;
        }
        return sum;
    }

    /** (x, z)'ye en yakın serbest karenin merkezini out[0], out[1]'e yazar. false: yakında yok (ya da saha dışı çok uzak). */
    public boolean nearestFree(float x, float z, float[] out){
        int c = nearestFreeCell(cellX(x), cellZ(z), 30, x, z);
        if(c < 0) return false;
        out[0] = (c / nz + 0.5f) * CELL;
        out[1] = (c % nz + 0.5f) * CELL;
        return true;
    }

    /** (ix, iz) çevresinde, (px, pz) noktasına GERÇEK mesafesi en küçük serbest kare (yoksa -1). maxRing kare yarıçapına kadar. */
    private int nearestFreeCell(int ix, int iz, int maxRing, float px, float pz){
        ix = Math.max(0, Math.min(nx - 1, ix));
        iz = Math.max(0, Math.min(nz - 1, iz));
        if(!blocked[ix * nz + iz]) return ix * nz + iz;

        int best = -1;
        float bestD = INF;
        for(int r = 1; r <= maxRing; r++){
            //halka içinde bir şey bulunduysa, bir sonraki halka bundan daha yakın olamaz (en fazla 1 kare tolerans)
            if(best >= 0 && (r - 1) * CELL > bestD) break;
            for(int ax = ix - r; ax <= ix + r; ax++){
                for(int az = iz - r; az <= iz + r; az += (ax == ix - r || ax == ix + r) ? 1 : 2 * r){
                    if(!inGrid(ax, az) || blocked[ax * nz + az]) continue;
                    float cx = (ax + 0.5f) * CELL, cz = (az + 0.5f) * CELL;
                    float d = (cx - px) * (cx - px) + (cz - pz) * (cz - pz);
                    d = (float) Math.sqrt(d);
                    if(d < bestD){ bestD = d; best = ax * nz + az; }
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------ dinamik ceza

    /** Geçici engel/ceza: (x, z) merkezli radius yarıçaplı dairedeki her kareye, bir sonraki findPath için karesi başına
     *  'cost' metre ek maliyet ekler (örn. diğer robotların etrafı). Bitince clearDynamic() çağır. */
    public void addPenaltyDisc(float x, float z, float radius, float cost){
        int x0 = Math.max(0, cellX(x - radius)), x1 = Math.min(nx - 1, cellX(x + radius));
        int z0 = Math.max(0, cellZ(z - radius)), z1 = Math.min(nz - 1, cellZ(z + radius));
        final float r2 = radius * radius;
        for(int ix = x0; ix <= x1; ix++){
            for(int iz = z0; iz <= z1; iz++){
                float dx = (ix + 0.5f) * CELL - x, dz = (iz + 0.5f) * CELL - z;
                if(dx * dx + dz * dz > r2) continue;
                int c = ix * nz + iz;
                if(dyn[c] == 0f){
                    if(dynCount == dynTouched.length) dynTouched = Arrays.copyOf(dynTouched, dynCount * 2);
                    dynTouched[dynCount++] = c;
                }
                dyn[c] += cost;
            }
        }
    }

    public void clearDynamic(){
        for(int i = 0; i < dynCount; i++) dyn[dynTouched[i]] = 0f;
        dynCount = 0;
    }

    // ------------------------------------------------------------------------------------------ A*

    /**
     * (sx, sz)'den (gx, gz)'ye yol arar (A*, 8 yönlü) ve görüş hattıyla sadeleştirip (ip germe) 'out'a yazar.
     * Başlangıç ya da hedef yasak bölgedeyse en yakın serbest kareye alınır: yol, hedef serbestse tam hedefte, değilse
     * ona en yakın serbest noktada biter (örn. duvar dibindeki top için). out'un ilk noktası (sx, sz)'dir.
     *
     * @return false: yol yok (hedef bölgesine ulaşılamıyor)
     */
    public boolean findPath(float sx, float sz, float gx, float gz, Route out){
        out.clear();
        fieldValid = false;

        int sc = nearestFreeCell(cellX(sx), cellZ(sz), 30, sx, sz);
        int gc = nearestFreeCell(cellX(gx), cellZ(gz), 30, gx, gz);
        if(sc < 0 || gc < 0) return false;

        //hedef serbest kareye alındıysa son nokta o karenin merkezi, değilse tam hedef
        float endX = gx, endZ = gz;
        if(isBlocked(gx, gz)){
            endX = (gc / nz + 0.5f) * CELL;
            endZ = (gc % nz + 0.5f) * CELL;
        }

        if(sc == gc){
            out.add(sx, sz);
            if(Math.hypot(endX - sx, endZ - sz) > 0.02f) out.add(endX, endZ);
            return true;
        }
        if(!search(sc, gc)) return false;

        //geriye doğru ham yolu çıkar (hedeften başa)
        int count = 0;
        for(int c = gc; c != -1; c = parent[c]) count++;
        final float[] rx = new float[count + 2], rz = new float[count + 2];
        int k = count;
        for(int c = gc; c != -1; c = parent[c]){
            k--;
            rx[k + 1] = (c / nz + 0.5f) * CELL;
            rz[k + 1] = (c % nz + 0.5f) * CELL;
        }
        //uçları gerçek noktalarla değiştir
        rx[0] = sx; rz[0] = sz;
        rx[count + 1] = endX; rz[count + 1] = endZ;
        final int total = count + 2;

        //ip germe: her adımda görebildiğin en uzak noktaya atla. Dinamik ceza varsa kestirme, ham yolun o kısmından
        //daha çok cezalı bölgeden geçmemeli (yoksa diğer robotların etrafından dolanan yol düz çizgiye çevrilirdi)
        float[] pre = null;
        if(dynCount > 0){
            pre = new float[total];
            for(int q = 0; q < total; q++) pre[q] = ((q > 0) ? pre[q - 1] : 0f) + dynAt(rx[q], rz[q]);
        }
        out.add(rx[0], rz[0]);
        int i = 0;
        while(i < total - 1){
            int j = total - 1;
            while(j > i + 1 && !(lineFree(rx[i], rz[i], rx[j], rz[j])
                    && (pre == null || lineDynCost(rx[i], rz[i], rx[j], rz[j]) <= pre[j] - pre[i] + 0.25f))) j--;
            out.add(rx[j], rz[j]);
            i = j;
        }
        return true;
    }

    /** Mesafe haritası: (sx, sz)'den (başlangıç yasak bölgedeyse en yakın serbest kareden) her yere en kısa yol uzunluğunu
     *  (maliyetli, yani tümsek/duvar dibi biraz pahalı) hesaplar. Sonra distanceTo ile sorgula; bir sonraki findPath/distanceFrom'a kadar geçerlidir.
     *  Çok sayıda hedefi (top adayları, atış noktaları) tek robot için puanlarken A*'ı tek tek çalıştırmaktan çok ucuzdur. */
    public void distanceFrom(float sx, float sz){
        int sc = nearestFreeCell(cellX(sx), cellZ(sz), 30, sx, sz);
        Arrays.fill(field, INF);
        fieldValid = false;
        if(sc < 0) return;
        search(sc, -1);
        for(int i = 0; i < field.length; i++) if(closed[i] == serial) field[i] = gCost[i];
        fieldValid = true;
    }

    /** distanceFrom'dan sonra (x, z)'ye yol mesafesi (m). Yasak bölgedeki hedef için en yakın serbest kareye olan düz mesafe eklenir.
     *  Ulaşılamıyorsa (ya da mesafe haritası yoksa) +sonsuz. */
    public float distanceTo(float x, float z){
        if(!fieldValid) return INF;
        int c = nearestFreeCell(cellX(x), cellZ(z), 30, x, z);
        if(c < 0 || field[c] == INF) return INF;
        float cx = (c / nz + 0.5f) * CELL, cz = (c % nz + 0.5f) * CELL;
        return field[c] + (isBlocked(x, z) ? (float) Math.hypot(cx - x, cz - z) : 0f);
    }

    /** goal < 0: hedefsiz (Dijkstra, tüm erişilebilir kareler). */
    private boolean search(int start, int goal){
        serial++;
        heapSize = 0;
        seen[start] = serial;
        gCost[start] = 0f;
        parent[start] = -1;
        final int gix = (goal >= 0) ? goal / nz : 0, giz = (goal >= 0) ? goal % nz : 0;
        push(start, heuristic(start / nz, start % nz, gix, giz, goal));

        while(heapSize > 0){
            final int cur = pop();
            if(closed[cur] == serial) continue;
            closed[cur] = serial;
            if(cur == goal) return true;

            final int ix = cur / nz, iz = cur - ix * nz;
            for(int d = 0; d < 8; d++){
                final int ax = ix + DX[d], az = iz + DZ[d];
                if(ax < 0 || az < 0 || ax >= nx || az >= nz) continue;
                final int nb = ax * nz + az;
                if(blocked[nb] || closed[nb] == serial) continue;
                if(d >= 4 && (blocked[(ix + DX[d]) * nz + iz] || blocked[ix * nz + iz + DZ[d]])) continue; //köşe kesme yok

                final float ng = gCost[cur] + STEP[d] * CELL * (1f + extra[nb]) + dyn[nb];
                if(seen[nb] != serial || ng < gCost[nb]){
                    seen[nb] = serial;
                    gCost[nb] = ng;
                    parent[nb] = cur;
                    push(nb, ng + heuristic(ax, az, gix, giz, goal));
                }
            }
        }
        return goal < 0;
    }

    private float heuristic(int ix, int iz, int gix, int giz, int goal){
        if(goal < 0) return 0f;
        float dx = Math.abs(ix - gix), dz = Math.abs(iz - giz);
        return (Math.max(dx, dz) + 0.41421356f * Math.min(dx, dz)) * CELL; //sekizli mesafe: maliyetler >= yürüyüş olduğu için kabul edilebilir
    }

    // ------------------------------------------------------------------------------------------ ikili yığın (min)

    private void push(int node, float key){
        if(heapSize == heapNode.length){
            heapNode = Arrays.copyOf(heapNode, heapSize * 2);
            heapKey = Arrays.copyOf(heapKey, heapSize * 2);
        }
        int i = heapSize++;
        while(i > 0){
            int p = (i - 1) >> 1;
            if(heapKey[p] <= key) break;
            heapNode[i] = heapNode[p]; heapKey[i] = heapKey[p];
            i = p;
        }
        heapNode[i] = node; heapKey[i] = key;
    }

    private int pop(){
        final int top = heapNode[0];
        final int node = heapNode[--heapSize];
        final float key = heapKey[heapSize];
        int i = 0;
        while(true){
            int l = 2 * i + 1;
            if(l >= heapSize) break;
            int r = l + 1;
            int m = (r < heapSize && heapKey[r] < heapKey[l]) ? r : l;
            if(heapKey[m] >= key) break;
            heapNode[i] = heapNode[m]; heapKey[i] = heapKey[m];
            i = m;
        }
        if(heapSize > 0){ heapNode[i] = node; heapKey[i] = key; }
        return top;
    }
}