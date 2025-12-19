import java.io.*;

public class Recorder{
    private static int allEnemyTankNum = 0;
    private static int bestScore = 0;

    // 最高的分
    private static final String recordFile = "record.txt";

    public static void loadRecord() {
        File f = new File(recordFile);
        if (!f.exists()) {
            bestScore = 0;
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line = br.readLine();
            if (line != null && !line.isEmpty()) {
                bestScore = Integer.parseInt(line.trim());
            }
        } catch (Exception e) {
            bestScore = 0;
        }
    }

    private static void saveRecord() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(recordFile))) {
            bw.write(String.valueOf(bestScore));
        } catch (Exception ignored) {}
    }

    public static int getAllEnemyTankNum(){ return allEnemyTankNum; }
    public static int getBestScore(){ return bestScore; }

    public static void setAllEnemyTankNum(int n){ allEnemyTankNum = n; }

    public static void addAllEnemyTankNum(){
        allEnemyTankNum++;
        if (allEnemyTankNum > bestScore) {
            bestScore = allEnemyTankNum;
            saveRecord();
        }
    }
}