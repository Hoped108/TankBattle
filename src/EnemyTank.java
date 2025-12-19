public class EnemyTank extends Tank implements Runnable{
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