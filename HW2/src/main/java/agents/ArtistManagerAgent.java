package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
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

        this.addBehaviour(new RunAuctionWaker(this, 5000));

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

    private class RunAuctionWaker extends WakerBehaviour
    {
        public RunAuctionWaker(ArtistManagerAgent agent, long timeout)
        {
            super(agent, timeout);
        }

        @Override
        protected void onWake()
        {
            getBidders();

            if (bidders.size() == 0)
            {
                System.out.println(myAgent.getName()
                        + " - AuctionSequentialBehaviour - there are no bidders, aborting");
                return;
            }

            // Prepare request
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.setConversationId("auction");
            cfp.setContent("this is the content :)");
            cfp.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
            for (AID bidder : bidders)
                cfp.addReceiver(bidder);

            myAgent.addBehaviour(new DutchAuctionInitiator(myAgent, cfp));
        }
    }

    private class DutchAuctionInitiator extends ContractNetInitiator
    {
        /*
        From documentation:
        The initiator can evaluate all the received proposals and make its choice of which agent proposals
        will be accepted and which will be rejected. This class provides two ways for this evaluation.
        It can be done progressively each time a new PROPOSE message is received and a new call to the handlePropose()
        callback method is executed or, in alternative, it can be done just once when all the PROPOSE messages
        have been collected (or the reply-by deadline has expired) and a single call to the handleAllResponses()
        callback method is executed. In both cases, the second parameter of the method,
        i.e. the Vector acceptances, must be filled with the appropriate ACCEPT/REJECT-PROPOSAL messages.
        Notice that, for the first case, the method skipNextResponses() has been provided that,
        if called by the programmer when waiting for PROPOSE messages, allows to skip to the next state
        and ignore all the responses and proposals that have not yet been received.
        */

        public DutchAuctionInitiator(Agent a, ACLMessage cfp)
        {
            super(a, cfp);
        }

        @Override
        protected void handleAllResponses(Vector responses, Vector acceptances)
        {
            // All responses have been received or the reply-by deadline has expired

            super.handleAllResponses(responses, acceptances);

            // Fill the acceptances vector with ACCEPT/REJECT-PROPOSAL messages

            System.out.println(myAgent.getName() + " - All responses received");

            for (int i = 0; i < responses.size(); i++)
            {
                ACLMessage response = (ACLMessage)responses.elementAt(i);

                if (response.getPerformative() == ACLMessage.PROPOSE)
                {
                    System.out.println("Have proposal for " + response.getContent());
                    ACLMessage reply = response.createReply();
                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                    acceptances.add(reply);
                }
            }
        }

        @Override
        protected void handleInform(ACLMessage inform)
        {
            super.handleInform(inform);
            System.out.println(myAgent.getName() + " - Received INFORM: " + inform);
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
