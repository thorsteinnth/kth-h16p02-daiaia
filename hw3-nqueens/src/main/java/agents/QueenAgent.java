package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.Property;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.awt.*;
import java.util.ArrayList;

public class QueenAgent extends Agent
{
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
                System.out.println(getName() + " - Invalid command line arguments. Should be: [ID] [N]");
                return;
            }
        }
        else
        {
            System.out.println(getName() + " - Invalid command line arguments. Should be: [ID] [N]");
            return;
        }

        registerQueenServices();

        this.position = new Point(0, 0); // Y should be fixed according to id

        this.addBehaviour(new InitWakerBehaviour(this, 5000));

        System.out.println("QueenAgent " + getAID().getName() + " is ready. ID: " + this.id + " n: " + this.n);
    }

    protected void takeDown()
    {
        deregisterQueenServices();
        System.out.println("QueenAgent " + getAID().getName() + " terminating.");
    }

    private boolean safe(Point position, ArrayList<Point> queenPositions)
    {
        for(Point queenPosition : queenPositions)
        {
            if(position.y == queenPosition.y)
            {
                // the queens are in the same column
                return false;
            }
            else
            {
                int deltaRow = Math.abs(position.y - queenPosition.y);
                int deltaColumn = Math.abs(position.x - queenPosition.x);

                if(deltaRow == deltaColumn)
                {
                    // the queens are on the same diagonal
                    return false;
                }
            }
        }

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

    /**
     * Search the DF for queens
     */
    private void getQueens()
    {
        ArrayList<AID> foundQueens = new ArrayList<>();

        // Queen service template
        DFAgentDescription queenServiceTemplate = new DFAgentDescription();

        ServiceDescription sd = new ServiceDescription();
        sd.setType(ServiceList.SRVC_QUEEN_TYPE);
        sd.setName(ServiceList.SRVC_QUEEN_NAME);

        // Want to find my predecessor and successor, if any
        if (this.id == 0)
        {
            // Only find a successor (ID 1)
            Property propQueenId = new Property();
            propQueenId.setName("ID");
            propQueenId.setValue(this.id+1);
            sd.addProperties(propQueenId);
        }
        if (this.id == n-1)
        {
            // Only find predecessor
            Property propQueenId = new Property();
            propQueenId.setName("ID");
            propQueenId.setValue(this.id-1);
            sd.addProperties(propQueenId);
        }
        else
        {
            // Find both predecessor and successor
            Property propQueenIdPred = new Property();
            propQueenIdPred.setName("ID");
            propQueenIdPred.setValue(this.id-1);
            Property propQueenIdSucc = new Property();
            propQueenIdSucc.setName("ID");
            propQueenIdSucc.setValue(this.id+1);

            sd.addProperties(propQueenIdPred);
            sd.addProperties(propQueenIdSucc);
        }

        queenServiceTemplate.addServices(sd);

        try
        {
            DFAgentDescription[] result = DFService.search(this, queenServiceTemplate);

            for (int i = 0; i < result.length; ++i)
            {
                foundQueens.add(result[i].getName());
            }

            System.out.println(getName() + " - Found " + foundQueens.size() + " queens: " + foundQueens);

            // TODO Save to predecessor and successor variables
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
    }

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

            getQueens();
        }
    }

    //endregion
}
