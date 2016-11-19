package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.ParallelBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetInitiator;

import java.util.ArrayList;
import java.util.Vector;

public class ArtistManagerAgent extends Agent
{
    private DFAgentDescription bidderServiceTemplate;
    private ArrayList<AID> bidders;

    protected void setup()
    {
        this.bidders = new ArrayList<>();

        // Bidder service template
        this.bidderServiceTemplate = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(ServiceList.SRVC_CURATOR_BIDDER_TYPE);
        sd.setName(ServiceList.SRVC_CURATOR_BIDDER_NAME);
        this.bidderServiceTemplate.addServices(sd);

        this.addBehaviour(new RunAuctionTicker(this, 5000));

        System.out.println("ArtistManagerAgent " + getAID().getName() + " is ready.");
    }

    protected void takeDown()
    {
        System.out.println("ArtistManagerAgent " + getAID().getName() + " terminating.");
    }

    /**
     * Search the DF for bidders
     */
    private void getBidders()
    {
        ArrayList<AID> foundBidders = new ArrayList<>();

        try
        {
            DFAgentDescription[] result = DFService.search(this, this.bidderServiceTemplate);
            for (int i = 0; i < result.length; ++i)
            {
                foundBidders.add(result[i].getName());
            }

            System.out.println(getName() + " - Found " + foundBidders.size() + " bidders");

            this.bidders = foundBidders;
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
    }

    //region Behaviours

    private class RunAuctionTicker extends TickerBehaviour
    {
        public RunAuctionTicker(ArtistManagerAgent agent, long timeout)
        {
            super(agent, timeout);
        }

        @Override
        protected void onTick()
        {
            getBidders();

            if (bidders.size() == 0)
            {
                System.out.println(myAgent.getName()
                        + " - AuctionSequentialBehaviour - there are no bidders, aborting");
                return;
            }

            // Prepare request
            ACLMessage request = new ACLMessage(ACLMessage.CFP);
            request.setConversationId("auction");
            request.setContent("this is the content :)");
            request.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
            for (AID bidder : bidders)
                request.addReceiver(bidder);

            myAgent.addBehaviour(
                    new ContractNetInitiator(myAgent, request)
                    {
                        @Override
                        protected void handlePropose(ACLMessage propose, Vector acceptances)
                        {
                            super.handlePropose(propose, acceptances);
                            System.out.println("Received proposal: " + propose);
                            System.out.println("Acceptances: " + propose);

                            // TODO Figure out when we have received all proposals and move on to
                            // next step in protocol
                        }

                        @Override
                        protected void handleRefuse(ACLMessage refuse)
                        {
                            super.handleRefuse(refuse);
                        }

                        @Override
                        protected void handleInform(ACLMessage inform)
                        {
                            super.handleInform(inform);
                        }
                    }
            );
        }
    }

    /* Normal (non-FIPA) implementation

    private class AuctionSequentialBehaviour extends SequentialBehaviour
    {
        public AuctionSequentialBehaviour(ArtistManagerAgent agent)
        {
            super(agent);

            getBidders();
            if (bidders.size() == 0)
            {
                System.out.println(myAgent.getName()
                        + " - AuctionSequentialBehaviour - there are no bidders, aborting");
                return;
            }

            this.addSubBehaviour(new RequestBidsParallelBehaviour(agent));
        }
    }

    private class RequestBidsParallelBehaviour extends ParallelBehaviour
    {
        public RequestBidsParallelBehaviour(ArtistManagerAgent agent)
        {
            // Set owner agent and terminate when all children are done
            super(agent, WHEN_ALL);

            for (AID bidder : bidders)
            {
                this.addSubBehaviour(new RequestBidFromBidder(agent, bidder));
            }
        }
    }

    private class RequestBidFromBidder extends OneShotBehaviour
    {
        private AID bidder;

        public RequestBidFromBidder(ArtistManagerAgent agent, AID bidder)
        {
            super(agent);
            this.bidder = bidder;
        }

        public void action()
        {
            System.out.println("Requesting bid from bidder: " + this.bidder.getName());

            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.addReceiver(this.bidder);
            cfp.setConversationId("request-bid");
            cfp.setContent("this is the content :)");
            myAgent.send(cfp);
        }
    }

    */

    //endregion
}
