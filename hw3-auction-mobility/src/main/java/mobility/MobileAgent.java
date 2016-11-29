package mobility;

import agents.ArtistManagerAgent;
import agents.CuratorAgent;
import gui.CuratorAgentGui;
import gui.ArtistManagerAgentGui;
import gui.MobileAgentGui;
import jade.core.*;
import jade.core.behaviours.*;
import jade.lang.acl.*;
import jade.content.*;
import jade.content.lang.sl.*;
import jade.content.onto.basic.*;
import jade.domain.mobility.*;
import jade.domain.JADEAgentManagement.*;
import jade.gui.*;


public class MobileAgent extends GuiAgent {
// ----------------------------------------

   private AID controller;
   protected Location destination;
   transient protected MobileAgentGui myGui;

   protected void setup() {
// ------------------------

	  // Retrieve arguments passed during this agent creation
	  Object[] args = getArguments();
	  controller = (AID) args[0];
	  destination = here();

	   init();

	  // Program the main behaviour of this agent
	  addBehaviour(new ReceiveCommands(this));
   }

   void init() {
// -------------

	  // Register language and ontology
	  getContentManager().registerLanguage(new SLCodec());
	  getContentManager().registerOntology(MobilityOntology.getInstance());

	   // Create and display the gui

	   if (this instanceof CuratorAgent)
	   {
		   myGui = new CuratorAgentGui((CuratorAgent)this);
           myGui.setVisible(true);
           myGui.setLocation(destination.getName());
	   }
	   else if (this instanceof ArtistManagerAgent)
	   {
		   // Show artist manager agent GUI
		   myGui = new ArtistManagerAgentGui((ArtistManagerAgent)this);
		   myGui.setVisible(true);
		   myGui.setLocation(destination.getName());
	   }
	   else
	   {
		   // Show generic mobile agent UI
		   myGui = new MobileAgentGui(this);
		   myGui.setVisible(true);
		   myGui.setLocation(destination.getName());
	   }
   }

   protected void onGuiEvent(GuiEvent e) {
// ---------------------------------------
   //No interaction with the gui
   }

   protected void beforeMove() {
// -----------------------------

	  System.out.println("Moving now to location : " + destination.getName());
	  myGui.setVisible(false);
	  myGui.dispose();
   }

   protected void afterMove() {
// ----------------------------

	  init();
	  myGui.setInfo("Arrived at location : " + destination.getName());
   }

   protected void beforeClone() {
// -----------------------------

	  myGui.setInfo("Cloning myself to location : " + destination.getName());
   }

   protected void afterClone() {
// ----------------------------

	  init();
   }


   /*
   * Receive all commands from the controller agent
   */
   class ReceiveCommands extends CyclicBehaviour {
// -----------------------------------------------

	  ReceiveCommands(Agent a) { super(a); }

	  public void action() {

		 ACLMessage msg = receive(
				 MessageTemplate.or(
						 MessageTemplate.MatchSender(controller),
						 MessageTemplate.MatchConversationId("artistmanageragent-setup-other-agents")
				 )
		 );

		 if (msg == null) { block(); return; }

		 if (msg.getPerformative() == ACLMessage.REQUEST){

			try {
			   ContentElement content = getContentManager().extractContent(msg);
			   Concept concept = ((Action)content).getAction();

			   if (concept instanceof CloneAction){

				  CloneAction ca = (CloneAction)concept;
				  String newName = ca.getNewName();
				  Location l = ca.getMobileAgentDescription().getDestination();
				  if (l != null) destination = l;
				  doClone(destination, newName);
			   }
			   else if (concept instanceof MoveAction){

				  MoveAction ma = (MoveAction)concept;
				  Location l = ma.getMobileAgentDescription().getDestination();

				   // Begin added by Thorsteinn and Fannar
				   // Don't allow moves to our current container
				   // (moves to the same container we are already in)
				   // The GUI just disappears if we do that, this is a workaround for that
				   if (l != null && l.equals(here()))
				   {
					   System.out.println(getName() + " - Trying to move to current container. Aborting move.");
					   return;
				   }
				   // End added by Thorsteinn and Fannar

				  if (l != null) doMove(destination = l);
			   }
			   else if (concept instanceof KillAgent){

				  myGui.setVisible(false);
				  myGui.dispose();
				  doDelete();
			   }
			}
			catch (Exception ex) { ex.printStackTrace(); }
		 }
		 else { System.out.println("Unexpected msg from controller agent"); }
	  }
   }

} // class MobileAgent