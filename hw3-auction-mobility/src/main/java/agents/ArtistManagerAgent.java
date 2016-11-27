package agents;

import DTOs.BidRequestDTO;
import artifacts.Painting;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;
import jade.proto.ContractNetInitiator;
import mobility.MobileAgent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.ThreadLocalRandom;

public class ArtistManagerAgent extends MobileAgent
{
    private DFAgentDescription bidderServiceTemplate;
    private ArrayList<AID> bidders;

    protected void setup()
    {
        super.setup();

        this.bidders = new ArrayList<>();

        // Bidder service template
        this.bidderServiceTemplate = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(ServiceList.SRVC_CURATOR_BIDDER_TYPE);
        sd.setName(ServiceList.SRVC_CURATOR_BIDDER_NAME);
        this.bidderServiceTemplate.addServices(sd);

        // We start the auction from the GUI instead
        //this.addBehaviour(new AuctionManagementWaker(this, 5000));

        System.out.println("ArtistManagerAgent " + getAID().getName() + " is ready.");
    }

    protected void takeDown()
    {
        System.out.println("ArtistManagerAgent " + getAID().getName() + " terminating.");
    }

    public void startAuction()
    {
        System.out.println(this.getName() + " - Starting auction");
        this.addBehaviour(new AuctionManagementBehaviour(this));
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

    private class AuctionManagementWaker extends WakerBehaviour
    {
        public AuctionManagementWaker(ArtistManagerAgent agent, long timeout)
        {
            super(agent, timeout);
        }

        @Override
        protected void onWake()
        {
            System.out.println(myAgent.getName() + " - Starting auction");
            myAgent.addBehaviour(new AuctionManagementBehaviour(myAgent));
        }
    }

    private class AuctionManagementBehaviour extends SequentialBehaviour
    {
        public AuctionManagementBehaviour(Agent agent)
        {
            super(agent);

            myGui.setInfo("");

            // Update bidder list
            getBidders();
            if (bidders.size() == 0)
            {
                System.out.println(myAgent.getName()
                        + " - AuctionManagementBehaviour - there are no bidders, aborting");

                myGui.setInfo("No bidders. Aborting auction.");

                // Abort auction
                return;
            }

            // Get painting to auction off
            Painting painting = getRandomPainting();
            System.out.println(myAgent.getName() + " - Auctioning off painting: " + painting);

            // Inform bidders that there is an auction starting
            this.addSubBehaviour(new InformBiddersOfStartOfAuctionBehaviour(myAgent, painting));

            // Start the auction
            try
            {
                ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                cfp.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
                cfp.setConversationId("auction-" + painting.getName());
                BidRequestDTO dto = new BidRequestDTO(painting, getInitialAskingPrice(painting));
                cfp.setContentObject(dto);
                for (AID bidder : bidders)
                    cfp.addReceiver(bidder);

                this.addSubBehaviour(new DutchAuctionInitiator(myAgent, cfp));
            }
            catch (IOException ex)
            {
                System.err.println(ex);
            }
        }
    }

    private class InformBiddersOfStartOfAuctionBehaviour extends OneShotBehaviour
    {
        private Painting painting;

        public InformBiddersOfStartOfAuctionBehaviour(Agent agent, Painting painting)
        {
            super(agent);
            this.painting = painting;
        }

        @Override
        public void action()
        {
            ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
            inform.setContent("start-of-auction");
            inform.setConversationId("auction-" + painting.getName());
            for (AID bidder : bidders)
                inform.addReceiver(bidder);

            myAgent.send(inform);
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

        private ACLMessage cfp;
        private int roundCount;

        public DutchAuctionInitiator(Agent a, ACLMessage cfp)
        {
            super(a, cfp);

            this.cfp = cfp;
            this.roundCount = 0;
        }

        @Override
        protected void handleAllResponses(Vector responses, Vector acceptances)
        {
            // All responses have been received or the reply-by deadline has expired

            super.handleAllResponses(responses, acceptances);

            roundCount++;

            // Fill the acceptances vector with ACCEPT/REJECT-PROPOSAL messages

            System.out.println(myAgent.getName() + " - All responses received");
            for (int i = 0; i < responses.size(); i++)
            {
                ACLMessage response = (ACLMessage)responses.elementAt(i);
                System.out.println(myAgent.getName() + " - " + AgentHelper.getAclMessageDisplayString(response));
            }

            ACLMessage winningBid = getHighestAcceptableBid(responses);

            if (winningBid != null)
            {
                ACLMessage reply = winningBid.createReply();
                reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                acceptances.add(reply);
            }

            // Send reject proposal messages to all other bidders
            // and remove the bidders that didn't understand from the list of bidders
            for (int i = 0; i < responses.size(); i++)
            {
                ACLMessage response = (ACLMessage) responses.elementAt(i);

                if (response.getPerformative() == ACLMessage.PROPOSE && !response.equals(winningBid))
                {
                    ACLMessage reply = response.createReply();
                    reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
                    acceptances.add(reply);
                }
                else if (response.getPerformative() == ACLMessage.NOT_UNDERSTOOD)
                {
                    // Remove bidder from list of possible bidders
                    AID bidderThatDidntUnderstand = response.getSender();
                    bidders.remove(bidderThatDidntUnderstand);
                }
            }

            if (winningBid == null)
            {
                // We do not have a winner, need another iteration with lower price

                try
                {
                    BidRequestDTO oldDTO = (BidRequestDTO)cfp.getContentObject();

                    // Check if we have already gone as low as we can go
                    if (oldDTO.askingPrice == getReservePrice(oldDTO.painting))
                    {
                        // We have already reached the reserve price with no luck.
                        // Abort the auction
                        System.out.println(myAgent.getName() + " - Auction of painting \"" + oldDTO.painting.getName()
                                + "\" reached the reserve price: " + getReservePrice(oldDTO.painting)
                                + " - Aborting auction."
                        );
                        System.out.println(myAgent.getName() + " - Auction over. Number of rounds: " + roundCount);
                        myGui.setInfo("Auction failure. Number of rounds: " + roundCount);
                        return;
                    }

                    // Start the next iteration, if we have any bidders left
                    if (bidders.size() > 0)
                    {
                        // Lower the price
                        BidRequestDTO newDTO = new BidRequestDTO(
                                oldDTO.painting,
                                lowerAskingPrice(oldDTO.painting, oldDTO.askingPrice)
                        );
                        cfp.setContentObject(newDTO);

                        // Update the bidders list
                        cfp.clearAllReceiver();
                        for (AID bidder : bidders)
                            cfp.addReceiver(bidder);

                        // Set up and start next iteration
                        Vector<ACLMessage> nextIterationMessages = new Vector<>();
                        nextIterationMessages.add(cfp);
                        newIteration(nextIterationMessages);
                    }
                    else
                    {
                        System.out.println(myAgent.getName() + " - No bidders left. Aborting auction.");
                        System.out.println(myAgent.getName() + " - Auction over. Number of rounds: " + roundCount);
                        myGui.setInfo("Auction failure. Number of rounds: " + roundCount);
                    }
                }
                catch (IOException|UnreadableException ex)
                {
                    System.err.println(ex);
                }
            }
            else
            {
                System.out.println(myAgent.getName() + " - Auction over. Number of rounds: " + roundCount);
                myGui.setInfo("Auction success. Number of rounds: " + roundCount);
            }
        }

        @Override
        protected void handleInform(ACLMessage inform)
        {
            super.handleInform(inform);
            System.out.println(myAgent.getName() + " - " + AgentHelper.getAclMessageDisplayString(inform));
        }

        private int getCurrentAskingPrice()
        {
            try
            {
                BidRequestDTO dto = (BidRequestDTO)cfp.getContentObject();
                return dto.askingPrice;
            }
            catch (UnreadableException ex)
            {
                System.err.println(ex);
                return 0;
            }
        }

        private ACLMessage getHighestAcceptableBid(Vector<ACLMessage> bids)
        {
            ArrayList<ACLMessage> acceptableBids = getAcceptableBids(bids);

            int highestBid = 0;
            ACLMessage highestBidMessage = null;

            for (ACLMessage acceptableBid : acceptableBids)
            {
                int bidAmount = Integer.parseInt(acceptableBid.getContent());

                if (bidAmount > highestBid)
                {
                    highestBid = bidAmount;
                    highestBidMessage = acceptableBid;
                }
            }

            if (highestBidMessage != null)
            {
                System.out.println(myAgent.getName()
                        + " - Highest acceptable bid (winning bid): "
                        + highestBidMessage.getContent() + " from " + highestBidMessage.getSender().getName()
                );
            }
            else
            {
                System.out.println(myAgent.getName() + " - No highest acceptable bid (winning bid)");
            }

            return highestBidMessage;
        }

        private ArrayList<ACLMessage> getAcceptableBids(Vector<ACLMessage> bids)
        {
            // We accept bids that match the asking price
            // Bids will always be integers

            ArrayList<ACLMessage> acceptableBids = new ArrayList<>();

            for (int i = 0; i < bids.size(); i++)
            {
                ACLMessage bid = bids.elementAt(i);

                if (bid.getPerformative() == ACLMessage.PROPOSE)
                {
                    try
                    {
                        int bidAmount = Integer.parseInt(bid.getContent());

                        System.out.println(myAgent.getName()
                                + " - Received bid from " + bid.getSender().getName()
                                + " for " + bidAmount
                        );

                        if (bidAmount >= getCurrentAskingPrice())
                            acceptableBids.add(bid);
                    }
                    catch (NumberFormatException ex)
                    {
                        System.err.println(ex);
                    }
                }
            }

            return acceptableBids;
        }
    }

    //endregion

    private int getInitialAskingPrice(Painting painting)
    {
        return painting.getMarketValue() * 2;
    }

    private int lowerAskingPrice(Painting painting, int currentAskingPrice)
    {
        int reservePrice = getReservePrice(painting);
        int newPrice = (int)(currentAskingPrice * 0.9);
        if (newPrice < reservePrice)
            newPrice = reservePrice;

        return newPrice;
    }

    /**
     * Get the reserve price, the lowest that the auctioneer is willing to go
     * */
    private int getReservePrice(Painting painting)
    {
        return (int)(painting.getMarketValue() * 1.1);
    }

    private Painting getRandomPainting()
    {
        ArrayList<Painting> paintings = AgentHelper.generatePaintings();
        return paintings.get(ThreadLocalRandom.current().nextInt(paintings.size()));
    }
}
