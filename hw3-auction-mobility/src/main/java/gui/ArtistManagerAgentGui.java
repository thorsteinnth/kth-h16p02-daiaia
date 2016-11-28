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
    private JButton startAuctionInClones;

    public ArtistManagerAgentGui(ArtistManagerAgent a)
    {
        super(a);
        this.agent = a;

        // Add button to start an auction
        Component[] components = getContentPane().getComponents();
        JPanel base = (JPanel)components[0];

        JPanel startAuctionPane = new JPanel();
        startAuctionPane.setLayout(new BorderLayout(10,0));
        startAuctionPane.add(startAuction = new JButton("Start auction"));
        startAuction.setToolTipText("Start a new auction");
        startAuction.addActionListener(this);
        base.add(startAuctionPane);

        JPanel startAuctionInClonesPane = new JPanel();
        startAuctionInClonesPane.setLayout(new BorderLayout(10,0));
        startAuctionInClonesPane.add(startAuctionInClones = new JButton("Start auction in clones"));
        startAuctionInClones.setToolTipText("Start auction in clones");
        startAuctionInClones.addActionListener(this);
        base.add(startAuctionInClonesPane);
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == startAuction)
        {
            this.agent.startAuction();
        }
        else if (e.getSource() == startAuctionInClones)
        {
            this.agent.startAuctionInClones();
        }
    }

    public void setStartAuctionButtonEnabled(boolean enabled)
    {
        this.startAuction.setEnabled(enabled);
    }

    public void setStartAuctionInClonesButtonEnabled(boolean enabled)
    {
        this.startAuctionInClones.setEnabled(enabled);
    }
}
