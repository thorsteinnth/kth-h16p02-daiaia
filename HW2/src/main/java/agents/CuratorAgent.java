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

public class CuratorAgent extends Agent
{
    protected void setup()
    {
        registerCuratorServices();

        this.addBehaviour(
                new BidRequestResponder(
                        this,
                        MessageTemplate.and(
                                MessageTemplate.MatchProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION),
                                MessageTemplate.MatchConversationId("auction")
                        )
                )
        );

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

    private class BidRequestResponder extends ContractNetResponder
    {
        public BidRequestResponder(Agent agent, MessageTemplate mt)
        {
            super(agent, mt);
        }

        @Override
        protected ACLMessage handleCfp(ACLMessage cfp) throws RefuseException, FailureException, NotUnderstoodException
        {
            String content = cfp.getContent();
            System.out.println("Received content in responder: " + content);

            ACLMessage reply = cfp.createReply();
            reply.setPerformative(ACLMessage.PROPOSE);
            reply.setContent("100");

            return reply;
        }
    }

    /* Old, non-fipa implementation
    private class BidServer extends CyclicBehaviour
    {
        public void action()
        {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.CFP),
                    MessageTemplate.MatchConversationId("request-bid")
            );

            ACLMessage msg = myAgent.receive(mt);

            if (msg != null)
            {
                // Request for bid received, handle it

                ACLMessage reply = msg.createReply();

                String content = msg.getContent();
                System.out.println("Received content: " + content);

                reply.setPerformative(ACLMessage.PROPOSE);
                reply.setContent("100");

                myAgent.send(reply);
            }
            else
            {
                block();
            }
        }
    }
    */

    //endregion
}
