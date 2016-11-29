package gui;

import agents.CuratorAgent;
import javax.swing.*;
import java.awt.*;

public class CuratorAgentGui extends MobileAgentGui
{
    private JLabel strategy;
    private JLabel interests;

    public CuratorAgentGui(CuratorAgent a){

        super(a);
        Component[] components = getContentPane().getComponents();
        JPanel base = (JPanel)components[0];

        JPanel strategyPane = new JPanel();
        strategyPane.setLayout(new BorderLayout(200,0));
        strategy = new JLabel();
        strategyPane.add(strategy, BorderLayout.SOUTH);
        base.add(strategyPane, BorderLayout.SOUTH);

        interests = new JLabel();
        base.add(interests, BorderLayout.SOUTH);
    }

    public void setLocation(String loc){

        super.setLocation(loc);
    }

    public void setInfo(String info){

        super.setInfo(info);
    }

    public void setStrategy(String strategy){

        this.strategy.setText("Strategy: " + strategy);
    }

    public void setInterests(String interests){

        this.interests.setText(interests);
    }
}
