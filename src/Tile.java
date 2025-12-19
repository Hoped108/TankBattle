import java.awt.*;

public abstract class Tile {
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