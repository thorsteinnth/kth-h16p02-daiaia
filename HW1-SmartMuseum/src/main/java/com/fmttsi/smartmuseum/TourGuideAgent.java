import com.sun.tools.javac.util.Pair;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.StringACLCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TourGuideAgent extends Agent
{
    private DFAgentDescription dfCuratorServiceTemplate;
    private AID[] curatorAgents;

    protected void setup()
    {
        System.out.println("TourGuideAgent " + getAID().getName() + " is ready.");

        RegisterTourGuideService();

        //Create the DF curator templete
        this.dfCuratorServiceTemplate = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("get-artifacts");
        sd.setName("get-artifacts-by-interest-and-id");
        this.dfCuratorServiceTemplate.addServices(sd);

        //add behavior to listen to virtual-tour requests from profiler agent
        addBehaviour(new VirtualTourServer());
    }

    protected void takeDown()
    {
        //Do necessary clean up here
        DeregisterTourGuideService();
        System.out.println("TourGuideAgent " + getAID().getName() + " terminating.");
    }

    // Registers the tour-guide service in the yellow pages
    private void RegisterTourGuideService()
    {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("virtual-tour-guide");
        sd.setName("Virtual-Tour guide");
        dfd.addServices(sd);
        try
        {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
    }
    // Deregister the tour-guide service from the yellow pages
    private void DeregisterTourGuideService()
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
            // TODO: Also send curator agent for profiler to contact for details
            System.out.println(myAgent.getLocalName() + " is sending Virtual-Tour");

            try
            {
                this.reply.setPerformative(ACLMessage.INFORM);

                ArrayList<ArtifactHeader> artifactHeaders = new ArrayList<>();

                for(Artifact artifact : artifacts)
                {
                    artifactHeaders.add(new ArtifactHeader(artifact.getId(), artifact.getName()));
                }

                this.reply.setContentObject(artifactHeaders);
            }
            catch (Exception ex)
            {
                this.reply.setPerformative(ACLMessage.FAILURE);
                ex.printStackTrace();
            }

            this.myAgent.send(reply);
        }
    }

    private class GetArtifacts extends Behaviour
    {
        private String interests;
        private ArtifactCallback delegate;
        private MessageTemplate mt;
        private int repliesCount;
        private int step;

        // TODO : Create list of lists of artifacts from all curators

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
                    // Start by update the list of all curator agents available
                    try
                    {
                        DFAgentDescription[] result = DFService.search(myAgent, dfCuratorServiceTemplate);
                        System.out.println("Found the following curator agents:");
                        curatorAgents = new AID[result.length];
                        for (int i = 0; i < result.length; ++i)
                        {
                            curatorAgents[i] = result[i].getName();
                            System.out.println(curatorAgents[i].getName());
                        }
                    }
                    catch (FIPAException fe)
                    {
                        fe.printStackTrace();
                    }

                    // Send request to curator agent to get list of artifacts for given interests
                    ACLMessage getListOfArtifactsMsg = new ACLMessage(ACLMessage.REQUEST);

                    for (int i = 0; i < curatorAgents.length; ++i)
                    {
                        getListOfArtifactsMsg.addReceiver(curatorAgents[i]);
                    }

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
                        // Reply received
                        repliesCount++;

                        //TODO the delegate should return list of list of artifacts

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

                        if (repliesCount >= curatorAgents.length)
                        {
                            // We received all replies
                            step = 2;
                        }
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
