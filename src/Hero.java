public class Hero extends Tank{
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