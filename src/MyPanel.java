import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Random;
import java.util.Vector;

public class MyPanel extends JPanel implements KeyListener, Runnable {
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

        img1 = Toolkit.getDefaultToolkit().getImage(MyPanel.class.getResource("/0.png"));
        img2 = Toolkit.getDefaultToolkit().getImage(MyPanel.class.getResource("/1.png"));
        img3 = Toolkit.getDefaultToolkit().getImage(MyPanel.class.getResource("/2.png"));
        img4 = Toolkit.getDefaultToolkit().getImage(MyPanel.class.getResource("/3.png"));

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
