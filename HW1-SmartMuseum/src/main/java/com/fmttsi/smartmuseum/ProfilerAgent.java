import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;
import jade.proto.states.MsgReceiver;

import java.util.ArrayList;
import java.util.Iterator;

public class ProfilerAgent extends Agent
{
    // Request virtual tours from tour guide agents
    // Get detailed info on objects in tour from curator agent

    private String age;
    private String occupation;
    /**
     * A space separated string of interests, e.g. "paintings sculptures buildings"
     */
    private String interests;
    private ArrayList<Artifact> visitedItems;

    private DFAgentDescription dfTourGuideServiceTemplate;
    private ArrayList<AID> tourGuides;
    private DataStore tourReplyDataStore;

    // TODO Get curator from DF
    private AID curatorAgent = new AID("curatorAgent", AID.ISLOCALNAME);

    //region Setup and takeDown

    protected void setup()
    {
        Object[] args = getArguments();
        if (args != null && args.length == 3)
        {
            this.age = (String)args[0];
            this.occupation = ((String)args[1]).replace("-", " ");
            this.interests = ((String)args[2]).replace("-", " ");
        }
        else
        {
            System.out.println("ProfilerAgent: Need command line arguments on the form (age,occupation,interests) " +
                    "where different words in the occupation and different interests are separated by \"-\". " +
                    "Example: (27,software-engineer,paintings-sculptures-buildings)");

            // Terminate agent
            doDelete();
        }

        this.visitedItems = new ArrayList<>();
        this.tourGuides = new ArrayList<>();
        this.tourReplyDataStore = new DataStore();

        // Create the DF tour-guide template
        this.dfTourGuideServiceTemplate = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("virtual-tour-guide");
        sd.setName("Virtual-Tour guide");
        this.dfTourGuideServiceTemplate.addServices(sd);

        // Let's request tours every 10 seconds
        this.addBehaviour(new TourRequestTicker(this, 10000));

        System.out.println("ProfilerAgent " + getAID().getName() + " is ready: " + this.toString());
    }

    protected void takeDown()
    {
        // Do necessary clean up here
        System.out.println("ProfilerAgent " + getAID().getName() + " terminating.");
    }

    //endregion

    //region Behaviours

    private class TourRequestTicker extends TickerBehaviour
    {
        public TourRequestTicker(ProfilerAgent agent, long timeout)
        {
            super(agent, timeout);
        }

        public void onTick()
        {
            // Clear the reply data store from the last iteration
            ((ProfilerAgent)myAgent).tourReplyDataStore.clear();
            // Update available tour guide list
            getAvailableTourGuides();
            if (tourGuides.size() == 0)
            {
                System.out.println(myAgent.getName() + ": No known tour guides. Aborting...");
                return;
            }
            // Request tours
            myAgent.addBehaviour(new TourRequestSequentialBehaviour((ProfilerAgent)myAgent));
        }
    }

    private class TourRequestSequentialBehaviour extends SequentialBehaviour
    {
        public TourRequestSequentialBehaviour(ProfilerAgent agent)
        {
            super(agent);
            this.addSubBehaviour(new RequestTourOffersParallelBehaviour(agent));
            this.addSubBehaviour(new ReceiveTourOffersParallelBehaviour(agent));
            this.addSubBehaviour(new SelectBestTourOfferAndAcceptItBehaviour(agent));
            this.addSubBehaviour(new ReceiveTourBehaviour(agent));
        }
    }

    //region Request tour offers behaviours

    private class RequestTourOffersParallelBehaviour extends ParallelBehaviour
    {
        public RequestTourOffersParallelBehaviour(ProfilerAgent agent)
        {
            // Set owner agent and terminate when all children are done
            super(agent, WHEN_ALL);

            // Request tours from all tour guides
            for (AID tourGuide : tourGuides)
            {
                this.addSubBehaviour(new RequestTourOfferFromTourGuideBehaviour(agent, tourGuide));
            }
        }
    }

    private class RequestTourOfferFromTourGuideBehaviour extends OneShotBehaviour
    {
        private AID tourGuide;

        public RequestTourOfferFromTourGuideBehaviour(ProfilerAgent agent, AID tourGuide)
        {
            super(agent);
            this.tourGuide = tourGuide;
        }

        public void action()
        {
            System.out.println("Requesting tour from tour guide: " + this.tourGuide.getName());

            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            cfp.addReceiver(this.tourGuide);
            cfp.setContent(interests);
            cfp.setConversationId("tour-offer-request");
            myAgent.send(cfp);
        }
    }

    //endregion

    //region Receive tour offers behaviours

    private class ReceiveTourOffersParallelBehaviour extends ParallelBehaviour
    {
        public ReceiveTourOffersParallelBehaviour(ProfilerAgent agent)
        {
            // Set owner agent and terminate when all children are done
            super(agent, WHEN_ALL);

            // Receive tour offer from all known tour guides
            for (AID tourGuide : tourGuides)
            {
                this.addSubBehaviour(new ReceiveTourOfferFromTourGuideBehaviour(agent, tourGuide));
            }
        }
    }

    private class ReceiveTourOfferFromTourGuideBehaviour extends MsgReceiver
    {
        public ReceiveTourOfferFromTourGuideBehaviour(ProfilerAgent agent, AID tourGuide)
        {
            // From documentation:
            // Put into the given key of the given datastore the received message according
            // to the given message template and timeout.
            // If the timeout expires before any message arrives,
            // the behaviour terminates and put null into the datastore.

            super(agent,
                    MessageTemplate.and(
                            MessageTemplate.MatchConversationId("tour-offer-request"),
                            MessageTemplate.MatchSender(tourGuide)
                    ),
                    MsgReceiver.INFINITE,
                    agent.tourReplyDataStore,
                    tourGuide.getName()
            );
        }
    }

    //endregion

    //region Get best tour behaviours

    private class SelectBestTourOfferAndAcceptItBehaviour extends OneShotBehaviour
    {
        public SelectBestTourOfferAndAcceptItBehaviour(ProfilerAgent agent)
        {
            super(agent);
        }

        public void action()
        {
            ACLMessage bestOffer = selectBestTourOffer();

            if (bestOffer == null)
            {
                System.out.println(getAgent().getName() + " - could not select best offer");
                return;
            }

            ACLMessage acceptMessage = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            acceptMessage.addReceiver(bestOffer.getSender());
            acceptMessage.setContent(interests);
            acceptMessage.setConversationId("tour-offer-request-acceptance");
            myAgent.send(acceptMessage);
        }
    }

    private class ReceiveTourBehaviour extends MsgReceiver
    {
        public ReceiveTourBehaviour(ProfilerAgent agent)
        {
            // NOTE:
            // We don't use the data store here, just the handleMessage() function
            // Just using an arbitrary DataStore and key

            // TODO: Do we need to have the message template more explicit?
            super(agent,
                    MessageTemplate.and(
                            MessageTemplate.MatchConversationId("tour-offer-request-acceptance"),
                            MessageTemplate.MatchPerformative(ACLMessage.INFORM)
                    ),
                    MsgReceiver.INFINITE,
                    new DataStore(),
                    "key"
            );
        }

        @Override
        protected void handleMessage(ACLMessage msg)
        {
            super.handleMessage(msg);

            if (msg.getPerformative() == ACLMessage.INFORM)
            {
                // Tour received (list of artifact headers)
                System.out.println("Received tour from agent: " + msg.getSender().getName());

                ArrayList<ArtifactHeader> artifactHeaders;

                try
                {
                    artifactHeaders = (ArrayList<ArtifactHeader>)msg.getContentObject();
                }
                catch (UnreadableException ex)
                {
                    System.err.println(ex.toString());
                    artifactHeaders = new ArrayList<>();
                }

                // Print out virtual tour headers
                System.out.println();
                System.out.println("The virtual tour (headers):");
                for (ArtifactHeader header : artifactHeaders)
                {
                    System.out.println(header.getId() + " - " + header.getName());
                }
                System.out.println();

                // Get artifact details
                myAgent.addBehaviour(new GetArtifactDetails(artifactHeaders));
            }
            else
            {
                System.err.println("Unexpected ACL message performative: " + msg.getPerformative());
            }
        }
    }

    private class GetArtifactDetails extends Behaviour
    {
        private ArrayList<ArtifactHeader> artifacts;
        private ArrayList<Artifact> receivedArtifactsWithDetails;
        private int step;
        private int sentRequestCount;
        private int receivedResponseCount;
        private MessageTemplate mt;

        public GetArtifactDetails(ArrayList<ArtifactHeader> artifacts)
        {
            this.artifacts = artifacts;
            this.receivedArtifactsWithDetails = new ArrayList<>();
            this.step = 0;
            this.sentRequestCount = 0;
            this.receivedResponseCount = 0;
        }

        public void action()
        {
            switch (step)
            {
                case 0:

                    for (ArtifactHeader artifact : this.artifacts)
                    {
                        ACLMessage cfp = new ACLMessage(ACLMessage.REQUEST);
                        cfp.addReceiver(curatorAgent);
                        cfp.setContent(String.valueOf(artifact.getId()));
                        cfp.setConversationId("get-artifact-details");

                        myAgent.send(cfp);
                        this.sentRequestCount++;
                    }

                    this.mt = MessageTemplate.MatchConversationId("get-artifact-details");

                    step = 1;

                    break;

                case 1:

                    // Receive all requested artifact details

                    ACLMessage reply = myAgent.receive(this.mt);

                    if (reply != null)
                    {
                        if (reply.getPerformative() == ACLMessage.INFORM)
                        {
                            try
                            {
                                Artifact receivedArtifact = (Artifact)reply.getContentObject();
                                this.receivedArtifactsWithDetails.add(receivedArtifact);
                            }
                            catch (UnreadableException ex)
                            {
                                System.err.println("ProfilerAgent.GetArtifactDetails.action(): " + ex.toString());
                            }

                        }
                        this.receivedResponseCount++;

                        if (this.receivedResponseCount >= this.sentRequestCount)
                        {
                            this.step = 2;

                            // Print artifacts with details
                            System.out.println("Received artifact details:");
                            for (Artifact artifact : this.receivedArtifactsWithDetails)
                                System.out.println(artifact.toString());
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
            return this.step == 2;
        }
    }

    //endregion

    //endregion

    private void getAvailableTourGuides()
    {
        ArrayList<AID> foundTourGuides = new ArrayList<>();

        try
        {
            DFAgentDescription[] result = DFService.search(this, this.dfTourGuideServiceTemplate);
            for (int i = 0; i < result.length; ++i)
            {
                foundTourGuides.add(result[i].getName());
            }

            System.out.println(getName() + " - Found " + foundTourGuides.size() + " tour guides");

            this.tourGuides = foundTourGuides;
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
    }

    private ACLMessage selectBestTourOffer()
    {
        int maxNumberOfInterestingObjects = 0;
        ACLMessage bestOffer = null;

        Iterator it = tourReplyDataStore.values().iterator();
        while (it.hasNext())
        {
            ACLMessage msg = (ACLMessage)it.next();
            int numberOfInterestingObjects = Integer.parseInt(msg.getContent());
            if (numberOfInterestingObjects > maxNumberOfInterestingObjects)
            {
                bestOffer = msg;
                maxNumberOfInterestingObjects = numberOfInterestingObjects;
            }
        }

        return bestOffer;
    }

    @Override
    public String toString()
    {
        return "ProfilerAgent{" +
                "age='" + age + '\'' +
                ", occupation='" + occupation + '\'' +
                ", interests='" + interests + '\'' +
                ", visitedItems=" + visitedItems +
                ", dfTourGuideServiceTemplate=" + dfTourGuideServiceTemplate +
                ", tourGuides=" + tourGuides +
                ", tourReplyDataStore=" + tourReplyDataStore +
                ", curatorAgent=" + curatorAgent +
                '}';
    }

    //region Old behaviours not in use

    /*
    private class TourRequestPerformer extends Behaviour
    {
        private MessageTemplate mt; // The template to receive replies
        private int bestTourNumberOfInterestingObjects;
        private AID bestTourGuide;
        private int repliesCount;
        private int step = 0;

        // Will be run repeatedly until done() returns true
        public void action()
        {
            switch (step)
            {
                case 0:

                    // Send tour requests

                    System.out.println(myAgent.getAID().getName()
                            + " Tour request entered step 0");

                    //Update the list of all tour-guide agents available
                    try
                    {
                        DFAgentDescription[] result = DFService.search(myAgent, dfTourGuideServiceTemplate);
                        System.out.println("Found the following tour-guide agents:");
                        tourGuideAgents = new AID[result.length];
                        for (int i = 0; i < result.length; ++i)
                        {
                            tourGuideAgents[i] = result[i].getName();
                            System.out.println(tourGuideAgents[i].getName());
                        }
                    }
                    catch (FIPAException fe)
                    {
                        fe.printStackTrace();
                    }

                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);

                    for (int i = 0; i < tourGuideAgents.length; ++i)
                    {
                        cfp.addReceiver(tourGuideAgents[i]);
                    }

                    cfp.setContent(interests);
                    cfp.setConversationId("tour-offer-request");
                    cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique value

                    myAgent.send(cfp);

                    // Prepare the template to get proposals
                    mt = MessageTemplate.and(
                            MessageTemplate.MatchConversationId("tour-offer-request"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith())
                    );

                    step = 1;

                    break;

                case 1:

                    // Get tour request replies and find the best one

                    System.out.println(myAgent.getAID().getName()
                            + " Tour request entered step 1");

                    ACLMessage reply = myAgent.receive(mt);

                    if (reply != null)
                    {
                        // Reply received
                        repliesCount++;

                        if (reply.getPerformative() == ACLMessage.PROPOSE)
                        {
                            // This is an offer

                            int numberOfInterestingObjects = Integer.parseInt(reply.getContent());
                            if (bestTourGuide == null || numberOfInterestingObjects > bestTourNumberOfInterestingObjects)
                            {
                                // This is the best offer at present
                                bestTourNumberOfInterestingObjects = numberOfInterestingObjects;
                                bestTourGuide = reply.getSender();
                            }
                        }

                        if (repliesCount >= tourGuideAgents.length)
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

                case 2:

                    // Accept the best tour request

                    System.out.println(myAgent.getAID().getName() + " Tour request entered step 2");

                    ACLMessage acceptMessage = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    acceptMessage.addReceiver(bestTourGuide);
                    acceptMessage.setContent(interests);
                    acceptMessage.setConversationId("tour-offer-request-acceptance");
                    acceptMessage.setReplyWith("acceptMessage" + System.currentTimeMillis());

                    myAgent.send(acceptMessage);

                    mt = MessageTemplate.and(
                            MessageTemplate.MatchConversationId("tour-offer-request-acceptance"),
                            MessageTemplate.MatchInReplyTo(acceptMessage.getReplyWith())
                    );

                    step = 3;

                    break;

                case 3:

                    System.out.println(myAgent.getAID().getName() + " Tour request entered step 3");

                    // Receive tour (artifact headers)

                    reply = myAgent.receive(mt);

                    if (reply != null)
                    {
                        if (reply.getPerformative() == ACLMessage.INFORM)
                        {
                            // Tour received (list of artifact headers)
                            System.out.println("Received tour from agent: " + reply.getSender().getName());
                            System.out.println("Number of interesting objects: " + bestTourNumberOfInterestingObjects);

                            ArrayList<ArtifactHeader> artifacts;

                            try
                            {
                                artifacts = (ArrayList<ArtifactHeader>) reply.getContentObject();
                            }
                            catch (UnreadableException ex)
                            {
                                System.err.println(ex.toString());
                                artifacts = new ArrayList<>();
                            }

                            // Print out virtual tour
                            System.out.println();
                            System.out.println("The virtual-tour:");

                            for (ArtifactHeader artifact : artifacts)
                            {
                                System.out.println(artifact.getId() + " - " + artifact.getName());
                            }
                            System.out.println();

                            myAgent.addBehaviour(new GetArtifactDetails(artifacts));
                        }
                        else
                        {
                            System.out.println("Unexpected ACL message performative: " + reply.getPerformative());
                        }

                        step = 4;
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
            return step == 4;
        }
    }
    */

    //endregion
}
