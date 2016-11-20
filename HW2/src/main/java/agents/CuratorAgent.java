package agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.ContractNetResponder;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class CuratorAgent extends Agent
{
    protected void setup()
    {
        registerCuratorServices();
        addBehaviour(new WaitForAuction());
        System.out.println("CuratorAgent " + getAID().getName() + " is ready.");
    }

    protected void takeDown()
    {
        deregisterCuratorServices();
        System.out.println("CuratorAgent " + getAID().getName() + " terminating.");
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
            // TODO We should send the painting details over here too
            // and decide how much we are willing to pay, and maybe refuse also
            String askingPrice = cfp.getContent();
            System.out.println("Received asking price: " + askingPrice);

            ACLMessage reply = cfp.createReply();

            try
            {
                int iAskingPrice = Integer.parseInt(askingPrice);
                reply.setPerformative(ACLMessage.PROPOSE);
                int randomBidAmount = ThreadLocalRandom.current().nextInt(iAskingPrice, iAskingPrice*2+1);
                reply.setContent(String.valueOf(randomBidAmount));
            }
            catch (NumberFormatException ex)
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
    }

    //endregion
}
