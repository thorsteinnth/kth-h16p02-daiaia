package gui;

import mobility.MobileAgent;

import javax.swing.*;
import java.awt.*;

public class ArtistManagerAgentGui extends MobileAgentGui
{
    private JTextField testTextField;

    public ArtistManagerAgentGui(MobileAgent a)
    {
        super(a);

        // Add button to start an auction
        Component[] components = getContentPane().getComponents();
        JPanel base = (JPanel)components[0];

        JPanel pane = new JPanel();
        pane.setLayout(new BorderLayout(10,0));
        pane.add(new JLabel("Test text field : "), BorderLayout.WEST);
        pane.add(testTextField = new JTextField(15), BorderLayout.EAST);
        testTextField.setEditable(false);
        testTextField.setBackground(Color.white);
        base.add(pane);

    }
}
