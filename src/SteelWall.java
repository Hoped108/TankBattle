import java.awt.*;

public class SteelWall extends Tile {
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