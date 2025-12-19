# TankBattle（Java Swing 坦克大战）

这是一个基于 **Java Swing + 多线程** 的坦克大战小项目：  
- 方向键移动  
- 空格射击（带弹夹与装弹冷却）  
- 敌人按配置间隔/连发数开火  
- 砖墙可被子弹摧毁，钢墙不可摧毁  
- 三张地图 map1/map2/map3 循环闯关（胜利/失败后按 N 进入下一关）

---

## 运行环境

- JDK 8+（建议 17/21）
- IntelliJ IDEA（或任意能编译运行 Java 的工具）

---

## 目录与资源

当前的结构大致是：

- `src/Main.java`（包含所有类）
- `src/0.png 1.png 2.png 3.png ...`（爆炸动画）
- `src/map1.txt map2.txt map3.txt`（地图）

> 地图和图片属于“资源文件”。打包成 JAR 时要确保它们也被打进 JAR。

---

## 操作说明

- **↑ ↓ ← →**：移动
- **Space（空格）**：射击（支持边走边打）
- **N**：结算界面（胜利/失败）进入下一关；`map3 -> map1` 循环

---

## 地图格式（map*.txt）

- `#`：SteelWall（钢墙，不可摧毁）
- `*`：BrickWall（砖墙，可摧毁）
- `-` 或 空格：EmptyTile（空地）

你的地图大小示例为 **40 列 × 30 行**（因为游戏宽 1000，高 750，Tile.SIZE=25）：

- 列数 = `1000 / 25 = 40`
- 行数 = `750 / 25 = 30`

---

## 资源读取（非常重要）

### ✅ 推荐：从 classpath 读取地图/图片（打成 JAR 也能用）

地图读取建议使用：

```java
InputStream in = GameMap.class.getResourceAsStream("/" + mapTxtName);
```

图片读取建议统一成：

```java
MyPanel.class.getResource("/0.png")
```

---

## 打包成可运行 JAR（IntelliJ IDEA）

1. 确保 `Main` 中入口是标准写法：

```java
public static void main(String[] args) {
    new Main();
}
```

2. 打开：`File -> Project Structure -> Artifacts`
3. 点击 `+` -> `JAR` -> `From modules with dependencies...`
4. Main Class 选择 `Main`
5. `Build -> Build Artifacts... -> Build`

产物一般在：`out/artifacts/.../*.jar`

运行：

```bash
java -jar xxx.jar
```

---

## 常见问题

### 1）打包后地图变成“全空地”
原因：资源没进 JAR 或路径不对。  
检查是否用的是 `getResourceAsStream("/map1.txt")` 这种写法，并确保 `map1.txt` 在 JAR 内。

### 2）英雄出生点卡住
原因：出生点落在墙里或可碰撞格子里。  
做法：用“在地图里找空地”的方式生成出生点（例如扫描 `EmptyTile`）。

---

## 说明

- 主循环（`MyPanel.run`）负责：**输入、碰撞检测、清理对象、胜负判断、重绘**
- 坦克/子弹用线程驱动移动：子弹线程不断更新坐标，出界后死亡；主循环负责碰撞与回收
