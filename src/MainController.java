import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class MainController implements ChangeListener {

    Main main;

    MainController(Main m) {
        main = m;
    }

    public void stateChanged(ChangeEvent e) {
        main.setImage((int)((JSlider)e.getSource()).getValue());
    }

}