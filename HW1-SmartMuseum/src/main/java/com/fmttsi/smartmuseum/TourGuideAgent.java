import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.StringACLCodec;

import java.util.ArrayList;

public class TourGuideAgent extends Agent
{
    // TODO: Find all Curators from DF
    private AID curatorAgent = new AID("curatorAgent", AID.ISLOCALNAME);

    protected void setup()
    {
        System.out.println("TourGuideAgent " + getAID().getName() + " is ready.");

        //add behavior to listen to virtual-tour requests from profiler agent
        addBehaviour(new VirtualTourServer());
    }

    protected void takeDown()
    {
        //Do necessary clean up here
        System.out.println("TourGuideAgent " + getAID().getName() + " terminating.");
    }

    private class VirtualTourServer extends CyclicBehaviour
    {
        public void action()
        {
            MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.CFP),
                    MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL));
            ACLMessage msg = myAgent.receive(mt);

            if(msg != null)
            {
                System.out.println(msg.getSender().getLocalName()
                        + " wants to get information on artifacts for " + msg.getContent());

                String interests = msg.getContent();
                ACLMessage reply = msg.createReply();

                //send content number of artifacts
                addBehaviour(
                        new GetArtifacts(
                                interests,
                                new ArtifactCallback()
                                {
                                    @Override
                                    public void onGetArtifacts(ArrayList<Artifact> artifacts)
                                    {
                                        if(artifacts != null)
                                        {
                                            if(msg.getPerformative() == ACLMessage.CFP)
                                            {
                                                int artifactsCount = artifacts.size();

                                                addBehaviour(new SendArtifactsCount(artifactsCount, reply));
                                            }
                                            else if(msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL)
                                            {
                                                addBehaviour(new SendVirtualTour(artifacts, reply));
                                            }
                                        }
                                        else
                                        {
                                            reply.setPerformative(ACLMessage.REFUSE);
                                            myAgent.send(reply);
                                        }
                                    }
                                }
                        )
                );

            }
            else
            {
                block();
            }
        }
    }

    private class SendArtifactsCount extends OneShotBehaviour
    {
        private int artifactsCount;
        private ACLMessage reply;

        public SendArtifactsCount(int artifactsCount, ACLMessage reply)
        {
            this.artifactsCount = artifactsCount;
            this.reply = reply;
        }

        public void action()
        {
            System.out.println(myAgent.getLocalName() + " is sending artifacts count: "
                    + String.valueOf(artifactsCount));
            // Send the number of artifacts to the profiler
            this.reply.setPerformative(ACLMessage.PROPOSE);
            this.reply.setContent(String.valueOf(artifactsCount));
            this.myAgent.send(reply);
        }
    }

    private class SendVirtualTour extends OneShotBehaviour
    {
        private ArrayList<Artifact> artifacts;
        private ACLMessage reply;

        public SendVirtualTour(ArrayList<Artifact> artifacts, ACLMessage reply)
        {
            this.artifacts = artifacts;
            this.reply = reply;
        }

        public void action()
        {
            System.out.println(myAgent.getLocalName() + " is sending Virtual-Tour");

            try
            {
                this.reply.setPerformative(ACLMessage.INFORM);
                this.reply.setContentObject(artifacts); //TODO : Format artifacts for profiler
            }
            catch (Exception ex)
            {
                this.reply.setPerformative(ACLMessage.FAILURE);
                ex.printStackTrace();
            }

            this.myAgent.send(reply);
        }
    }

    // TODO : For now we assume that the tour guide agent only knows of one curator agent
    private class GetArtifacts extends Behaviour
    {
        private String interests;
        private ArtifactCallback delegate;
        private MessageTemplate mt;
        private int step;

        public GetArtifacts(String interests, ArtifactCallback delegate)
        {
            this.interests = interests;
            this.delegate = delegate;
            this.step = 0;
        }

        public void action()
        {
            switch (this.step)
            {
                case 0:
                    // Send request to curator agent to get list of artifacts for given interests
                    ACLMessage getListOfArtifactsMsg = new ACLMessage(ACLMessage.REQUEST);
                    getListOfArtifactsMsg.addReceiver(curatorAgent);
                    getListOfArtifactsMsg.setLanguage("English");
                    getListOfArtifactsMsg.setContent(interests);
                    getListOfArtifactsMsg.setConversationId("get-artifacts-for-interest");
                    getListOfArtifactsMsg.setReplyWith("get-artifacts" + System.currentTimeMillis());
                    myAgent.send(getListOfArtifactsMsg);

                    this.mt = MessageTemplate.and(
                            MessageTemplate.MatchConversationId("get-artifacts-for-interest"),
                            MessageTemplate.MatchInReplyTo(getListOfArtifactsMsg.getReplyWith())
                    );

                    step = 1;
                    break;
                case 1:
                    // Receive list of artifacts from curator agent
                    ACLMessage reply = myAgent.receive(this.mt);

                    if(reply != null)
                    {
                        if(reply.getPerformative() == ACLMessage.REFUSE)
                        {
                            //No artifacts available
                            if(this.delegate != null)
                                this.delegate.onGetArtifacts(null);
                        }
                        else
                        {
                            try
                            {
                                if(this.delegate != null)
                                {
                                    ArrayList<Artifact> artifacts = (ArrayList<Artifact>)reply.getContentObject();
                                    this.delegate.onGetArtifacts(artifacts);
                                }
                            }
                            catch(Exception ex)
                            {
                                ex.printStackTrace();
                            }
                        }

                        step = 2;
                    }
                    else
                    {
                        block();
                    }
                    break;
            }
        }

        public boolean done()
        {
            return step == 2;
        }
    }


    private interface ArtifactCallback
    {
        void onGetArtifacts(ArrayList<Artifact> artifacts);
    }

}
