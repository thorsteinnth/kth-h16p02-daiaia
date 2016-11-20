package agents;

import DTOs.BidRequestDTO;
import artifacts.Painting;
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

import java.util.ArrayList;
import java.util.Random;

public class CuratorAgent extends Agent
{
    private enum BiddingStrategy
    {
        PASSIVE,
        AGGRESSIVE
    }

    private ArrayList<Painting.SubjectMatter> subjectMatterInterests;
    private ArrayList<Painting.PaintingMedium> paintingMediumInterests;
    private BiddingStrategy biddingStrategy;

    protected void setup()
    {
        // Get command line arguments
        Object[] args = getArguments();
        if (args != null && args.length == 1)
        {
            String biddingStrategy = (String)args[0];

            if(biddingStrategy.equals("passive"))
            {
                this.biddingStrategy = BiddingStrategy.PASSIVE;
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
            invalidCommandLineArguments();
            return;
        }

        registerCuratorServices();
        addBehaviour(new WaitForAuction());
        getPaintingInterests();
        System.out.println("CuratorAgent " + getAID().getName() + " is ready.");
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

    private void registerCuratorServices()
    {
        ServiceDescription bidderService = new ServiceDescription();
        bidderService.setName(ServiceList.SRVC_CURATOR_BIDDER_NAME);
        bidderService.setType(ServiceList.SRVC_CURATOR_BIDDER_TYPE);

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
            DFService.deregister(this);
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
    }

    private void getPaintingInterests()
    {
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
    }

    //region Behaviours

    /**
     * A Cycle behaviour that waits for a "start-of-auction" INFORM message, picks up the conversation id
     * for the auction and then adds a BidRequestResponderBehaviour to participate in the auction
     */
    private class WaitForAuction extends CyclicBehaviour
    {
        public void action()
        {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
            ACLMessage msg = this.myAgent.receive(mt);

            if(msg != null)
            {
                String content = msg.getContent();

                if(content.equals("start-of-auction"))
                {
                    String conversationId = msg.getConversationId();

                    addBehaviour(
                            new BidRequestResponder(
                                    myAgent,
                                    MessageTemplate.and(
                                            MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION),
                                            MessageTemplate.MatchConversationId(conversationId)
                                    )
                            )
                    );
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
        public BidRequestResponder(Agent agent, MessageTemplate mt)
        {
            super(agent, mt);
        }

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) throws RefuseException, FailureException, NotUnderstoodException
        {
            ACLMessage reply = cfp.createReply();

            try
            {
                BidRequestDTO dto = (BidRequestDTO) cfp.getContentObject();

                double paintingInterestFactor = getPaintingInterestFactorForAgent(dto.painting);
                double amountWillingToPay = dto.painting.getMarketValue() * paintingInterestFactor;

                System.out.println(myAgent.getName()
                        + " - Received asking price for painting " + dto.painting.getName() + ": " + dto.askingPrice
                        + " - Willing to pay: " + amountWillingToPay
                );

                if ((double)dto.askingPrice <= amountWillingToPay)
                {
                    // I am willing to pay the asking price or higher,
                    // let's bid the asking price
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(String.valueOf(dto.askingPrice));
                }
                else
                {
                    // I am not willing to pay the asking price
                    // Let's refuse the CFP
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("Asking price too high");
                }
            }
            catch (UnreadableException|NumberFormatException ex)
            {
                System.err.println(ex);
                reply.setPerformative(ACLMessage.NOT_UNDERSTOOD);
                reply.setContent("not understood");
            }

            return reply;
        }

        @Override
        protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) throws FailureException
        {
            System.out.println(myAgent.getName() + " - Received accept proposal: " + accept);
            ACLMessage reply = accept.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setContent("Thank you.");
            return reply;
        }

        @Override
        protected void handleRejectProposal(ACLMessage cfp, ACLMessage propose, ACLMessage reject)
        {
            System.out.println(myAgent.getName() + " - Received reject proposal: " + reject);
        }

        private double getPaintingInterestFactorForAgent(Painting painting)
        {
            double strategyFactor = 0;

            if(biddingStrategy.equals(BiddingStrategy.PASSIVE))
            {
                strategyFactor = 0.2;
            }
            else if(biddingStrategy.equals(BiddingStrategy.AGGRESSIVE))
            {
                strategyFactor = 0.25;
            }

            double interestFactor = 1;

            if(subjectMatterInterests.contains(painting.getSubjectMatter()))
            {
                interestFactor += strategyFactor;
            }

            if(paintingMediumInterests.contains(painting.getMedium()))
            {
                interestFactor += strategyFactor;
            }

            return interestFactor;
        }
    }

    //endregion
}
