package mobility;

import java.util.*;

import agents.ArtistManagerAgent;
import agents.CuratorAgent;
import agents.ServiceList;
import gui.ControllerAgentGui;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.*;
import jade.content.*;
import jade.content.onto.basic.*;
import jade.content.lang.sl.*;
import jade.core.*;
import jade.domain.mobility.*;
import jade.domain.JADEAgentManagement.*;
import jade.gui.*;


public class ControllerAgent extends GuiAgent {
// --------------------------------------------

   private jade.wrapper.AgentContainer home;
   private jade.wrapper.AgentContainer[] container = null;
   private Map locations = new HashMap();
   private Vector agents = new Vector();
   private int agentCntArtistManager = 0;
   private int agentCntCurator = 0;
   private int agentCloneCntArtistManager = 0;
   private int agentCloneCntCurator = 0;

   private int command;
   transient protected ControllerAgentGui myGui;

   public static final int QUIT = 0;
   public static final int NEW_ARTISTMANAGER_AGENT = 1;
   public static final int NEW_CURATOR_AGENT = 2;
   public static final int MOVE_AGENT = 3;
   public static final int CLONE_AGENT = 4;
   public static final int KILL_AGENT = 5;

   private static final String CURATOR_AGENT_NAME = "CuratorAgent";
   private static final String ARTISTMANAGER_AGENT_NAME = "ArtistManagerAgent";

   // Get a JADE Runtime instance
   jade.core.Runtime runtime = jade.core.Runtime.instance();

   protected void setup() {
// ------------------------

	  // Register language and ontology
	  getContentManager().registerLanguage(new SLCodec());
	  getContentManager().registerOntology(MobilityOntology.getInstance());

      try {
         // Create the container objects
         home = runtime.createAgentContainer(new ProfileImpl());
         container = new jade.wrapper.AgentContainer[3];
         for (int i = 0; i < 5; i++){
            container[0] = runtime.createAgentContainer(new ProfileImpl());
	     }
	     doWait(2000);

	     // Get available locations with AMS
	     sendRequest(new Action(getAMS(), new QueryPlatformLocationsAction()));

	     //Receive response from AMS
         MessageTemplate mt = MessageTemplate.and(
			                  MessageTemplate.MatchSender(getAMS()),
			                  MessageTemplate.MatchPerformative(ACLMessage.INFORM));
         ACLMessage resp = blockingReceive(mt);
         ContentElement ce = getContentManager().extractContent(resp);
         Result result = (Result) ce;
         jade.util.leap.Iterator it = result.getItems().iterator();
         while (it.hasNext()) {
            Location loc = (Location)it.next();
            locations.put(loc.getName(), loc);
		 }
	  }
	  catch (Exception e) { e.printStackTrace(); }


	  // Create and show the gui
      myGui = new ControllerAgentGui(this, locations.keySet());
      myGui.setVisible(true);

       // Register service and add behaviour (added by Fannar and Thorsteinn)
       registerControllerServices();
       addBehaviour(new AddAgentToGuiServer(this));
   }


   protected void onGuiEvent(GuiEvent ev) {
// ----------------------------------------

	  command = ev.getType();

	  if (command == QUIT) {
	     try {
		    home.kill();
		    for (int i = 0; i < container.length; i++) container[i].kill();
	     }
	     catch (Exception e) { e.printStackTrace(); }
	     myGui.setVisible(false);
	     myGui.dispose();
		 doDelete();
		 System.exit(0);
      }
	  if (command == NEW_ARTISTMANAGER_AGENT) {

	     jade.wrapper.AgentController a = null;
         try {
            Object[] args = new Object[2];
            args[0] = getAID();
            String name = ARTISTMANAGER_AGENT_NAME+agentCntArtistManager++;
            a = home.createNewAgent(name, ArtistManagerAgent.class.getName(), args);
	        a.start();
	        agents.add(name);
	        myGui.updateList(agents);
	     }
         catch (Exception ex) {
		    System.out.println("Problem creating new agent");
	     }
         return;
	  }
	  else if (command == NEW_CURATOR_AGENT) {

          jade.wrapper.AgentController a = null;
          try {
              Object[] args = new Object[2];
              args[0] = getAID();
              String name = CURATOR_AGENT_NAME+agentCntCurator++;
              a = home.createNewAgent(name, CuratorAgent.class.getName(), args);
              a.start();
              agents.add(name);
              myGui.updateList(agents);
          }
          catch (Exception ex) {
              System.out.println("Problem creating new agent");
          }
          return;
      }
      String agentName = (String)ev.getParameter(0);
      AID aid = new AID(agentName, AID.ISLOCALNAME);

	  if (command == MOVE_AGENT) {

         String destName = (String)ev.getParameter(1);
         Location dest = (Location)locations.get(destName);
         MobileAgentDescription mad = new MobileAgentDescription();
         mad.setName(aid);
         mad.setDestination(dest);
         MoveAction ma = new MoveAction();
         ma.setMobileAgentDescription(mad);
         sendRequest(new Action(aid, ma));
	  }
      else if (command == CLONE_AGENT) {

         String destName = (String)ev.getParameter(1);
         Location dest = (Location)locations.get(destName);
         MobileAgentDescription mad = new MobileAgentDescription();
         mad.setName(aid);
         mad.setDestination(dest);
         String newName = "";

         if(agentName.contains(CURATOR_AGENT_NAME))
         {
             newName = "Clone-" + agentName + "-" + agentCloneCntCurator++;
         }
         else if(agentName.contains(ARTISTMANAGER_AGENT_NAME))
         {
             newName = "Clone-" + agentName + "-" + agentCloneCntArtistManager++;
         }
         else
         {
             newName = "Clone-"+agentName;
         }
         CloneAction ca = new CloneAction();
         ca.setNewName(newName);
         ca.setMobileAgentDescription(mad);
         sendRequest(new Action(aid, ca));
         agents.add(newName);
         myGui.updateList(agents);
	  }
      else if (command == KILL_AGENT) {

         KillAgent ka = new KillAgent();
         ka.setAgent(aid);
         sendRequest(new Action(aid, ka));
	     agents.remove(agentName);
		 myGui.updateList(agents);
	  }
   }


   void sendRequest(Action action) {
// ---------------------------------

      ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
      request.setLanguage(new SLCodec().getName());
      request.setOntology(MobilityOntology.getInstance().getName());
      try {
	     getContentManager().fillContent(request, action);
	     request.addReceiver(action.getActor());
	     send(request);
	  }
	  catch (Exception ex) { ex.printStackTrace(); }
   }

    //region Behaviours and services (added by Fannar and Thorsteinn)

    /**
     * Register the controller agent services with DF
     * */
    private void registerControllerServices()
    {
        ServiceDescription controllerService = new ServiceDescription();

        String serviceName = ServiceList.SRVC_CONTROLLER_NAME; // + "-" + destination.getName()
        String serviceType = ServiceList.SRVC_CONTROLLER_TYPE;

        System.out.println("Registering controller service at: " + serviceName);

        controllerService.setName(serviceName);
        controllerService.setType(serviceType);

        DFAgentDescription agentDescription = new DFAgentDescription();
        agentDescription.setName(getAID());
        agentDescription.addServices(controllerService);

        try
        {
            DFService.register(this, agentDescription);
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
    }

    /**
     * Behaviour that allows other agents to add themselves to the controller GUI
     * Used when agents are created by some other agent than the ControllerAgent
     * */
    private class AddAgentToGuiServer extends CyclicBehaviour
    {
        public AddAgentToGuiServer(ControllerAgent a)
        {
            super(a);
        }

        public void action()
        {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchConversationId("new-agents-notification")
            );
            ACLMessage msg = this.myAgent.receive(mt);

            if (msg != null)
            {
                //System.out.println(myAgent.getName() + " - Received message from " + msg.getSender().getName() + " - " + msg);

                try
                {
                    ArrayList<String> newAgentNames = (ArrayList<String>) msg.getContentObject();
                    for (String newAgentName : newAgentNames)
                        agents.add(newAgentName);

                    myGui.updateList(agents);
                }
                catch (UnreadableException ex)
                {
                    System.err.println(ex);
                }
            }
            else
            {
                block();
            }
        }
    }

    //endregion

}//class Controller
