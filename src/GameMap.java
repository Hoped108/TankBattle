import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class GameMap {
    private static final int TILE_SIZE = Tile.SIZE;
    private final int rows, cols;
    private final Tile[][] tiles;

    // ✅ 新增：从txt加载地图
    public GameMap(int width, int height, String mapTxtName) {
        this.cols = width / TILE_SIZE;
        this.rows = height / TILE_SIZE;
        this.tiles = new Tile[rows][cols];

        loadFromTxt(mapTxtName);

        forceBorderSteel();
    }

    // 默认全空地 + 边界钢墙
    public GameMap(int width, int height) {
        this.cols = width / TILE_SIZE;
        this.rows = height / TILE_SIZE;
        this.tiles = new Tile[rows][cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                tiles[r][c] = new EmptyTile(c * TILE_SIZE, r * TILE_SIZE);
            }
        }
        forceBorderSteel();
    }

    private void loadFromTxt(String mapTxtName) {
        // 读取资源文件
        InputStream in = GameMap.class.getResourceAsStream("/" + mapTxtName);
        if (in == null) {
            // 找不到文件就退化为全空地
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    tiles[r][c] = new EmptyTile(c * TILE_SIZE, r * TILE_SIZE);
                }
            }
            return;
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {

            for (int r = 0; r < rows; r++) {
                String line = br.readLine();
                if (line == null) line = ""; // 行不够就当空行

                for (int c = 0; c < cols; c++) {
                    char ch = (c < line.length()) ? line.charAt(c) : ' '; // 列不够补空格

                    int px = c * TILE_SIZE;
                    int py = r * TILE_SIZE;

                    switch (ch) {
                        case '#': tiles[r][c] = new SteelWall(px, py); break;
                        case '*': tiles[r][c] = new BrickWall(px, py); break;
                        case '-': tiles[r][c] = new EmptyTile(px, py); break;
                        default : tiles[r][c] = new EmptyTile(px, py); break; // 其他字符也当空地
                    }

                }
            }
        } catch (IOException e) {
            // 读失败 -> 全空地
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    tiles[r][c] = new EmptyTile(c * TILE_SIZE, r * TILE_SIZE);
                }
            }
        }
    }

    private void forceBorderSteel() {
        for (int c = 0; c < cols; c++) {
            tiles[0][c] = new SteelWall(c * TILE_SIZE, 0);
            tiles[rows - 1][c] = new SteelWall(c * TILE_SIZE, (rows - 1) * TILE_SIZE);
        }
        for (int r = 0; r < rows; r++) {
            tiles[r][0] = new SteelWall(0, r * TILE_SIZE);
            tiles[r][cols - 1] = new SteelWall((cols - 1) * TILE_SIZE, r * TILE_SIZE);
        }
    }

    public Tile getTileAt(int x, int y) {
        int col = x / TILE_SIZE;
        int row = y / TILE_SIZE;
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            return new SteelWall(x - x % TILE_SIZE, y - y % TILE_SIZE);
        }
        return tiles[row][col];
    }

    public boolean collidesWithTank(Rectangle rect) {
        int[] xs = {rect.x, rect.x + rect.width - 1};
        int[] ys = {rect.y, rect.y + rect.height - 1};
        for (int x : xs) {
            for (int y : ys) {
                if (getTileAt(x, y).blocksTank()) return true;
            }
        }
        return false;
    }

    public boolean collidesWithBullet(int x, int y) {
        return getTileAt(x, y).blocksBullet();
    }

    public void tryDestroy(int x, int y) {
        int col = x / TILE_SIZE;
        int row = y / TILE_SIZE;
        if (row < 0 || row >= rows || col < 0 || col >= cols) return;

        Tile tile = tiles[row][col];
        if (tile.isDestructible()) {
            tiles[row][col] = new EmptyTile(col * TILE_SIZE, row * TILE_SIZE);
        }
    }

    public void draw(Graphics g) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                tiles[r][c].draw(g);
            }
        }
    }
}