package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.util.ArrayList;

public class QueenAgent extends Agent
{
    private ArrayList<AID> queens;

    protected void setup()
    {
        this.queens = new ArrayList<>();

        registerQueenServices();
        getQueens();

        System.out.println("QueenAgent " + getAID().getName() + " is ready.");
    }

    protected void takeDown()
    {
        deregisterQueenServices();
        System.out.println("QueenAgent " + getAID().getName() + " terminating.");
    }

    private void registerQueenServices()
    {
        ServiceDescription queenService = new ServiceDescription();
        queenService.setName(ServiceList.SRVC_QUEEN_NAME);
        queenService.setType(ServiceList.SRVC_QUEEN_TYPE);

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
        queenServiceTemplate.addServices(sd);

        try
        {
            DFAgentDescription[] result = DFService.search(this, queenServiceTemplate);

            // TODO Only find my predecessor and successor ... according to ID that is encoded in the name?

            for (int i = 0; i < result.length; ++i)
            {
                foundQueens.add(result[i].getName());
            }

            System.out.println(getName() + " - Found " + foundQueens.size() + " queens");

            this.queens = foundQueens;
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
    }
}
