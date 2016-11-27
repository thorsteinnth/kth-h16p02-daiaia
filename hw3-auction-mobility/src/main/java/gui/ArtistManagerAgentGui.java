package gui;

import agents.ArtistManagerAgent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ArtistManagerAgentGui extends MobileAgentGui implements ActionListener
{
    private ArtistManagerAgent agent;
    private JButton startAuction;

    public ArtistManagerAgentGui(ArtistManagerAgent a)
    {
        super(a);
        this.agent = a;

        // Add button to start an auction
        Component[] components = getContentPane().getComponents();
        JPanel base = (JPanel)components[0];

        JPanel pane = new JPanel();
        pane.setLayout(new BorderLayout(10,0));
        pane.add(startAuction = new JButton("Start auction"));
        startAuction.setToolTipText("Start a new auction");
        startAuction.addActionListener(this);
        base.add(pane);
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == startAuction)
        {
            this.agent.startAuction();
        }
    }
}
