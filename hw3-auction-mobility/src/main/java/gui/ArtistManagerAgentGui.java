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
    private JButton reportWinningBid;

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

        JPanel reportWinningBidPane = new JPanel();
        reportWinningBidPane.setLayout(new BorderLayout(10,0));
        reportWinningBidPane.add(reportWinningBid = new JButton("Report winning bid"));
        reportWinningBid.setToolTipText("Report winning bid");
        reportWinningBid.addActionListener(this);
        setReportWinningButtonEnabled(false);
        base.add(reportWinningBidPane);
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == startAuction)
        {
            this.agent.startAuction();
        }
        else if (e.getSource() == reportWinningBid)
        {
            this.agent.reportWinningBid();
        }
    }

    public void setReportWinningButtonEnabled(boolean enabled)
    {
        this.reportWinningBid.setEnabled(enabled);
    }

    public void setStartAuctionButtonEnabled(boolean enabled)
    {
        this.startAuction.setEnabled(enabled);
    }
}
