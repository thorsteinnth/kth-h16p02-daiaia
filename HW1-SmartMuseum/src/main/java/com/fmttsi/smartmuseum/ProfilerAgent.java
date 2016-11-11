import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

import java.util.ArrayList;

public class ProfilerAgent extends Agent
{
    // Requests virtual tour from tour guide agent
    // Get detailed info on objects in tour from curator agent

    private int age;
    private String occupation;
    private ArrayList<String> interests;
    private ArrayList<Artifact> visitedItems;

    // TODO Get agents from DF
    private AID tourGuideAgent = new AID("tourGuideAgent", AID.ISLOCALNAME);
    private AID curatorAgent = new AID("curatorAgent", AID.ISLOCALNAME);

    //region Setup and takeDown

    protected void setup()
    {
        // TODO Get info from command line arguments
        this.age = 27;
        this.occupation = "Software engineer";
        this.interests = new ArrayList<>();
        this.interests.add("paintings");
        this.interests.add("sculptures");

        this.visitedItems = new ArrayList<>();

        this.addBehaviour(new TourRequestPerformer());
        // Let's make him request a new tour every 10 seconds
        this.addBehaviour(new TourRequestTicker(this, 10000));

        System.out.println("ProfilerAgent " + getAID().getName() + " is ready.");
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
        public TourRequestTicker(Agent agent, long timeout)
        {
            super(agent, timeout);
        }

        public void onTick()
        {
            myAgent.addBehaviour(new TourRequestPerformer());
        }
    }

    private class TourRequestPerformer extends Behaviour
    {
        private MessageTemplate mt; // The template to receive replies
        private int bestTourNumberOfInterestingObjects;
        private AID bestTourGuide;
        private int repliesCount;
        private int sentRequestCount;
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

                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    cfp.addReceiver(tourGuideAgent);
                    cfp.setContent(getInterestString());
                    cfp.setConversationId("tour-offer-request");
                    cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique value

                    myAgent.send(cfp);
                    sentRequestCount = 1;

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

                        if (repliesCount >= sentRequestCount)
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
                    acceptMessage.setContent(getInterestString());
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

                    // Receive tour
                    // TODO Now we receive a full list of artifact objects
                    // TODO We should not do that, have to contact the curatoragent for artifact details

                    reply = myAgent.receive(mt);

                    if (reply != null)
                    {
                        if (reply.getPerformative() == ACLMessage.INFORM)
                        {
                            // Tour received (list of artifacts)
                            System.out.println("Received tour from agent: " + reply.getSender().getName());
                            System.out.println("Number of interesting objects: " + bestTourNumberOfInterestingObjects);

                            ArrayList<Artifact> artifacts;

                            try
                            {
                                artifacts = (ArrayList<Artifact>) reply.getContentObject();
                            }
                            catch (UnreadableException ex)
                            {
                                System.err.println(ex.toString());
                                artifacts = new ArrayList<>();
                            }

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

    private class GetArtifactDetails extends Behaviour
    {
        private ArrayList<Artifact> artifacts;
        private ArrayList<Artifact> receivedArtifactsWithDetails;
        private int step;
        private int sentRequestCount;
        private int receivedResponseCount;
        private MessageTemplate mt;

        public GetArtifactDetails(ArrayList<Artifact> artifacts)
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

                    for (Artifact artifact : this.artifacts)
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

    private String getInterestString()
    {
        StringBuilder sb = new StringBuilder();

        for (String interest : interests)
            sb.append(interest + " ");

        return sb.toString().trim();
    }

    @Override
    public String toString()
    {
        return "ProfilerAgent{" +
                "age=" + age +
                ", occupation='" + occupation + '\'' +
                ", interests=" + interests +
                ", visitedItems=" + visitedItems +
                ", tourGuideAgent=" + tourGuideAgent +
                '}';
    }
}
