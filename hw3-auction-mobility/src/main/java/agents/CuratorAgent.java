package agents;

import DTOs.BidRequestDTO;
import artifacts.Painting;
import gui.CuratorAgentGui;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.ContractNetResponder;
import mobility.MobileAgent;

import java.util.ArrayList;
import java.util.Random;

public class CuratorAgent extends MobileAgent
{
    private enum BiddingStrategy
    {
        PASSIVE,
        MEDIUM,
        AGGRESSIVE
    }

    private ArrayList<Painting.SubjectMatter> subjectMatterInterests;
    private ArrayList<Painting.PaintingMedium> paintingMediumInterests;
    private ArrayList<String> artistInterests;
    private BiddingStrategy biddingStrategy;
    private BidRequestResponder bidRequestResponder;

    public String getStrategy()
    {
        return biddingStrategy.toString();
    }

    protected void setup()
    {
        super.setup();

        // Get command line arguments
        Object[] args = getArguments();
        if (args != null && args.length == 1)
        {
            String biddingStrategy = (String)args[0];

            if(biddingStrategy.equals("passive"))
            {
                this.biddingStrategy = BiddingStrategy.PASSIVE;
            }
            else if(biddingStrategy.equals("medium"))
            {
                this.biddingStrategy = BiddingStrategy.MEDIUM;
            }
            else if(biddingStrategy.equals("aggressive"))
            {
                this.biddingStrategy = BiddingStrategy.AGGRESSIVE;
            }
            else
            {
                invalidCommandLineArguments();
                return;
            }
        }
        else
        {
            // default to all passive
            this.biddingStrategy = BiddingStrategy.PASSIVE;
        }

        ((CuratorAgentGui)myGui).setStrategy(this.biddingStrategy.toString());

        registerCuratorServices();
        addBehaviour(new WaitForAuction(this));
        getPaintingInterests();
        System.out.println("CuratorAgent " + getAID().getName() + " is ready. Strategy: " + this.biddingStrategy);
    }

    protected void takeDown()
    {
        deregisterCuratorServices();
        System.out.println("CuratorAgent " + getAID().getName() + " terminating.");
    }

    private void invalidCommandLineArguments()
    {
        System.out.println("CuratorAgent: Need command line arguments on the form (biddingStrategy) " +
                "where bidding strategy can be either passive or aggressive " +
                "Example: (aggressive)");

        // Terminate agent
        doDelete();
    }

    protected void beforeMove() {
// -----------------------------

        super.beforeMove();
    }

    protected void afterMove() {
// ----------------------------

        super.afterMove();

    }

    protected void beforeClone() {
// -----------------------------

        super.beforeClone();
    }

    protected void afterClone() {
// ----------------------------

        super.afterClone();

        // randomly generate new interests for the clone
        this.resetPaintingInterests();
        this.getPaintingInterests();

        ((CuratorAgentGui)myGui).setStrategy(this.biddingStrategy.toString());
    }

    private void registerCuratorServices()
    {
        ServiceDescription bidderService = new ServiceDescription();

        String serviceName = ServiceList.SRVC_CURATOR_BIDDER_NAME; // + "-" + destination.getName()
        String serviceType = ServiceList.SRVC_CURATOR_BIDDER_TYPE;

        System.out.println("Registering curator service at: " + serviceName);

        bidderService.setName(serviceName);
        bidderService.setType(serviceType);

        DFAgentDescription agentDescription = new DFAgentDescription();
        agentDescription.setName(getAID());
        agentDescription.addServices(bidderService);

        try
        {
            DFService.register(this, agentDescription);
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
    }

    private void deregisterCuratorServices()
    {
        try
        {
            DFService.deregister(this, getAID());
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
    }

    private void resetPaintingInterests()
    {
        this.subjectMatterInterests = new ArrayList<>();
        this.paintingMediumInterests = new ArrayList<>();
    }

    private void getPaintingInterests()
    {
        // Add subject matter and painting medium interests

        this.subjectMatterInterests = new ArrayList<>();
        this.paintingMediumInterests = new ArrayList<>();

        Random random = new Random();
        switch (random.nextInt(8))
        {
            case 0:
                this.subjectMatterInterests.add(Painting.SubjectMatter.Portrait);
                this.subjectMatterInterests.add(Painting.SubjectMatter.Abstract);
                this.paintingMediumInterests.add(Painting.PaintingMedium.Oil);
                break;
            case 1:
                this.subjectMatterInterests.add(Painting.SubjectMatter.Landscape);
                this.paintingMediumInterests.add(Painting.PaintingMedium.Oil);
                break;
            case 2:
                this.subjectMatterInterests.add(Painting.SubjectMatter.Religious);
                this.paintingMediumInterests.add(Painting.PaintingMedium.Pastel);
                break;
            case 3:
                this.subjectMatterInterests.add(Painting.SubjectMatter.Portrait);
                this.subjectMatterInterests.add(Painting.SubjectMatter.StillLife);
                this.paintingMediumInterests.add(Painting.PaintingMedium.Fresco);
                break;
            case 4:
                this.subjectMatterInterests.add(Painting.SubjectMatter.StillLife);
                this.paintingMediumInterests.add(Painting.PaintingMedium.Pastel);
                break;
            case 5:
                this.subjectMatterInterests.add(Painting.SubjectMatter.Abstract);
                this.subjectMatterInterests.add(Painting.SubjectMatter.Landscape);
                this.paintingMediumInterests.add(Painting.PaintingMedium.Acrylic);
                this.paintingMediumInterests.add(Painting.PaintingMedium.Fresco);
                break;
            case 6:
                this.subjectMatterInterests.add(Painting.SubjectMatter.Portrait);
                this.subjectMatterInterests.add(Painting.SubjectMatter.Religious);
                this.paintingMediumInterests.add(Painting.PaintingMedium.Oil);
                this.paintingMediumInterests.add(Painting.PaintingMedium.Pastel);
                break;
            case 7:
                this.subjectMatterInterests.add(Painting.SubjectMatter.StillLife);
                this.paintingMediumInterests.add(Painting.PaintingMedium.Watercolor);
                this.paintingMediumInterests.add(Painting.PaintingMedium.Fresco);
                break;
        }

        // Add artist interests

        this.artistInterests = new ArrayList<>();

        ArrayList<String> artists = AgentHelper.getAllArtists();
        String randomArtist;

        // Let's make him interested in up to 2 artists
        randomArtist = artists.get(random.nextInt(artists.size()));
        if (!this.artistInterests.contains(randomArtist))
            this.artistInterests.add(randomArtist);
        randomArtist = artists.get(random.nextInt(artists.size()));
        if (!this.artistInterests.contains(randomArtist))
            this.artistInterests.add(randomArtist);

        ((CuratorAgentGui)myGui).setInterests(
                this.subjectMatterInterests.toString() + " " + this.paintingMediumInterests.toString());
    }

    //region Behaviours

    /**
     * A Cycle behaviour that waits for a "start-of-auction" INFORM message, picks up the conversation id
     * for the auction and then adds a BidRequestResponderBehaviour to participate in the auction
     */
    private class WaitForAuction extends CyclicBehaviour
    {
        private CuratorAgent agent;

        public WaitForAuction(CuratorAgent a)
        {
            super(a);
            this.agent = a;
        }

        public void action()
        {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = this.myAgent.receive(mt);

            if (msg != null)
            {
                String content = msg.getContent();

                if (content.equals("start-of-auction"))
                {
                    String conversationId = msg.getConversationId();

                    System.out.println(myAgent.getName() + " - Received start of auction message with conversation ID: "
                            + conversationId);

                    if (agent.bidRequestResponder != null)
                    {
                        // This is not the first auction.
                        // Remove the previous responder behaviour.
                        // (this agent does not necessarily know that the previous auction is over)
                        // (can only partake in one auction at a time by doing it like this)
                        // (this is a hack, auctioneer should send an inform message to all
                        // participants when an auction is over, and they would then remove their
                        // BidRequestResponder behaviours)
                        removeBehaviour(agent.bidRequestResponder);
                    }

                    agent.bidRequestResponder = new BidRequestResponder(
                            (CuratorAgent)myAgent,
                            MessageTemplate.and(
                                    MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION),
                                    MessageTemplate.MatchConversationId(conversationId)
                            )
                    );

                    addBehaviour(agent.bidRequestResponder);
                }
            }
            else
            {
                block();
            }
        }
    }

    private class BidRequestResponder extends ContractNetResponder
    {
        private CuratorAgent agent;

        public BidRequestResponder(CuratorAgent agent, MessageTemplate mt)
        {
            super(agent, mt);
            this.agent = agent;
        }

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) throws RefuseException, FailureException, NotUnderstoodException
        {
            ACLMessage reply = cfp.createReply();

            try
            {
                BidRequestDTO dto = (BidRequestDTO) cfp.getContentObject();

                double strategyMultiplier = getStrategyMultiplier(dto.painting);
                double amountWillingToPay = dto.painting.getMarketValue() * strategyMultiplier;

                System.out.println(myAgent.getName()
                        + " - Received asking price for painting " + dto.painting.getName() + ": " + dto.askingPrice
                        + " - Willing to pay: " + (int)amountWillingToPay
                        + " - Strategy multiplier: " + strategyMultiplier
                );

                if ((double)dto.askingPrice <= amountWillingToPay)
                {
                    // I am willing to pay the asking price or higher,
                    // let's bid the asking price
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(String.valueOf(dto.askingPrice));
                    agent.myGui.setInfo("Bid placed: " + dto.askingPrice);
                }
                else
                {
                    // I am not willing to pay the asking price
                    // Let's refuse the CFP
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("Asking price too high");
                    agent.myGui.setInfo("Bid request refused");
                }
            }
            catch (UnreadableException|NumberFormatException ex)
            {
                System.err.println(ex);
                reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                reply.setContent("not understood");
                agent.myGui.setInfo("Not understood");
            }

            return reply;
        }

        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException
        {
            System.out.println(myAgent.getName() + " - " + AgentHelper.getAclMessageDisplayString(accept));
            ACLMessage reply = accept.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent("Thank you.");

            System.out.println(myAgent.getName() + " - Won the auction. Strategy: " + biddingStrategy);

            agent.myGui.setInfo("Bid accepted - winner");

            return reply;
        }

        @Override
        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject)
        {
            System.out.println(myAgent.getName() + " - " + AgentHelper.getAclMessageDisplayString(reject));
            agent.myGui.setInfo("Bid rejected - loser");
        }

        private double getStrategyMultiplier(Painting painting)
        {
            double interestFactor = 1;
            double interestIncrement = 0.3;

            if (subjectMatterInterests.contains(painting.getSubjectMatter()))
            {
                interestFactor += interestIncrement;
            }

            if (paintingMediumInterests.contains(painting.getMedium()))
            {
                interestFactor += interestIncrement;
            }

            if (artistInterests.contains(painting.getArtist()))
            {
                interestFactor += interestIncrement;
            }

            double strategyFactor = 1;

            if (biddingStrategy.equals(BiddingStrategy.PASSIVE))
            {
                // Do nothing
            }
            else if (biddingStrategy.equals(BiddingStrategy.MEDIUM))
            {
                strategyFactor = 1.2;
            }
            else if (biddingStrategy.equals(BiddingStrategy.AGGRESSIVE))
            {
                strategyFactor = 1.4;
            }

            return interestFactor * strategyFactor;
        }
    }

    //endregion
}
