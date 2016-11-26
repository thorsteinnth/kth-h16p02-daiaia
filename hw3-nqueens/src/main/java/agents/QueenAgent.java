package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.Property;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class QueenAgent extends Agent
{
    private final String SET_POSITION_REQUEST = "set-position";
    private final String CHANGE_POSITION_REQUEST = "change-position";

    private AID predecessor;
    private AID successor;
    /**
     * My ID.
     * */
    private int id;
    /**
     * Total number of queens. Will be arranged on an nxn matrix.
     * */
    private int n;
    private Point position;
    private ArrayList<Point> triedPositions;

    protected void setup()
    {
        // Get command line arguments
        Object[] args = getArguments();
        if (args != null && args.length == 2)
        {
            try
            {
                this.id = Integer.parseInt((String)args[0]);
                this.n = Integer.parseInt((String)args[1]);
            }
            catch (Exception ex)
            {
                System.err.println(ex);
                System.out.println(getName() + " - Invalid command line arguments. Should be: [ID],[N]");
                return;
            }
        }
        else
        {
            System.out.println(getName() + " - Invalid command line arguments. Should be: [ID],[N]");
            return;
        }

        registerQueenServices();

        this.triedPositions = new ArrayList<>();
        this.position = new Point(0, this.id);

        this.addBehaviour(new InitWakerBehaviour(this, 5000));

        System.out.println("QueenAgent " + getAID().getName() + " is ready. ID: " + this.id + " n: " + this.n);
    }

    protected void takeDown()
    {
        deregisterQueenServices();
        System.out.println("QueenAgent " + getAID().getName() + " terminating.");
    }

    private boolean safe(Point position, ArrayList<Point> filledPositions)
    {
        for(Point filledPosition : filledPositions)
        {
            if(position.x == filledPosition.x)
            {
                // this is no a safe position - the queens are in the same column
                return false;
            }
            else
            {
                int deltaRow = Math.abs(position.y - filledPosition.y);
                int deltaColumn = Math.abs(position.x - filledPosition.x);

                if(deltaRow == deltaColumn)
                {
                    // this is not a safe position - the queens are on the same diagonal
                    return false;
                }
            }
        }

        this.triedPositions.add(position);
        return true;
    }

    private void registerQueenServices()
    {
        ServiceDescription queenService = new ServiceDescription();
        queenService.setName(ServiceList.SRVC_QUEEN_NAME);
        queenService.setType(ServiceList.SRVC_QUEEN_TYPE);
        Property propQueenId = new Property();
        propQueenId.setName("ID");
        propQueenId.setValue(this.id);
        queenService.addProperties(propQueenId);

        DFAgentDescription agentDescription = new DFAgentDescription();
        agentDescription.setName(getAID());
        agentDescription.addServices(queenService);

        try
        {
            DFService.register(this, agentDescription);
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
    }

    private void deregisterQueenServices()
    {
        try
        {
            DFService.deregister(this);
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
    }

    //region DF search

    private DFAgentDescription getQueenWithIdAgentDescription(int id)
    {
        DFAgentDescription queenServiceTemplate = new DFAgentDescription();

        ServiceDescription sd = new ServiceDescription();
        sd.setType(ServiceList.SRVC_QUEEN_TYPE);
        sd.setName(ServiceList.SRVC_QUEEN_NAME);

        Property propQueenId = new Property();
        propQueenId.setName("ID");
        propQueenId.setValue(id);
        sd.addProperties(propQueenId);

        queenServiceTemplate.addServices(sd);

        return queenServiceTemplate;
    }

    /**
     * Search the DF for queens
     */
    private void getPredecessorAndSuccessor()
    {
        try
        {
            // Find predecessor

            ArrayList<AID> foundPredecessors = new ArrayList<>();

            DFAgentDescription[] predecessorResult = DFService.search(this, getQueenWithIdAgentDescription(this.id-1));

            for (int i = 0; i < predecessorResult.length; ++i)
            {
                foundPredecessors.add(predecessorResult[i].getName());
            }

            //System.out.println(getName() + " - Found " + foundPredecessors.size() + " predecessors: " + foundPredecessors);

            // Find successors

            ArrayList<AID> foundSuccessors = new ArrayList<>();

            DFAgentDescription[] successorResult = DFService.search(this, getQueenWithIdAgentDescription(this.id+1));

            for (int i = 0; i < successorResult.length; ++i)
            {
                foundSuccessors.add(successorResult[i].getName());
            }

            //System.out.println(getName() + " - Found " + foundSuccessors.size() + " successors: " + foundSuccessors);

            if (foundPredecessors.size() == 1)
            {
                this.predecessor = foundPredecessors.get(0);
            }

            if (foundSuccessors.size() == 1)
            {
                this.successor = foundSuccessors.get(0);
            }

            System.out.println(getName()
                    + " - Predecessor: " + (this.predecessor != null ? this.predecessor.getName() : "null")
                    + " - Successor: " + (this.successor != null ? this.successor.getName() : "null")
            );
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
    }

    //endregion

    //region Behaviours

    private class InitWakerBehaviour extends WakerBehaviour
    {
        public InitWakerBehaviour(Agent a, long timeout)
        {
            super(a, timeout);
        }

        @Override
        protected void onWake()
        {
            super.onWake();

            getPredecessorAndSuccessor();
            addBehaviour(new QueenServer());
        }
    }

    private class QueenServer extends CyclicBehaviour
    {
        public void action()
        {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchConversationId("nqueens")
            );

            ACLMessage msg = myAgent.receive(mt);

            if (msg != null)
            {
                String requestType = msg.getContent();

                try
                {
                    ArrayList<Point> filledPositions = (ArrayList<Point>) msg.getContentObject();

                    if (requestType.equals(SET_POSITION_REQUEST))
                    {

                    }
                    else if(requestType.equals((CHANGE_POSITION_REQUEST)))
                    {

                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
            else
            {
                block();
            }
        }
    }

    //endregion
}
