package gui;

import agents.CuratorAgent;
import javax.swing.*;
import java.awt.*;

public class CuratorAgentGui extends MobileAgentGui
{
    private JLabel strategy;

    public CuratorAgentGui(CuratorAgent a){

        super(a);
        Component[] components = getContentPane().getComponents();
        JPanel base = (JPanel)components[0];
        JPanel pane = new JPanel();
        pane.setLayout(new BorderLayout(20,0));
        strategy = new JLabel();
        pane.add(strategy, BorderLayout.WEST);
        base.add(pane, BorderLayout.SOUTH);
    }

    public void setLocation(String loc){

        super.setLocation(loc);
    }

    public void setInfo(String info){

        super.setInfo(info);
    }

    public void setStrategy(String info){

        this.strategy.setText("Strategy: " + info);
    }
}
