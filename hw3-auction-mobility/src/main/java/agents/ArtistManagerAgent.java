package agents;

import DTOs.BidRequestDTO;
import artifacts.Painting;
import gui.ArtistManagerAgentGui;
import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.content.onto.basic.Result;
import jade.core.AID;
import jade.core.Agent;
import jade.core.Location;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.JADEAgentManagement.QueryPlatformLocationsAction;
import jade.domain.JADEAgentManagement.WhereIsAgentAction;
import jade.domain.mobility.CloneAction;
import jade.domain.mobility.MobileAgentDescription;
import jade.domain.mobility.MobilityOntology;
import jade.domain.mobility.MoveAction;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.ContractNetInitiator;
import jade.wrapper.StaleProxyException;
import mobility.MobileAgent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.ThreadLocalRandom;

public class ArtistManagerAgent extends MobileAgent
{
    private DFAgentDescription bidderServiceTemplate;
    private ArrayList<AID> biddersInSameContainer;
    private ArrayList<AID> clones;
    private Painting paintingToAuction;
    private AID originalParent;
    private Location originalParentLocation;

    private ACLMessage auctionWinningBid;

    protected void setup()
    {
        // NOTE: This gets run only in the original agent, not the clones

        super.setup();

        this.biddersInSameContainer = new ArrayList<>();
        this.clones = new ArrayList<>();
        this.paintingToAuction = getRandomPainting();
        // Set ourselves as the parent, our clones will have access to this field
        this.originalParent = getAID();
        this.originalParentLocation = here();

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

    public boolean isClone()
    {
        return !this.getAID().equals(this.originalParent);
    }

    public void startAuction()
    {
        System.out.println(this.getName() + " - Starting auction");
        ((ArtistManagerAgentGui)myGui).setStartAuctionButtonEnabled(false);
        this.auctionWinningBid = null;
        this.addBehaviour(new AuctionManagementBehaviour(this));
    }

    public void startAuctionInClones()
    {
        if (clones.size() > 0)
        {
            System.out.println(getName() + " - Sending \"start auction\" message to clones");
            addBehaviour(new StartAuctionInClonesBehaviour(this));
            System.out.println(getName() + " - Listening for auction results from clones");
            addBehaviour(new GetAuctionResultFromClonesCyclicBehaviour(this, clones));
        }
        else
        {
            System.out.println(getName() + " - There are no clones. Aborting start auction in clones.");
        }
    }

    public void moveCloneToOriginalContainerAndReportAuctionResult()
    {
        if (here() != this.originalParentLocation)
        {
            // the clone is not in the same location as it's original parent
            // lets move it there before it can report its auction result
            myGui.setInfo("Moving to original container and reporting auction result");

            System.out.println("Moving to parent location: " + this.originalParentLocation.toString());
            doMove(destination = this.originalParentLocation);
        }

        addBehaviour(new ReportAuctionResultOneShot());
    }

    private void getBiddersInSameContainer()
    {
        ArrayList<AID> allBidders = getBidders();
        ArrayList<AID> biddersInSameContainer = new ArrayList<>();

        for (AID bidder : allBidders)
        {
            ArrayList<Location> bidderLocations = getAgentLocations(bidder);
            if (bidderLocations.contains(here()))
                biddersInSameContainer.add(bidder);
        }

        System.out.println(getName() + " - Found " + biddersInSameContainer.size() + " bidders in same container");
        this.biddersInSameContainer = biddersInSameContainer;
    }

    /**
     * Search the DF for bidders
     */
    private ArrayList<AID> getBidders()
    {
        ArrayList<AID> foundBidders = new ArrayList<>();

        try
        {
            DFAgentDescription[] result = DFService.search(this, this.bidderServiceTemplate);
            for (int i = 0; i < result.length; ++i)
            {
                foundBidders.add(result[i].getName());
            }
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }

        System.out.println(getName() + " - Found " + foundBidders.size() + " bidders in total");
        return foundBidders;
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
            getBiddersInSameContainer();
            if (biddersInSameContainer.size() == 0)
            {
                System.out.println(myAgent.getName()
                        + " - AuctionManagementBehaviour - there are no bidders (in same container), aborting");

                myGui.setInfo("No bidders. Aborting auction.");
                moveCloneToOriginalContainerAndReportAuctionResult();

                // Abort auction
                return;
            }

            System.out.println(myAgent.getName() + " - Auctioning off painting: " + paintingToAuction);

            // Inform bidders that there is an auction starting
            this.addSubBehaviour(new InformBiddersOfStartOfAuctionBehaviour(myAgent, paintingToAuction));

            // Start the auction
            try
            {
                ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                cfp.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
                cfp.setConversationId("auction-" + paintingToAuction.getName());
                BidRequestDTO dto = new BidRequestDTO(paintingToAuction, getInitialAskingPrice(paintingToAuction));
                cfp.setContentObject(dto);
                for (AID bidder : biddersInSameContainer)
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
            for (AID bidder : biddersInSameContainer)
                inform.addReceiver(bidder);

            myAgent.send(inform);
        }
    }

    private class ReportAuctionResultOneShot extends OneShotBehaviour
    {
        public ReportAuctionResultOneShot()
        {
            super();
        }

        @Override
        public void action()
        {
            try
            {
                System.out.println(myAgent.getLocalName() + " - Reporting auction result");

                ACLMessage reportMsg = new ACLMessage(ACLMessage.INFORM);

                if (auctionWinningBid != null)
                {
                    System.out.println("Winning bid is: " + auctionWinningBid.getContent().toString());
                    reportMsg.setConversationId("auction-" + paintingToAuction.getName() + "-auctionresult-success");
                    reportMsg.setContentObject(auctionWinningBid);
                }
                else
                {
                    System.out.println("Got no acceptable bids");
                    reportMsg.setConversationId("auction-" + paintingToAuction.getName() + "-auctionresult-failure");
                    reportMsg.setContent("auction-failed");
                }

                reportMsg.addReceiver(originalParent);
                myAgent.send(reportMsg);
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
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
                auctionWinningBid = winningBid;
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
                    biddersInSameContainer.remove(bidderThatDidntUnderstand);
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
                        moveCloneToOriginalContainerAndReportAuctionResult();
                        return;
                    }

                    // Start the next iteration, if we have any bidders left
                    if (biddersInSameContainer.size() > 0)
                    {
                        // Lower the price
                        BidRequestDTO newDTO = new BidRequestDTO(
                                oldDTO.painting,
                                lowerAskingPrice(oldDTO.painting, oldDTO.askingPrice)
                        );
                        cfp.setContentObject(newDTO);

                        // Update the bidders list
                        cfp.clearAllReceiver();
                        for (AID bidder : biddersInSameContainer)
                            cfp.addReceiver(bidder);

                        // Set up and start next iteration
                        Vector<ACLMessage> nextIterationMessages = new Vector<>();
                        nextIterationMessages.add(cfp);
                        newIteration(nextIterationMessages);
                    }
                    else
                    {
                        // auction failure
                        System.out.println(myAgent.getName() + " - No bidders left. Aborting auction.");
                        System.out.println(myAgent.getName() + " - Auction over. Number of rounds: " + roundCount);
                        myGui.setInfo("Auction failure. Number of rounds: " + roundCount);
                        moveCloneToOriginalContainerAndReportAuctionResult();
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

                int bidAmount = Integer.parseInt(auctionWinningBid.getContent());
                System.out.println(myAgent.getLocalName() + " got a winning bid: " + bidAmount);
                myGui.setInfo(myAgent.getLocalName() + " got a winning bid: " + bidAmount);
                moveCloneToOriginalContainerAndReportAuctionResult();
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

    private class StartAuctionInClonesBehaviour extends OneShotBehaviour
    {
        private ArtistManagerAgent agent;

        public StartAuctionInClonesBehaviour(ArtistManagerAgent a)
        {
            super(a);
            this.agent = a;
        }

        @Override
        public void action()
        {
            ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
            inform.setConversationId("start-auction-" + paintingToAuction.getName());
            for (AID clone : clones)
                inform.addReceiver(clone);

            myAgent.send(inform);
        }
    }

    private class GetAuctionResultFromClonesCyclicBehaviour extends CyclicBehaviour
    {
        private ArrayList<AID> clonesYetToSendTheirAuctionResult;
        private ArrayList<ACLMessage> winningBids;

        public GetAuctionResultFromClonesCyclicBehaviour(ArtistManagerAgent a, ArrayList<AID> clonesYetToSendTheirAuctionResult)
        {
            super(a);
            this.clonesYetToSendTheirAuctionResult = clonesYetToSendTheirAuctionResult;
            this.winningBids = new ArrayList<>();
        }

        @Override
        public void action()
        {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.or(
                            MessageTemplate.MatchConversationId("auction-" + paintingToAuction.getName() + "-auctionresult-success"),
                            MessageTemplate.MatchConversationId("auction-" + paintingToAuction.getName() + "-auctionresult-failure")
                    )
            );
            ACLMessage msg = this.myAgent.receive(mt);

            if (msg != null)
            {
                if (msg.getConversationId().equals("auction-" + paintingToAuction.getName() + "-auctionresult-success"))
                {
                    // This is a winning PROPOSE message

                    try
                    {
                        ACLMessage winningBid = (ACLMessage) msg.getContentObject();

                        System.out.println(myAgent.getName()
                                + " - Received winning bid message from clone " + msg.getSender().getName()
                                + " - [winner, bid] - [" + winningBid.getSender().getName() + ", " + winningBid.getContent() + "]"
                        );

                        // Save the winning bid
                        this.winningBids.add(winningBid);
                    }
                    catch (UnreadableException ex)
                    {
                        System.err.println(ex);
                        System.out.println(myAgent.getName() + " - Unknown auction result - " + msg);
                    }
                }
                else if (msg.getConversationId().equals("auction-" + paintingToAuction.getName() + "-auctionresult-failure"))
                {
                    // This is an auction failed message

                    System.out.println(myAgent.getName()
                            + " - Received auction failed message from clone " + msg.getSender().getName()
                            + " - " + msg.getContent()
                    );
                }
                else
                {
                    System.out.println(myAgent.getName() + " - Unknown auction result - " + msg);
                }

                // Remove the sender from our list of clones that have yet to send us their auction result
                this.clonesYetToSendTheirAuctionResult.remove(msg.getSender());

                if (this.clonesYetToSendTheirAuctionResult.isEmpty())
                {
                    selectBestWinningBidAndProcessIt();

                    // We have received all messages that we are expecting, remove this cyclic behaviour from agent
                    myAgent.removeBehaviour(this);
                }
            }
            else
            {
                block();
            }
        }

        private void selectBestWinningBidAndProcessIt()
        {
            int highestBid = -1;
            ACLMessage bestWinningBid = null;

            for (ACLMessage winningBid : this.winningBids)
            {
                try
                {
                    int bid = Integer.parseInt(winningBid.getContent());
                    if (bid > highestBid)
                    {
                        highestBid = bid;
                        bestWinningBid = winningBid;
                    }
                }
                catch (NumberFormatException ex)
                {
                    System.err.println(ex);
                }
            }

            if (bestWinningBid != null)
            {
                System.out.println(myAgent.getName()
                        + " - Best winning bid from clones  - [winner, bid] - ["
                        + bestWinningBid.getSender().getName() + ", " + bestWinningBid.getContent() + "]");

                acceptBestWinningBid(bestWinningBid);
            }
            else
            {
                System.out.println(myAgent.getName() + " - Could not find best winning bid from clones");
            }
        }

        private void acceptBestWinningBid(ACLMessage bestWinningBid)
        {
            // Send ACCEPT_PROPOSAL to the winner
            ACLMessage reply = bestWinningBid.createReply();
            reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
            myAgent.send(reply);
        }
    }

    private class WaitForStartAuctionRequest extends CyclicBehaviour
    {
        public WaitForStartAuctionRequest()
        {
            super();
        }

        @Override
        public void action()
        {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchConversationId("start-auction-" + paintingToAuction.getName())),
                    MessageTemplate.MatchSender(originalParent)
            );
            ACLMessage msg = this.myAgent.receive(mt);

            if (msg != null)
            {
                startAuction();
                myAgent.removeBehaviour(this);
            }
            else
            {
                block();
            }
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

    /**
     * Get agent locations (containers) from AMS
     * */
    private ArrayList<Location> getAgentLocations(AID agentAID)
    {
        ArrayList<Location> locations = new ArrayList<>();

        try
        {
            // Set up action
            WhereIsAgentAction whereIsAgentAction = new WhereIsAgentAction();
            whereIsAgentAction.setAgentIdentifier(agentAID);
            Action action = new Action(getAMS(), whereIsAgentAction);

            // Set up request message
            ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
            request.setLanguage(new SLCodec().getName());
            request.setOntology(MobilityOntology.getInstance().getName());

            // Send request message
            getContentManager().fillContent(request, action);
            request.addReceiver(action.getActor());
            send(request);

            // Receive response
            MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchSender(getAMS()),
                MessageTemplate.MatchPerformative(ACLMessage.INFORM)
            );
            ACLMessage resp = blockingReceive(mt);

            // Process response
            ContentElement ce = getContentManager().extractContent(resp);
            Result result = (Result)ce;
            jade.util.leap.Iterator it = result.getItems().iterator();
            while (it.hasNext())
            {
                Location loc = (Location) it.next();
                locations.add(loc);
            }
        }
        catch (Codec.CodecException|OntologyException ex)
        {
            System.err.println(ex);
        }

        return locations;
    }

    @Override
    public void doClone(Location destination, String newName)
    {
        super.doClone(destination, newName);

        // Save the clone
        AID cloneAID = new AID(newName, AID.ISLOCALNAME);
        clones.add(cloneAID);
        System.out.println(getName() + " - Clones: " + clones);
    }

    @Override
    protected void beforeClone()
    {
        // This gets run on the agent that is being cloned

        super.beforeClone();
        //System.out.println(getName() + " - BEFORE clone - Original parent: " + originalParent + " - Painting: " + paintingToAuction);
    }

    @Override
    protected void afterClone()
    {
        // This gets run on the clones

        super.afterClone();
        // Clear the clone list we got from our parent. We want to have our own clone list, with just our clones.
        clones.clear();
        System.out.println(getName() + " - AFTER clone - Original parent: " + originalParent + " - Painting: " + paintingToAuction);

        if (isClone())
        {
            // Disable the "start auction" button for clones
            // (they will start the auction when they receive a message from the original parent telling them to do so)
            ((ArtistManagerAgentGui) myGui).setStartAuctionButtonEnabled(false);

            // Disable the "start auction in clones" button for clones
            ((ArtistManagerAgentGui) myGui).setStartAuctionInClonesButtonEnabled(false);

            // Disable the "set up other agents" button for clones
            ((ArtistManagerAgentGui) myGui).setSetupOtherAgentsButtonEnabled(false);

            // wait for start auction
            addBehaviour(new WaitForStartAuctionRequest());
        }
    }

    @Override
    protected void afterMove()
    {
        super.afterMove();

        // Have to re-hide the appropriate buttons since the GUI resets when you move an agent
        if (isClone())
        {
            // Disable the "start auction" button for clones
            // (they will start the auction when they receive a message from the original parent telling them to do so)
            ((ArtistManagerAgentGui) myGui).setStartAuctionButtonEnabled(false);

            // Disable the "start auction in clones" button for clones
            ((ArtistManagerAgentGui) myGui).setStartAuctionInClonesButtonEnabled(false);

            // Disable the "set up other agents" button for clones
            ((ArtistManagerAgentGui) myGui).setSetupOtherAgentsButtonEnabled(false);
        }
    }

    //region Set up other agents - clone and move automation

    public void setupOtherAgents()
    {
        System.out.println(getName() + " - Setting up other agents");

        final String CURATOR_AGENT_NAME = "CuratorAgent";
        final String ARTISTMANAGER_AGENT_NAME = "ArtistManagerAgent";

        AID controllerAgentAID = getControllerAgentAID();
        if (controllerAgentAID == null)
        {
            System.out.println(getName() + " - Could not find controller agent. Aborting setup of other agents.");
            return;
        }

        HashMap<String, Location> locations = getAvailableLocationsFromAMS();
        Location container2 = locations.get("Container-2");
        Location container3 = locations.get("Container-3");

        // Clone myself twice, to container 2 and 3
        String selfClone0Name = getCloneName(ARTISTMANAGER_AGENT_NAME, 0, 0);
        String selfClone1Name = getCloneName(ARTISTMANAGER_AGENT_NAME, 0, 1);
        cloneAgent(getAID().getLocalName(), selfClone0Name, container2);
        cloneAgent(getAID().getLocalName(), selfClone1Name, container3);

        // Add two curator agents
        String newCurator0Name = CURATOR_AGENT_NAME+0;
        String newCurator1Name = CURATOR_AGENT_NAME+1;
        createCuratorAgent(controllerAgentAID, newCurator0Name);
        createCuratorAgent(controllerAgentAID, newCurator1Name);

        // Move the new curator agents to their appropriate containers
        // The first one to container 2, the second one to container 3
        moveAgent(newCurator0Name, container2);
        moveAgent(newCurator1Name, container3);

        // Update controller agent GUI
        ArrayList<String> newAgentNames = new ArrayList<>();
        newAgentNames.add(selfClone0Name);
        newAgentNames.add(selfClone1Name);
        newAgentNames.add(newCurator0Name);
        newAgentNames.add(newCurator1Name);
        sendAgentAddedInformMessageToControllerAgent(controllerAgentAID, newAgentNames);
    }

    private void cloneAgent(String oldName, String newName, Location destination)
    {
        AID aid = new AID(oldName, AID.ISLOCALNAME);

        MobileAgentDescription mad = new MobileAgentDescription();
        mad.setName(aid);
        mad.setDestination(destination);
        CloneAction ca = new CloneAction();
        ca.setNewName(newName);
        ca.setMobileAgentDescription(mad);
        sendMobilityRequest(new Action(aid, ca));
    }

    private void createCuratorAgent(AID controllerAgentAID, String newCuratorName)
    {
        try
        {
            Object[] args = new Object[2];
            // HACK: Faking it as if it is the controller agent that is creating the agent.
            // Necessary for the MobileAgent implementation.
            args[0] = controllerAgentAID;
            jade.wrapper.AgentController ac =
                    getContainerController().createNewAgent(newCuratorName, CuratorAgent.class.getName(), args);
            ac.start();
        }
        catch (StaleProxyException ex)
        {
            System.err.println(getName() + " - Could not create new agent - " + ex);
        }
    }

    private void moveAgent(String agentName, Location destination)
    {
        AID aid = new AID(agentName, AID.ISLOCALNAME);
        MobileAgentDescription mad = new MobileAgentDescription();
        mad.setName(aid);
        mad.setDestination(destination);
        MoveAction ma = new MoveAction();
        ma.setMobileAgentDescription(mad);
        sendMobilityRequest(new Action(aid, ma));
    }

    private HashMap<String, Location> getAvailableLocationsFromAMS()
    {
        HashMap<String, Location> locationsMap = new HashMap<>();

        // Send request to AMS
        sendMobilityRequest(new Action(getAMS(), new QueryPlatformLocationsAction()));

        // Receive response from AMS
        MessageTemplate mt = MessageTemplate.and(
                MessageTemplate.MatchSender(getAMS()),
                MessageTemplate.MatchPerformative(ACLMessage.INFORM));
        ACLMessage resp = blockingReceive(mt);

        try
        {
            ContentElement ce = getContentManager().extractContent(resp);
            Result result = (Result) ce;
            jade.util.leap.Iterator it = result.getItems().iterator();
            while (it.hasNext())
            {
                Location loc = (Location)it.next();
                locationsMap.put(loc.getName(), loc);
            }
        }
        catch (Codec.CodecException|OntologyException ex)
        {
            System.err.println(ex);
        }

        return locationsMap;
    }

    private void sendMobilityRequest(Action action)
    {
        ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
        request.setLanguage(new SLCodec().getName());
        request.setOntology(MobilityOntology.getInstance().getName());
        request.setConversationId("artistmanageragent-setup-other-agents");
        try
        {
            getContentManager().fillContent(request, action);
            request.addReceiver(action.getActor());
            send(request);
        }
        catch (Exception ex)
        {
            System.err.println(ex);
        }
    }

    /**
     * Send notification to controller agent to get him to update his GUI.
     * NOTE: This is a function and not a behaviour since the behaviour would get added to the newly
     * created clones as well.
     */
    private void sendAgentAddedInformMessageToControllerAgent(AID controllerAgentAID, ArrayList<String> addedAgentNames)
    {
        try
        {
            ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
            inform.setConversationId("new-agents-notification");
            inform.setContentObject(addedAgentNames);
            inform.addReceiver(controllerAgentAID);
            this.send(inform);
        }
        catch (IOException ex)
        {
            System.err.println(ex);
        }
    }

    private AID getControllerAgentAID()
    {
        try
        {
            // Find controller agent

            DFAgentDescription controllerAgentDescription = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType(ServiceList.SRVC_CONTROLLER_TYPE);
            sd.setName(ServiceList.SRVC_CONTROLLER_NAME);
            controllerAgentDescription.addServices(sd);

            DFAgentDescription[] result = DFService.search(this, controllerAgentDescription);
            if (result.length != 1)
            {
                System.out.println(this.getName()
                        + " - ERROR: Found " + result.length
                        + " controller agents. Expecting 1.");
            }
            else
            {
                return result[0].getName();
            }
        }
        catch (FIPAException fe)
        {
            System.err.println(fe);
        }

        return null;
    }

    private String getCloneName(String agentTypeName, int agentNumber, int cloneNumber)
    {
        return "Clone-" + agentTypeName + agentNumber + "-" + cloneNumber;
    }

    //endregion
}
