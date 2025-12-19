import java.awt.*;

public class shot implements Runnable {
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