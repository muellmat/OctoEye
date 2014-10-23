import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Main {

    public MainController controller = new MainController(this);

    public String path;
    public File folder;
    public File[] files;
    public JFrame frame;
    public JSlider slider;
    public JLabel text;
    public JLabel labelSrc;
    public JLabel labelDst;
    public BufferedImage imageSrc;
    public BufferedImage imageDst;
    public OctoEye oe;

    public Main(String[] args) {
        if (args.length!=1) {
            System.err.println("Path to images missing.");
            System.exit(1);
        }

        path   = String.format(args[0]);
        folder = new File(path);
        files  = folder.listFiles();
        frame  = new JFrame();
        slider = new JSlider(JSlider.HORIZONTAL,0,files.length-1,0);
        text   = new JLabel("");
        labelSrc = new JLabel();
        labelDst = new JLabel();

        setImage(0);

        slider.addChangeListener(controller);
        slider.setMajorTickSpacing(50);
        slider.setMinorTickSpacing(10);
        slider.setMinimum(0);
        slider.setMaximum(files.length);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        text.setFont(new Font("Courier New",Font.BOLD,12));
        text.setHorizontalAlignment(SwingConstants.CENTER);
        labelSrc.setBorder(BorderFactory.createLineBorder(Color.BLACK,1));
        labelDst.setBorder(BorderFactory.createLineBorder(Color.BLACK,1));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocation(50,50);
        frame.getContentPane().add(text,BorderLayout.NORTH);
        frame.getContentPane().add(labelSrc,BorderLayout.WEST);
        frame.getContentPane().add(labelDst,BorderLayout.EAST);
        frame.getContentPane().add(slider,BorderLayout.SOUTH);
        frame.pack();
        frame.setVisible(true);
    }

    public void setImage(int i) {
        if (i<0 || i>=files.length)
            return;
        readImageFromFile(files[i].toString());
        frame.setTitle(files[i].getName());
        labelSrc.setIcon(new ImageIcon(imageSrc));
        labelDst.setIcon(new ImageIcon(imageDst));
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
        imageSrc = new BufferedImage(OctoEye.WIDTH,OctoEye.HEIGHT,BufferedImage.TYPE_BYTE_GRAY);
        System.arraycopy(buffer,0,((DataBufferByte)imageSrc.getRaster().getDataBuffer()).getData(),0,buffer.length);
        oe = new OctoEye(buffer);

        imageSrc = oe.getBufferedImage(oe.getDbg());
        imageDst = oe.getBufferedImage(oe.getDst());

        String info = String.format("t = %02d ms    d = %02dpx    a = %02dpx    b = %02dpx    [%s%s]",
                oe.getTime(),
                oe.getDiameter(),
                oe.getPupilMajorAxis(),
                oe.getPupilMinorAxis(),
                oe.isStar()?"*":" ",
                oe.isRing()?"o":" ",
                fileName);
        text.setText(info);
        //System.out.println(info + "    " + fileName);
    }

    public static void main(String[] args) {
        new Main(args);
    }
}
