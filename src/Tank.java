import java.awt.*;
import java.util.Vector;

public class Tank {
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