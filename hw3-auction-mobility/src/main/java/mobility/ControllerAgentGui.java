package mobility;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.border.*;

import jade.core.*;
import jade.gui.*;


public class ControllerAgentGui extends JFrame implements ActionListener {
// -----------------------------------------------------------------------

   private JList list;
   private DefaultListModel listModel;
   private JComboBox locations;
   private JButton newAgent, move, clone, kill, quit;
   private ControllerAgent myAgent;

   public ControllerAgentGui(ControllerAgent a, Set s) {
// -------------------------------------------------------

      super("Controller");
      this.myAgent = a;
      JPanel base = new JPanel();
      base.setBorder(new EmptyBorder(15,15,15,15));
      base.setLayout(new BorderLayout(10,0));
	  getContentPane().add(base);


	  JPanel pane = new JPanel();
	  base.add(pane, BorderLayout.WEST);
      pane.setLayout(new BorderLayout(0,10));
      listModel = new DefaultListModel();
      list = new JList(listModel);
      list.setBorder(new EmptyBorder(2,2,2,2));
      list.setVisibleRowCount(5);
      list.setFixedCellHeight(18);
      list.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
      pane.add(new JScrollPane(list), BorderLayout.NORTH);

      JPanel p = new JPanel();
      p.setLayout(new GridLayout(1,2,5,0));
      p.add(new JLabel("Destination :"));
      locations = new JComboBox(s.toArray());
      p.add(locations);
      pane.add(p, BorderLayout.CENTER);

      p = new JPanel();
      p.setLayout(new GridLayout(1,3,5,0));
      p.add(move = new JButton("Move"));
      move.setToolTipText("Move agent to a new location");
      move.addActionListener(this);
      p.add(clone = new JButton("Clone"));
      clone.setToolTipText("Clone selected agent");
      clone.addActionListener(this);
      p.add(kill = new JButton("Kill"));
      kill.setToolTipText("Kill selected agent");
      kill.addActionListener(this);
      pane.add(p, BorderLayout.SOUTH);
	  move.setEnabled(false);
	  clone.setEnabled(false);
	  kill.setEnabled(false);
      list.addListSelectionListener( new ListSelectionListener() {
	  	 public void valueChanged(ListSelectionEvent e) {
	  		if (list.getSelectedIndex() == -1){
			   move.setEnabled(false);
			   clone.setEnabled(false);
			   kill.setEnabled(false);
			}
			else {
			   move.setEnabled(true);
			   clone.setEnabled(true);
			   kill.setEnabled(true);
			}
	  	 }
	  });
      pane = new JPanel();
      pane.setBorder(new EmptyBorder(0,0,110,0));
	  base.add(pane, BorderLayout.EAST);
      pane.setLayout(new GridLayout(2,1,0,5));
      pane.add(newAgent = new JButton("New agent"));
      newAgent.setToolTipText("Create a new agent");
      newAgent.addActionListener(this);
      pane.add(quit = new JButton("Quit"));
      quit.setToolTipText("Terminate this program");
      quit.addActionListener(this);

      addWindowListener(new WindowAdapter() {
	     public void windowClosing(WindowEvent e) {
		    shutDown();
		 }
	  });

      setSize(300, 210);
      setResizable(false);
      pack();
   }

   public void actionPerformed(ActionEvent ae) {
// ---------------------------------------------

      if (ae.getSource() == newAgent) {

         GuiEvent ge = new GuiEvent(this, myAgent.NEW_AGENT);
         myAgent.postGuiEvent(ge);
	  }
      else if(ae.getSource() == move) {

         GuiEvent ge = new GuiEvent(this, myAgent.MOVE_AGENT);
         ge.addParameter((String)list.getSelectedValue());
         ge.addParameter((String)locations.getSelectedItem());
         myAgent.postGuiEvent(ge);
	  }
      else if (ae.getSource() == clone) {

         GuiEvent ge = new GuiEvent(this, myAgent.CLONE_AGENT);
         ge.addParameter((String)list.getSelectedValue());
         ge.addParameter((String)locations.getSelectedItem());
         myAgent.postGuiEvent(ge);
	  }
	  else if (ae.getSource() == kill) {

         GuiEvent ge = new GuiEvent(this, myAgent.KILL_AGENT);
         ge.addParameter((String)list.getSelectedValue());
         myAgent.postGuiEvent(ge);
	  }
      else if (ae.getSource() == quit) {
         shutDown();
	  }
   }

   void shutDown() {
// -----------------  Control the closing of this gui

      GuiEvent ge = new GuiEvent(this, myAgent.QUIT);
      myAgent.postGuiEvent(ge);
   }

   public void updateList(Vector v) {
// ----------------------------------

      listModel.clear();
      for (int i = 0; i < v.size(); i++){
         listModel.addElement(v.get(i));
	  }
   }

}