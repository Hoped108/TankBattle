import java.awt.*;

public class EmptyTile extends Tile {
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