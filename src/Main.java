import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Random;
import java.util.Vector;
import javax.swing.*;
import java.io.*;



import static javax.swing.plaf.basic.BasicGraphicsUtils.drawString;

public class Main extends JFrame{
    MyPanel mp;

    public static void main(String[] args){
        new Main();
    }

    public Main(){
        mp = new MyPanel();
        Thread thread = new Thread(mp);
        thread.start();
        this.add(mp);
        this.setSize(1200,800);
        this.addKeyListener(mp);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setVisible(true);
    }
}