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

    private DFAgentDescription dfCuratorServiceTemplate;
    private AID curatorAgent;

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

        //Create the DF template to find the curator agent
        this.dfCuratorServiceTemplate = new DFAgentDescription();
        ServiceDescription curatorSD = new ServiceDescription();
        curatorSD.setType("get-artifact-details");
        curatorSD.setName("name-get-artifact-details");
        this.dfCuratorServiceTemplate.addServices(curatorSD);

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
        // TODO Call behaviours from here, don't have them nested
        public TourRequestSequentialBehaviour(ProfilerAgent agent)
        {
            super(agent);
            this.addSubBehaviour(new RequestToursParallelBehaviour(agent));
            this.addSubBehaviour(new ReceiveToursParallelBehaviour(agent));
            this.addSubBehaviour(new SelectBestTourAndRequestItBehaviour(agent));
        }
    }

    //region Request tour behaviours

    private class RequestToursParallelBehaviour extends ParallelBehaviour
    {
        public RequestToursParallelBehaviour(ProfilerAgent agent)
        {
            // Set owner agent and terminate when all children are done
            super(agent, WHEN_ALL);

            // Request tours from all tour guides
            for (AID tourGuide : tourGuides)
            {
                this.addSubBehaviour(new RequestTourFromTourGuideBehaviour(agent, tourGuide));
            }
        }
    }

    private class RequestTourFromTourGuideBehaviour extends OneShotBehaviour
    {
        private AID tourGuide;

        public RequestTourFromTourGuideBehaviour(ProfilerAgent agent, AID tourGuide)
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

    //region Receive tour behaviours

    private class ReceiveToursParallelBehaviour extends ParallelBehaviour
    {
        public ReceiveToursParallelBehaviour(ProfilerAgent agent)
        {
            // Set owner agent and terminate when all children are done
            super(agent, WHEN_ALL);

            // Receive tour from all known tour guides
            for (AID tourGuide : tourGuides)
            {
                this.addSubBehaviour(new ReceiveTourFromTourGuideBehaviour(agent, tourGuide));
            }
        }
    }

    private class ReceiveTourFromTourGuideBehaviour extends MsgReceiver
    {
        public ReceiveTourFromTourGuideBehaviour(ProfilerAgent agent, AID tourGuide)
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

    //region Select best tour behaviours

    private class SelectBestTourAndRequestItBehaviour extends OneShotBehaviour
    {
        public SelectBestTourAndRequestItBehaviour(ProfilerAgent agent)
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

            // Add a nested behaviour to receive
            // TODO: Probably better to not do it like this
            myAgent.addBehaviour(new ReceiveTourBehaviour((ProfilerAgent)myAgent, bestOffer.getSender()));
        }
    }

    private class ReceiveTourBehaviour extends MsgReceiver
    {
        public ReceiveTourBehaviour(ProfilerAgent agent, AID tourGuide)
        {
            // NOTE: We don't use the data store here, just the handleMessage() function
            // Just using an arbitrary DataStore and key

            super(agent,
                    MessageTemplate.and(
                            MessageTemplate.MatchConversationId("tour-offer-request-acceptance"),
                            MessageTemplate.MatchSender(tourGuide)
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
                    artifactHeaders = (ArrayList<ArtifactHeader>) msg.getContentObject();
                }
                catch (UnreadableException ex)
                {
                    System.err.println(ex.toString());
                    artifactHeaders = new ArrayList<>();
                }

                // Print out virtual tour
                System.out.println();
                System.out.println("The virtual tour:");
                for (ArtifactHeader header : artifactHeaders)
                {
                    System.out.println(header.getId() + " - " + header.getName());
                }
                System.out.println();

                // TODO Don't use nested behaviours
                myAgent.addBehaviour(new GetArtifactDetails(artifactHeaders));
            }
            else
            {
                System.err.println("Unexpected ACL message performative: " + msg.getPerformative());
            }
        }
    }

    //endregion

    // TODO Update to use a higher level subclass behaviour
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

                    System.out.println(myAgent.getAID().getName() + " Get artifact details entered step 0");

                    // Start by finding the curator, if we cannot find we break from the loop
                    if(!getCurator())
                    {
                        step = 2;
                        break;
                    }

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
                                // TODO Verify that this works
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
                            System.out.println("Received artifact details: " + this.receivedArtifactsWithDetails.toString());
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

            System.out.println(getLocalName() + ": Found the following tour-guide agents:");
            for (AID tourGuide : foundTourGuides)
                System.out.println(tourGuide);

            this.tourGuides = foundTourGuides;
        }
        catch (FIPAException fe)
        {
            fe.printStackTrace();
        }
    }

    private boolean getCurator()
    {
        try
        {
            DFAgentDescription[] result = DFService.search(this, this.dfCuratorServiceTemplate);

            if(result.length > 0)
            {
                System.out.println(getLocalName() + ": Found curator agent:");
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

    private ACLMessage selectBestTourOffer()
    {
        int maxNumberOfInterestingObjects = 0;
        ACLMessage bestOffer = null;

        Iterator it = tourReplyDataStore.values().iterator();
        while (it.hasNext())
        {
            if (it.next() == null)
                continue;

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
