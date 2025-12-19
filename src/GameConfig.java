public class GameConfig {
    // 游戏区域（地图）大小
    public static final int GAME_WIDTH = 1000;
    public static final int GAME_HEIGHT = 750;

    // ===== 英雄：弹夹 + 装弹冷却 =====
    public static int HERO_MAX_AMMO = 6;            // 弹夹容量（可控）
    public static int HERO_RELOAD_MS = 1500;        // 打空后装满弹药冷却时间（可控）
    public static int HERO_SHOT_INTERVAL_MS = 120;  // 防止按J疯狂刷（可控）

    // ===== 敌人：射击控制 =====
    public static int ENEMY_SHOT_INTERVAL_MS = 1800;   // 敌人射击间隔（可控）
    public static int ENEMY_BURST_COUNT = 1;           // 每次射击发几颗（可控）
    public static int ENEMY_MAX_BULLETS_ALIVE = 3;     // 敌人同时在场子弹上限（可控）

    // ===== 波次：每波多少 + 总共多少 =====
    public static int TOTAL_ENEMY_LIMIT = 7;          // 总敌人数量（可控）
    public static int[] WAVE_COUNTS = {2, 2, 1, 2};    // 每波敌人数量（可控）

    //===== 速度 =====
    public static  int SHOT_SPEED = 25; //子弹类型的速度
    public static int TANK_SPEED = 5;

    //===== 像素大小 =====
    public static int PIECE = 25;
}