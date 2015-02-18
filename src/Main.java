import javax.swing.*;
import javax.swing.event.*;
import java.awt.image.*;
import java.io.*;

public class Main {

    private JFrame frame;

    private JPanel main;
    private JLabel status;
    private JLabel srcLabel;
    private JLabel dstLabel;
    private JSlider select;

    private String path;
    private File folder;
    private File[] files;
    private BufferedImage src;
    private BufferedImage dst;
    private OctoEye oe;

    public Main(String[] args) {
        if (args.length!=1) {
            System.err.println("Path to images missing.");
            System.exit(1);
        }

        path   = String.format(args[0]);
        folder = new File(path);
        files  = folder.listFiles();

        frame = new JFrame("OctoEye");
        frame.setContentPane(main);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setResizable(false);
        frame.setVisible(true);

        select.setMinimum(0);
        select.setMaximum(files.length - 1);
        select.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                setImage((int) ((JSlider) e.getSource()).getValue());
            }
        });

        setImage(0);
    }

    public static void main(String[] args) {
        new Main(args);
    }

    public void setImage(int i) {
        if (i<0 || i>=files.length)
            return;
        readImageFromFile(files[i].toString());

        frame.setTitle(files[i].getName());
        srcLabel.setIcon(new ImageIcon(src.getScaledInstance(OctoEye.WIDTH*2,OctoEye.HEIGHT*2,java.awt.Image.SCALE_SMOOTH)));
        dstLabel.setIcon(new ImageIcon(dst.getScaledInstance(OctoEye.WIDTH*2,OctoEye.HEIGHT*2,java.awt.Image.SCALE_SMOOTH)));
    }

    public void readImageFromFile(String fileName) {
        File file = new File(fileName);
        byte buffer[] = new byte[(int)file.length()];
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            fis.read(buffer);
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        src = new BufferedImage(OctoEye.WIDTH,OctoEye.HEIGHT,BufferedImage.TYPE_BYTE_GRAY);
        System.arraycopy(buffer,0,((DataBufferByte)src.getRaster().getDataBuffer()).getData(),0,buffer.length);
        oe = new OctoEye(buffer);

        src = oe.getBufferedImage(oe.getDbg());
        dst = oe.getBufferedImage(oe.getDst());

        String info = String.format("t = %02d ms    d = %02dpx    a = %02dpx    b = %02dpx    [%s%s]",
                oe.getTime(),
                oe.getDiameter(),
                oe.getPupilMajorAxis(),
                oe.getPupilMinorAxis(),
                oe.isStar()?"*":" ",
                oe.isRing()?"o":" ",
                fileName);
        status.setText(info);
    }

}
