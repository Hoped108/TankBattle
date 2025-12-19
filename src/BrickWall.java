import java.awt.*;

public class BrickWall extends Tile {
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