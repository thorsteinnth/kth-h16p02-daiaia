import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SenderBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.*;

/**
 * Tour Guide Agent: Registers a
 */
public class TourGuideAgent extends Agent
{
    /**
     * A space separated string of the tour guide specialities, e.g. "paintings sculptures buildings"
     */
    private String specialities;
    private DFAgentDescription dfCuratorServiceTemplate;
    private AID curatorAgent;

    protected void setup()
    {
        this.specialities = getTourGuideSpecialities();

        registerTourGuideService();

        //Create the DF template to find the curator agent
        this.dfCuratorServiceTemplate = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(AppConstants.SRVC_CURATOR_GET_ARTIFACTS_FOR_INTEREST_TYPE);
        sd.setName(AppConstants.SRVC_CURATOR_GET_ARTIFACTS_FOR_INTEREST_NAME);
        this.dfCuratorServiceTemplate.addServices(sd);

        //add behavior to listen to virtual-tour requests from profiler agent
        addBehaviour(new VirtualTourServer());

        System.out.println("TourGuideAgent " + getAID().getName() + " is ready with specialities: " + specialities);
    }

    protected void takeDown()
    {
        //Do necessary clean up here
        deregisterTourGuideService();
        System.out.println("TourGuideAgent " + getAID().getName() + " terminating.");
    }

    // Returns a space separated string of the tour guide specialities, e.g. "paintings sculptures buildings"
    private String getTourGuideSpecialities()
    {
        String specialities = "";

        Random random = new Random();

        switch (random.nextInt(4))
        {
            case 0:
                specialities = "paintings sculptures";
                break;
            case 1:
                specialities = "buildings";
                break;
            case 2:
                specialities = "paintings items";
                break;
            case 3:
                specialities = "sculptures buildings items";
                break;
        }

        return specialities;
    }


    // Registers the virtual tour-guide service in the yellow pages
    private void registerTourGuideService()
    {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType(AppConstants.SRVC_TOUR_GUIDE_VIRTUAL_TOUR_GUIDE_TYPE);
        sd.setName(AppConstants.SRVC_TOUR_GUIDE_VIRTUAL_TOUR_GUIDE_NAME);
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
    // Deregister the virtual tour-guide service from the yellow pages
    private void deregisterTourGuideService()
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
                String mutualInterests = getMutualInterests(interests);

                ACLMessage reply = msg.createReply();

                addBehaviour(
                        new GetArtifacts(
                                mutualInterests,
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
                                                reply.setPerformative(ACLMessage.PROPOSE);
                                                reply.setContent(String.valueOf(artifactsCount));

                                                System.out.println(myAgent.getLocalName() +
                                                        " is sending artifacts count: "
                                                        + String.valueOf(artifactsCount));

                                                addBehaviour(new SenderBehaviour(myAgent, reply));
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

        // Returns a space separated string of interests that the guide specialises in and the profiler is interested in
        private String getMutualInterests(String profilerInterests)
        {
            String result = "";

            Set<String> profilerInt = new HashSet<>(Arrays.asList(profilerInterests.split(" ")));
            Set<String> tourGuideSpes = new HashSet<>(Arrays.asList(specialities.split(" ")));

            Set<String> intersect = new HashSet<>(profilerInt);
            intersect.retainAll(tourGuideSpes);

            for (String interest: intersect)
            {
                result += " " + interest;
            }

            System.out.println("Found intersect for tourGuide " + myAgent.getName() + " :" + result);

            return result;
        }
    }

    private class SendVirtualTour extends OneShotBehaviour
    {
        private ArrayList<Artifact> artifacts;
        private ACLMessage msg;

        public SendVirtualTour(ArrayList<Artifact> artifacts, ACLMessage reply)
        {
            this.artifacts = artifacts;
            this.msg = reply;
        }

        public void action()
        {
            System.out.println(myAgent.getLocalName() + " is sending Virtual-Tour");

            try
            {
                this.msg.setPerformative(ACLMessage.INFORM);

                ArrayList<ArtifactHeader> artifactHeaders = new ArrayList<>();

                for(Artifact artifact : artifacts)
                {
                    artifactHeaders.add(new ArtifactHeader(artifact.getId(), artifact.getName()));
                }

                this.msg.setContentObject(artifactHeaders);
            }
            catch (Exception ex)
            {
                this.msg.setPerformative(ACLMessage.FAILURE);
                ex.printStackTrace();
            }

            this.myAgent.send(msg);
        }
    }

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

                    // Start by finding the curator agent (there should only be one)
                    if(!getCurator())
                    {
                        step = 2;
                        break;
                    }

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
                        // Reply received

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

    private boolean getCurator()
    {
        try
        {
            DFAgentDescription[] result = DFService.search(this, this.dfCuratorServiceTemplate);

            if(result.length > 0)
            {
                System.out.println(getLocalName() + ": Found curator agent: ");
                this.curatorAgent = result[0].getName();
                System.out.println(curatorAgent.getName());

                return true;
            }
            else
            {
                System.out.println("Could not find curator agent");
            }
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }

        return false;
    }


    private interface ArtifactCallback
    {
        void onGetArtifacts(ArrayList<Artifact> artifacts);
    }

}
