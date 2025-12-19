public class Bomb {
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