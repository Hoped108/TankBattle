import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Random;
import java.util.Vector;
import javax.swing.*;
import java.io.*;



import static javax.swing.plaf.basic.BasicGraphicsUtils.drawString;

public class Main extends JFrame{
    MyPanel mp;

    static void main(String[] args){
        new Main();
    }

    public Main(){
        mp = new MyPanel();
        Thread thread = new Thread(mp);
        thread.start();
        this.add(mp);
        this.setSize(1200,800);
        this.addKeyListener(mp);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setVisible(true);
    }
}
class MyPanel extends JPanel implements KeyListener, Runnable {
    enum GameState { RUNNING, WIN, LOSE }
    private volatile GameState gameState = GameState.RUNNING;
    Hero hero;
    private volatile boolean up, down, left, right, fire;
    private Vector<EnemyTank> enemyTanks = new Vector<>();
    Vector<Bomb> bombs = new Vector<>();
    public GameMap gameMap;
    private int waveIndex = 0;      // 当前波次（从0开始）
    private int spawnedTotal = 0;
    private final String[] MAPS = {"map1.txt", "map2.txt", "map3.txt"};
    private int mapIndex = 0;
    // 已经生成过的敌人总数


    //定义爆炸效果图片
     Image img1 ;
     Image img2 ;
     Image img3 ;
     Image img4 ;
    // 修改后的 showInfo（简洁版）
    public void showInfo(Graphics g) {
        g.setColor(Color.DARK_GRAY);
        g.fillRect(1000, 0, 200, 750);

        g.setColor(Color.WHITE);
        g.setFont(new Font("微软雅黑", Font.BOLD, 16));

        int kill = Recorder.getAllEnemyTankNum();
        int best = Recorder.getBestScore();

        int alive = enemyTanks.size();
//        int remainingTotal = GameConfig.TOTAL_ENEMY_LIMIT - kill; // 还没被击杀的总数（含未生成）

        g.drawString("累计击杀: " + kill, 1010, 60);
        g.drawString("历史最高: " + best, 1010, 90);

        g.drawString("当前波次: " + waveIndex, 1010, 140);
        g.drawString("在场敌人: " + alive, 1010, 170);
//        g.drawString("剩余总敌: " + remainingTotal, 1010, 200);

        // 英雄弹药
        g.drawString("弹药: " + hero.getAmmo() + "/" + hero.getMaxAmmo(), 1010, 260);
        if (hero.isReloading()) {
            g.drawString("装弹中...", 1010, 290);
        }
    }

    public MyPanel() {
        Recorder.loadRecord();

        img1 = Toolkit.getDefaultToolkit().getImage(MyPanel.class.getResource("0.png"));
        img2 = Toolkit.getDefaultToolkit().getImage(MyPanel.class.getResource("1.png"));
        img3 = Toolkit.getDefaultToolkit().getImage(MyPanel.class.getResource("2.png"));
        img4 = Toolkit.getDefaultToolkit().getImage(MyPanel.class.getResource("3.png"));

        loadLevel(0);
    }

    private void endGame(GameState state) {
        if (gameState != GameState.RUNNING) return; // 防止重复触发
        gameState = state;

        // 1) 停止所有敌人（让 enemy thread 自己退出）
        for (EnemyTank et : enemyTanks.toArray(new EnemyTank[0])) {
            if (et != null) et.destroy();
        }

        // 2) 停止英雄所有子弹
        synchronized (hero.bullets) {
            for (shot b : hero.bullets.toArray(new shot[0])) {
                if (b != null) b.setLive(false);
            }
        }

        // 3) 停止敌人所有子弹
        for (EnemyTank et : enemyTanks.toArray(new EnemyTank[0])) {
            if (et == null) continue;
            synchronized (et.bullets) {
                for (shot b : et.bullets.toArray(new shot[0])) {
                    if (b != null) b.setLive(false);
                }
            }
        }

        // 4) 立刻重绘，显示“结束/胜利”
        repaint();
    }

    private void spawnNextWave() {
        if (spawnedTotal >= GameConfig.TOTAL_ENEMY_LIMIT) return;

        int count = GameConfig.WAVE_COUNTS[waveIndex % GameConfig.WAVE_COUNTS.length];
        count = Math.min(count, GameConfig.TOTAL_ENEMY_LIMIT - spawnedTotal);

        waveIndex++;
        spawnWave(count);
    }

    private void spawnWave(int count) {
        int attempts = 0;
        int created = 0;
        Random rand = new Random();

        while (created < count && attempts < 200) {
            attempts++;

            // 在上方随机刷怪（避开边界）
            // ====== 出生点对齐到格子 ======
            int spawnRow = 2;                 // 0-based，第 3 行（你 txt 第3行）
            int y = spawnRow * Tile.SIZE;     // 2*25=50

            // 敌人默认向下，碰撞盒 40x60
            int tankW = 40;

            // 地图列数：1000/25 = 40
            int cols = GameConfig.GAME_WIDTH / Tile.SIZE;

            // 留出左右边界墙（col=0 和 col=cols-1 是 #），并保证坦克宽度不撞右边墙
            int minCol = 1;
            int maxCol = (GameConfig.GAME_WIDTH - Tile.SIZE - tankW) / Tile.SIZE; // 1000-25-40=935 -> 37

            int col = minCol + rand.nextInt(maxCol - minCol + 1);
            int x = col * Tile.SIZE;


            EnemyTank enemy = new EnemyTank(x, y, 2, this);
            enemy.setDirect(2);

            // 统一从配置控制（可控）
            enemy.setFireControl(
                    GameConfig.ENEMY_SHOT_INTERVAL_MS,
                    GameConfig.ENEMY_BURST_COUNT,
                    GameConfig.ENEMY_MAX_BULLETS_ALIVE
            );

            // 刷新位置不能卡墙/重叠
            if (gameMap.collidesWithTank(enemy.getBounds()) || isTankOverlapping(enemy)) {
                continue;
            }

            new Thread(enemy).start();
            enemyTanks.add(enemy);

            created++;
            spawnedTotal++;
        }
    }


    @Override
    public void paint(Graphics g) {
        super.paint(g);
        gameMap.draw(g);
        //绘制黑色背景，默认黑色
//        g.fillRect(0, 0, 1000, 750);
        if (hero.isLive()) {
            drawTank(hero.getX(), hero.getY(), g, hero.getDirect(), 0);
        }
        // 绘制所有英雄的子弹
        for (shot bullet : hero.bullets.toArray(new shot[0])) {
            if (bullet != null && bullet.Live()) {
                g.fill3DRect(bullet.getX(), bullet.getY(), 4, 4, false);
            }
        }


        //bombs集合中有对象就画出来
        for(Bomb bomb : bombs.toArray(new Bomb[0])){
            if (bomb == null) continue;

            // 根据 life 值选择图片
            if(bomb.life > 9){
                g.drawImage(img1, bomb.x, bomb.y, 60, 50, this);
            } else if(bomb.life > 6){
                g.drawImage(img2, bomb.x, bomb.y, 60, 60, this);
            } else if(bomb.life > 3){
                g.drawImage(img3, bomb.x, bomb.y, 60, 57, this);
            } else{
                g.drawImage(img4, bomb.x, bomb.y, 49, 60, this);
            }
        }
        //绘制敌方坦克
        for (EnemyTank enemyTank : enemyTanks.toArray(new EnemyTank[0])) {
            if (enemyTank != null && enemyTank.Live()) {
                drawTank(enemyTank.getX(), enemyTank.getY(), g, enemyTank.getDirect(), 1);
                for (shot bullet : enemyTank.bullets.toArray(new shot[0])) {
                    if (bullet != null && bullet.Live()) {
                        g.fill3DRect(bullet.getX(), bullet.getY(), 4, 4, false);
                    }
                }
            }
        }

        showInfo(g);
        // 结算
        if (gameState == GameState.WIN || gameState == GameState.LOSE) {
            g.setColor(Color.RED);
            g.setFont(new Font("宋体", Font.BOLD, 60));
            String msg = (gameState == GameState.WIN) ? "游戏胜利" : "游戏结束";

            g.drawString(msg, 250, 380);

            g.setFont(new Font("微软雅黑", Font.BOLD, 24));
            g.drawString("按 N 进入下一关", 330, 410);
        }


    }

    public void drawTank(int x, int y, Graphics g, int direct, int type) {

        switch (type) {
            case 0://我们的tank
                g.setColor(Color.YELLOW);
                break;
            case 1://敌人的tank
                g.setColor(Color.CYAN);
                break;
        }

        //根据坦克方向来绘制坦克,0-上，1-右，2-下，3-左
        switch (direct) {
            case 0: //表示向上
                g.fill3DRect(x, y, 10, 60, false);//false是表示图层上或者下
                g.fill3DRect(x + 30, y, 10, 60, false);
                g.fill3DRect(x + 10, y + 10, 20, 40, false);
                g.fillOval(x + 10, y + 20, 20, 20);
                g.drawLine(x + 20, y, x + 20, y + 30);
                break;
            case 1: // 向右（炮口朝右）
                g.fill3DRect(x, y, 60, 10, false); // 左右履带（横向拉长）
                g.fill3DRect(x, y + 30, 60, 10, false);
                g.fill3DRect(x + 10, y + 10, 40, 20, false); // 车身（横向）
                g.fillOval(x + 20, y + 10, 20, 20); // 炮塔（位置不变）
                g.drawLine(x + 30, y + 20, x + 60, y + 20); // 炮口朝右
                break;

            case 2: // 向下（炮口朝下）
                g.fill3DRect(x, y, 10, 60, false); // 履带位置和向上一致（纵向）
                g.fill3DRect(x + 30, y, 10, 60, false);
                g.fill3DRect(x + 10, y + 10, 20, 40, false); // 车身不变
                g.fillOval(x + 10, y + 20, 20, 20); // 炮塔不变
                g.drawLine(x + 20, y + 30, x + 20, y + 60); // 炮口朝下
                break;

            case 3: // 向左（炮口朝左）
                g.fill3DRect(x, y, 60, 10, false); // 左右履带（横向）
                g.fill3DRect(x, y + 30, 60, 10, false);
                g.fill3DRect(x + 10, y + 10, 40, 20, false); // 车身（横向）
                g.fillOval(x + 20, y + 10, 20, 20); // 炮塔不变
                g.drawLine(x + 30, y + 20, x, y + 20); // 炮口朝左
                break;
            default:
                System.out.println("暂时没有处理");
        }
    }

    private void loadLevel(int nextIndex) {
        mapIndex = (nextIndex % MAPS.length + MAPS.length) % MAPS.length;

        up = down = left = right = fire = false;

        // 先停敌人线程
        for (EnemyTank et : enemyTanks.toArray(new EnemyTank[0])) {
            if (et != null) et.destroy();
        }
        enemyTanks.clear();

        // hero 可能为空，做保护
        if (hero != null) {
            synchronized (hero.bullets) { hero.bullets.clear(); }
        }
        bombs.clear();

        waveIndex = 0;
        spawnedTotal = 0;

        gameMap = new GameMap(1000, 750, MAPS[mapIndex]);

        hero = new Hero(900, 600, 0);   // 你后面最好换成 spawnHeroSafely()

        spawnNextWave();

        gameState = GameState.RUNNING;
        repaint();
    }


    public boolean isTankOverlapping(Tank movingTank) {
        Rectangle selfRect = movingTank.getBounds();

        // === 新增：禁止坦克移出屏幕 ===
        if (selfRect.x < 0 || selfRect.y < 0 ||
                selfRect.x + selfRect.width > this.getWidth() ||
                selfRect.y + selfRect.height > this.getHeight()) {
            return true; // 视为“碰撞”，禁止移动
        }

        // 检查是否与 hero 重叠（如果是敌人）
        if (movingTank != hero && hero != null && selfRect.intersects(hero.getBounds())) {
            if (selfRect.intersects(hero.getBounds())) {
                return true;
            }
        }

        // 检查是否与其他敌人重叠
        EnemyTank[] ets = enemyTanks.toArray(new EnemyTank[0]);
        for (EnemyTank et : ets) {
            if (et == movingTank) continue;
            if (et != null && et.Live() && selfRect.intersects(et.getBounds())) {
                return true;
            }
        }


        return false;
    }
    private void tryMoveHero(int dir) {
        int oldX = hero.getX();
        int oldY = hero.getY();

        hero.setDirect(dir);
        switch (dir) {
            case 0: hero.moveUp(); break;
            case 1: hero.moveRight(); break;
            case 2: hero.moveDown(); break;
            case 3: hero.moveLeft(); break;
        }

        if (gameMap.collidesWithTank(hero.getBounds()) || isTankOverlapping(hero)) {
            hero.setX(oldX);
            hero.setY(oldY);
        }
    }

    public void hitTank(shot s, EnemyTank enemyTank) {
        //子弹击中时就创建bomb对象
        switch (enemyTank.getDirect()) {
            case 0:
            case 2:
                if (s.getX() > enemyTank.getX() && s.getX() < enemyTank.getX() + 40
                        && s.getY() > enemyTank.getY() && s.getY() < enemyTank.getY() + 60) {
                    s.setLive(false);
                    enemyTank.destroy();
                     Bomb bomb = new Bomb(enemyTank.getX(),enemyTank.getY());
                     bombs.add(bomb);
                    Recorder.addAllEnemyTankNum();
                }
                break;
            case 1:
            case 3:
                if (s.getX() > enemyTank.getX() && s.getX() < enemyTank.getX() + 60
                        && s.getY() > enemyTank.getY() && s.getY() < enemyTank.getY() + 40) {
                    s.setLive(false);
                    enemyTank.destroy();
                     Bomb bomb = new Bomb(enemyTank.getX(),enemyTank.getY());
                     bombs.add(bomb);
                    Recorder.addAllEnemyTankNum();
                }
                break;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {

        // ✅ 结算界面：按 N 下一关（WIN/LOSE都可以按）
        if ((gameState == GameState.WIN || gameState == GameState.LOSE)
                && e.getKeyCode() == KeyEvent.VK_N) {
            loadLevel(mapIndex + 1);   // map3 -> map1（取模）
            return;
        }

        // ✅ 只有 RUNNING 才响应移动/射击
        if (gameState != GameState.RUNNING) return;
        if (!hero.isLive()) return;

        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:    up = true; break;
            case KeyEvent.VK_DOWN:  down = true; break;
            case KeyEvent.VK_LEFT:  left = true; break;
            case KeyEvent.VK_RIGHT: right = true; break;
            case KeyEvent.VK_SPACE: fire = true; break;
        }
    }



    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:    up = false; break;
            case KeyEvent.VK_DOWN:  down = false; break;
            case KeyEvent.VK_LEFT:  left = false; break;
            case KeyEvent.VK_RIGHT: right = false; break;
            case KeyEvent.VK_SPACE:     fire = false; break;
        }
    }


    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(33); // 主循环每 100ms 一次（约 10 FPS）
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            hero.updateReload();

            if (gameState != GameState.RUNNING) {
                repaint();
                continue;
            }

            // ====== 每帧处理移动（支持按住移动）======
            // 优先级：上 > 右 > 下 > 左（你也可以自己改）
            if (up)       tryMoveHero(0);
            else if (right) tryMoveHero(1);
            else if (down)  tryMoveHero(2);
            else if (left)  tryMoveHero(3);

            // ====== 每帧处理射击（支持按住J边走边打）======
            if (fire) hero.tryShoot();

            // ========== 新增：检测敌人子弹是否击中英雄 ==========
            if (hero.isLive()) {
                EnemyTank[] tanksSnap = enemyTanks.toArray(new EnemyTank[0]);
                for (EnemyTank tank : tanksSnap) {
                    shot[] bs = tank.bullets.toArray(new shot[0]);
                    for (shot bullet : bs) {
                        if (bullet != null && bullet.Live() && bullet.hitHero(hero)) {
                            bullet.setLive(false);
                            hero.setLive(false);
                            bombs.add(new Bomb(hero.getX(), hero.getY()));
                            endGame(GameState.LOSE);
                        }
                    }
                }
            }


            // ========== 子弹与敌人/墙壁碰撞检测 ==========
            shot[] heroBulletsSnap = hero.bullets.toArray(new shot[0]);
            for (shot bullet : heroBulletsSnap) {
                if (bullet != null && bullet.Live()) {
                    EnemyTank[] tanksSnap = enemyTanks.toArray(new EnemyTank[0]);
                    for (EnemyTank enemyTank : tanksSnap) {
                        hitTank(bullet, enemyTank);
                    }
                    if (gameMap.collidesWithBullet(bullet.getX(), bullet.getY())) {
                        bullet.setLive(false);
                        gameMap.tryDestroy(bullet.getX(), bullet.getY());
                    }
                }
            }

            //========== 敌人击碎墙壁检测 ==========
            EnemyTank[] tanksSnap2 = enemyTanks.toArray(new EnemyTank[0]);
            for (EnemyTank et : tanksSnap2) {
                shot[] bs = et.bullets.toArray(new shot[0]);
                for (shot bullet : bs) {
                    if (bullet != null && bullet.Live()) {
                        if (gameMap.collidesWithBullet(bullet.getX(), bullet.getY())) {
                            bullet.setLive(false);
                            gameMap.tryDestroy(bullet.getX(), bullet.getY());
                        }
                    }
                }
            }


            // ========== 关键：更新所有爆炸效果 ==========
            for (int i = bombs.size() - 1; i >= 0; i--) {
                Bomb bomb = bombs.get(i);
                bomb.lifeDown(); // ← 自动根据时间更新 life
                if (!bomb.isLive()) {
                    bombs.remove(i); // 移除已结束的爆炸
                }
            }

            // ========== 清理死亡对象 ==========
            for (int i = enemyTanks.size() - 1; i >= 0; i--) {
                if (!enemyTanks.get(i).Live()) enemyTanks.remove(i);
            }

            // ===== 波次刷新：这一波清空了就刷下一波，直到总数到上限 =====
            if (hero.isLive() && enemyTanks.isEmpty() && spawnedTotal < GameConfig.TOTAL_ENEMY_LIMIT) {
                spawnNextWave();
            }

            // ========== 清理子弹对象 ==========
            synchronized (hero.bullets) {
                for (int i = hero.bullets.size() - 1; i >= 0; i--) {
                    if (!hero.bullets.get(i).Live()) hero.bullets.remove(i);
                }
            }

            // ========== 胜利判断 ==========
            if (hero.isLive()
                    && spawnedTotal >= GameConfig.TOTAL_ENEMY_LIMIT
                    && enemyTanks.isEmpty()) {
                endGame(GameState.WIN);
            }




            // ========== 重绘 ==========
            this.repaint();
        }
    }
}
class GameConfig {
    // 游戏区域（地图）大小
    public static final int GAME_WIDTH = 1000;
    public static final int GAME_HEIGHT = 750;

    // ===== 英雄：弹夹 + 装弹冷却 =====
    public static int HERO_MAX_AMMO = 6;            // 弹夹容量（可控）
    public static int HERO_RELOAD_MS = 1500;        // 打空后装满弹药冷却时间（可控）
    public static int HERO_SHOT_INTERVAL_MS = 120;  // 防止按J疯狂刷（可控）

    // ===== 敌人：射击控制 =====
    public static int ENEMY_SHOT_INTERVAL_MS = 1800;   // 敌人射击间隔（可控）
    public static int ENEMY_BURST_COUNT = 1;           // 每次射击发几颗（可控）
    public static int ENEMY_MAX_BULLETS_ALIVE = 3;     // 敌人同时在场子弹上限（可控）

    // ===== 波次：每波多少 + 总共多少 =====
    public static int TOTAL_ENEMY_LIMIT = 7;          // 总敌人数量（可控）
    public static int[] WAVE_COUNTS = {2, 2, 1, 2};    // 每波敌人数量（可控）

    //===== 速度 =====
    public static  int SHOT_SPEED = 25; //子弹类型的速度
    public static int TANK_SPEED = 5;

    //===== 像素大小 =====
    public static int PIECE = 25;
}

class Recorder{
    private static int allEnemyTankNum = 0;
    private static int bestScore = 0;

    // 最高的分
    private static final String recordFile = "record.txt";

    public static void loadRecord() {
        File f = new File(recordFile);
        if (!f.exists()) {
            bestScore = 0;
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line = br.readLine();
            if (line != null && !line.isEmpty()) {
                bestScore = Integer.parseInt(line.trim());
            }
        } catch (Exception e) {
            bestScore = 0;
        }
    }

    private static void saveRecord() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(recordFile))) {
            bw.write(String.valueOf(bestScore));
        } catch (Exception ignored) {}
    }

    public static int getAllEnemyTankNum(){ return allEnemyTankNum; }
    public static int getBestScore(){ return bestScore; }

    public static void setAllEnemyTankNum(int n){ allEnemyTankNum = n; }

    public static void addAllEnemyTankNum(){
        allEnemyTankNum++;
        if (allEnemyTankNum > bestScore) {
            bestScore = allEnemyTankNum;
            saveRecord();
        }
    }
}

class shot implements Runnable {
    private int x;
    private int y;
    private final int speed = GameConfig.SHOT_SPEED;
    private final int direct;
    private volatile boolean isLive;//判断子弹是否存活

    public shot(int x, int y, int direct) {
        this.x = x;
        this.y = y;
        this.direct = direct;
        this.isLive = true;
    }
    public boolean hitHero(Hero hero){
        if (!hero.isLive() || !this.isLive) {
            return false;
        }

        // 子弹矩形（4x4）
        Rectangle shotRect = new Rectangle(this.x, this.y, 4, 4);
        // 英雄矩形（根据方向）
        Rectangle heroRect = hero.getBounds();

        return shotRect.intersects(heroRect);
    }
    public int getX(){
        return x;
    }
    public int getY(){
        return y;
    }
    public boolean Live(){
        return isLive;
    }
    public void setLive(boolean isLive){
        this.isLive = isLive;
    }
    @Override
    public void run() {
        while (isLive) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                isLive = false;
                break;
            }

            switch (direct) {
                case 0://上
                    y -= speed;
                    break;
                case 1://右
                    x += speed;
                    break;
                case 2://下
                    y += speed;
                    break;
                case 3://左
                    x -= speed;
                    break;
            }
//            System.out.println(x+" "+y);
            //一定要在子弹为false之后退出线程，不然会导致内存溢出
            // 子弹完全飞出屏幕就死亡
            if (x < -10 || x > 1010 || y < -10 || y > 760) {
                isLive = false;
                break;
            }
        }
    }
}
class Tank {
    protected int x;
    protected int y;
    protected int direct;
    protected final int speed = GameConfig.TANK_SPEED;
    public Vector<shot> bullets = new Vector<>();

    Tank(int x,int y,int direct){
        this.x = x;
        this.direct = direct;
        this.y = y;
    }

    public Rectangle getBounds() {
        if (direct == 0 || direct == 2) {
            // 上/下：宽40，高60
            return new Rectangle(x, y, 40, 60);
        } else {
            // 左/右：宽60，高40
            return new Rectangle(x, y, 60, 40);
        }
    }
    public int getX(){
        return x;
    }
    public int getY(){
        return y;
    }
    public int getDirect(){
        return direct;
    }

    public void setX(int x){ this.x = x; }
    public void setY(int y){ this.y = y; }

    public void moveUp(){
        y -= speed;
    }
    public void moveLeft(){
        x -= speed;
    }
    public void moveDown(){
        y += speed;
    }
    public void moveRight(){
        x += speed;
    }

    public void setDirect(int direct){
        this.direct = direct;
    }
}

class Hero extends Tank{
    private boolean isLive = true;

    // ===== 弹夹/装弹冷却 =====
    private int maxAmmo = GameConfig.HERO_MAX_AMMO;
    private int ammo = maxAmmo;

    private boolean reloading = false;
    private long reloadStartTime = 0;

    private long lastShotTime = 0;

    public Hero(int x,int y,int direct){
        super(x,y,direct);
    }

    public boolean isLive() { return isLive; }
    public void setLive(boolean live) { this.isLive = live; }

    public int getAmmo() { return ammo; }
    public int getMaxAmmo() { return maxAmmo; }
    public boolean isReloading() { return reloading; }

    // 主循环里也会调用，保证不按键也能装弹结束
    public void updateReload() {
        if (!reloading) return;
        long now = System.currentTimeMillis();
        if (now - reloadStartTime >= GameConfig.HERO_RELOAD_MS) {
            ammo = maxAmmo;      // 冷却结束，直接满弹
            reloading = false;
        }
    }

    private void startReload() {
        reloading = true;
        reloadStartTime = System.currentTimeMillis();
    }

    // 统一入口：按space调用这个
    public void tryShoot() {
        if (!isLive) return;

        updateReload();
        long now = System.currentTimeMillis();

        // 射击间隔限制（防抖）
        if (now - lastShotTime < GameConfig.HERO_SHOT_INTERVAL_MS) return;

        // 装弹中不能射击
        if (reloading) return;

        // 没子弹 -> 立刻进入冷却装弹（冷却结束才满弹）
        if (ammo <= 0) {
            startReload();
            return;
        }

        // 真正发射子弹
        shot newBullet;
        switch(getDirect()) {
            case 0: newBullet = new shot(getX() + 20, getY(), 0); break;
            case 1: newBullet = new shot(getX() + 60, getY() + 20, 1); break;
            case 2: newBullet = new shot(getX() + 20, getY() + 60, 2); break;
            case 3: newBullet = new shot(getX(), getY() + 20, 3); break;
            default: return;
        }
        synchronized (bullets) {
            bullets.add(newBullet);
        }
        new Thread(newBullet).start();


        ammo--;
        lastShotTime = now;

        // 打空后开始冷却装弹
        if (ammo == 0) {
            startReload();
        }
    }
}

class EnemyTank extends Tank implements Runnable{
    private volatile boolean isLive = true;

    // ===== 射击控制（可控） =====
    private long lastShotTime = 0;
    private int shotInterval = GameConfig.ENEMY_SHOT_INTERVAL_MS;
    private int burstCount = GameConfig.ENEMY_BURST_COUNT;
    private int maxBulletsAlive = GameConfig.ENEMY_MAX_BULLETS_ALIVE;

    private MyPanel mp;

    public EnemyTank(int x, int y, int direct, MyPanel mp) {
        super(x, y, direct);
        this.mp = mp;
    }

    public void setFireControl(int shotIntervalMs, int burstCount, int maxBulletsAlive) {
        this.shotInterval = shotIntervalMs;
        this.burstCount = burstCount;
        this.maxBulletsAlive = maxBulletsAlive;
    }

    public void destroy(){ this.isLive = false; }
    public boolean Live(){ return isLive; }

    private void tryShoot() {
        long now = System.currentTimeMillis();
        if (now - lastShotTime < shotInterval) return;

        synchronized (bullets) {
            // 手动清理死亡子弹（不要 removeIf）
            for (int i = bullets.size() - 1; i >= 0; i--) {
                if (!bullets.get(i).Live()) bullets.remove(i);
            }

            if (bullets.size() >= maxBulletsAlive) return;

            for (int i = 0; i < burstCount; i++) {
                if (bullets.size() >= maxBulletsAlive) break;

                shot s;
                switch (getDirect()) {
                    case 0: s = new shot(getX() + 20, getY(), 0); break;
                    case 1: s = new shot(getX() + 60, getY() + 20, 1); break;
                    case 2: s = new shot(getX() + 20, getY() + 60, 2); break;
                    case 3: s = new shot(getX(), getY() + 20, 3); break;
                    default: continue;
                }
                bullets.add(s);
                new Thread(s).start();
            }

            lastShotTime = now;
        }
    }


    @Override
    public void run() {
        while (isLive) {
            // 每一小段移动中也尝试射击（这样不会“停住才开火”）
            for (int i = 0; i < 30 && isLive; i++) {
                tryShoot();

                int oldX = getX();
                int oldY = getY();

                int futureX = oldX, futureY = oldY;
                switch (getDirect()) {
                    case 0: futureY -= speed; break;
                    case 1: futureX += speed; break;
                    case 2: futureY += speed; break;
                    case 3: futureX -= speed; break;
                }

                x = futureX; y = futureY;
                boolean hitWall = mp.gameMap.collidesWithTank(getBounds());
                boolean overlap = mp.isTankOverlapping(this);
                x = oldX; y = oldY;

                if (!hitWall && !overlap) {
                    switch (getDirect()) {
                        case 0: moveUp(); break;
                        case 1: moveRight(); break;
                        case 2: moveDown(); break;
                        case 3: moveLeft(); break;
                    }
                }

                try { Thread.sleep(50); }
                catch (InterruptedException e) { isLive = false; break; }
            }

            setDirect((int)(Math.random() * 4));
        }
    }
}

class Bomb {
    public int x, y;
    public int life; // 👈 保留这个字段！外部代码依赖它

    private long startTime;
    private static final long DURATION = 500; // 总持续时间 500ms
    private static final int MAX_LIFE = 12;   // 对应你原来的 life 初始值

    public Bomb(int x, int y) {
        this.x = x;
        this.y = y;
        this.startTime = System.currentTimeMillis();
        this.life = MAX_LIFE; // 初始化为 10，和你原来一致
    }

    // 每帧调用一次，自动更新 life 值（基于真实时间）
    public void lifeDown() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= DURATION) {
            life = 0; // 动画结束
        } else {
            // 将时间映射回 life 值：500ms → life 从 10 降到 1
            double ratio = (double) elapsed / DURATION; // 0.0 ~ 1.0
            life = (int) (MAX_LIFE * (1.0 - ratio));
            if (life < 1) life = 1; // 至少为 1，直到最后设为 0
        }
    }

    // 供主循环判断是否移除
    public boolean isLive() {
        return life > 0;
    }
}
abstract class Tile {
    protected int x, y;
    protected static final int SIZE = GameConfig.PIECE; // 每个格子 40x40 像素

    public Tile(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // 是否阻挡坦克
    public abstract boolean blocksTank();

    // 是否阻挡子弹
    public abstract boolean blocksBullet();

    // 是否可被摧毁（比如砖墙）
    public abstract boolean isDestructible();

    // 绘制自己
    public abstract void draw(Graphics g);

    // 获取矩形区域（用于碰撞检测）
    public Rectangle getBounds() {
        return new Rectangle(x, y, SIZE, SIZE);
    }

    // 销毁（仅对可摧毁的生效）
    public void destroy() {
        // 默认什么都不做，子类可重写
    }
}
class EmptyTile extends Tile {
    public EmptyTile(int x, int y) {
        super(x, y);
    }

    @Override
    public boolean blocksTank() { return false; }
    @Override
    public boolean blocksBullet() { return false; }
    @Override
    public boolean isDestructible() { return false; }

    @Override
    public void draw(Graphics g) {
        // 可选：绘制浅灰色背景表示空地
        g.setColor(new Color(50, 50, 50));
        g.fillRect(x, y, SIZE, SIZE);
    }
}
class BrickWall extends Tile {
    private boolean destroyed = false;

    public BrickWall(int x, int y) {
        super(x, y);
    }

    @Override
    public boolean blocksTank() { return !destroyed; }
    @Override
    public boolean blocksBullet() { return !destroyed; }
    @Override
    public boolean isDestructible() { return true; }

    @Override
    public void draw(Graphics g) {
        if (!destroyed) {
            g.setColor(new Color(180, 80, 60)); // 砖红色
            g.fillRect(x, y, SIZE, SIZE);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, SIZE, SIZE);
        }
    }

    @Override
    public void destroy() {
        this.destroyed = true;

    }
}
class SteelWall extends Tile {
    public SteelWall(int x, int y) {
        super(x, y);
    }

    @Override
    public boolean blocksTank() { return true; }
    @Override
    public boolean blocksBullet() { return true; }
    @Override
    public boolean isDestructible() { return false; }

    @Override
    public void draw(Graphics g) {
        g.setColor(new Color(100, 100, 100)); // 银灰色
        g.fillRect(x, y, SIZE, SIZE);
        g.setColor(Color.DARK_GRAY);
        g.drawRect(x, y, SIZE, SIZE);
    }
}
class GameMap {
    private static final int TILE_SIZE = Tile.SIZE;
    private final int rows, cols;
    private final Tile[][] tiles;

    // ✅ 新增：从txt加载地图
    public GameMap(int width, int height, String mapTxtName) {
        this.cols = width / TILE_SIZE;
        this.rows = height / TILE_SIZE;
        this.tiles = new Tile[rows][cols];

        loadFromTxt(mapTxtName);

        // 可选：你想强制边界钢墙就打开
        // forceBorderSteel();
    }

    // 兼容你原来的：默认全空地 + 边界钢墙
    public GameMap(int width, int height) {
        this.cols = width / TILE_SIZE;
        this.rows = height / TILE_SIZE;
        this.tiles = new Tile[rows][cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                tiles[r][c] = new EmptyTile(c * TILE_SIZE, r * TILE_SIZE);
            }
        }
        forceBorderSteel();
    }

    private void loadFromTxt(String mapTxtName) {
        // 读取资源文件（和你 load png 一样的思路）
        InputStream in = GameMap.class.getResourceAsStream(mapTxtName);
        if (in == null) {
            // 找不到文件就退化为全空地
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    tiles[r][c] = new EmptyTile(c * TILE_SIZE, r * TILE_SIZE);
                }
            }
            return;
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {

            for (int r = 0; r < rows; r++) {
                String line = br.readLine();
                if (line == null) line = ""; // 行不够就当空行

                for (int c = 0; c < cols; c++) {
                    char ch = (c < line.length()) ? line.charAt(c) : ' '; // 列不够补空格

                    int px = c * TILE_SIZE;
                    int py = r * TILE_SIZE;

                    switch (ch) {
                        case '#': tiles[r][c] = new SteelWall(px, py); break;
                        case '*': tiles[r][c] = new BrickWall(px, py); break;
                        case '-': tiles[r][c] = new EmptyTile(px, py); break;
                        default : tiles[r][c] = new EmptyTile(px, py); break; // 其他字符也当空地
                    }

                }
            }
        } catch (IOException e) {
            // 读失败 -> 全空地
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    tiles[r][c] = new EmptyTile(c * TILE_SIZE, r * TILE_SIZE);
                }
            }
        }
    }

    private void forceBorderSteel() {
        for (int c = 0; c < cols; c++) {
            tiles[0][c] = new SteelWall(c * TILE_SIZE, 0);
            tiles[rows - 1][c] = new SteelWall(c * TILE_SIZE, (rows - 1) * TILE_SIZE);
        }
        for (int r = 0; r < rows; r++) {
            tiles[r][0] = new SteelWall(0, r * TILE_SIZE);
            tiles[r][cols - 1] = new SteelWall((cols - 1) * TILE_SIZE, r * TILE_SIZE);
        }
    }

    public Tile getTileAt(int x, int y) {
        int col = x / TILE_SIZE;
        int row = y / TILE_SIZE;
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            return new SteelWall(x - x % TILE_SIZE, y - y % TILE_SIZE);
        }
        return tiles[row][col];
    }

    public boolean collidesWithTank(Rectangle rect) {
        int[] xs = {rect.x, rect.x + rect.width - 1};
        int[] ys = {rect.y, rect.y + rect.height - 1};
        for (int x : xs) {
            for (int y : ys) {
                if (getTileAt(x, y).blocksTank()) return true;
            }
        }
        return false;
    }

    public boolean collidesWithBullet(int x, int y) {
        return getTileAt(x, y).blocksBullet();
    }

    public void tryDestroy(int x, int y) {
        int col = x / TILE_SIZE;
        int row = y / TILE_SIZE;
        if (row < 0 || row >= rows || col < 0 || col >= cols) return;

        Tile tile = tiles[row][col];
        if (tile.isDestructible()) {
            tiles[row][col] = new EmptyTile(col * TILE_SIZE, row * TILE_SIZE);
        }
    }

    public void draw(Graphics g) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                tiles[r][c].draw(g);
            }
        }
    }
}