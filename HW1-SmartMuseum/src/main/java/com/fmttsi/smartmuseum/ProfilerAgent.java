import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

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

    //region Setup and takeDown

    protected void setup()
    {
        // TODO Get info from command line arguments
        this.age = 27;
        this.occupation = "Software engineer";
        this.interests = new ArrayList<>();
        this.interests.add("painting");
        this.interests.add("sculpture");

        this.visitedItems = new ArrayList<>();

        System.out.println("ProfilerAgent " + getAID().getName() + " is ready.");
    }

    protected void takeDown()
    {
        // Do necessary clean up here
        System.out.println("ProfilerAgent " + getAID().getName() + " terminating.");
    }

    //endregion

    //region Behaviours

    private class TourRequestPerformer extends Behaviour
    {
        private MessageTemplate mt; // The template to receive replies
        private int bestTourNumberOfInterestingObjects;
        private AID bestTourGuide;
        private int repliesCount;
        private int sentRequestCount;
        private int step = 0;

        public void action()
        {
            switch (step)
            {
                case 0:

                    // Send tour requests

                    System.out.println("Entered step 0");

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

                    System.out.println("Entered step 1");

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

                    System.out.println("Entered step 3");

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

                    System.out.println("Entered step 3");

                    // Receive tour

                    reply = myAgent.receive(mt);
                    if (reply != null)
                    {
                        if (reply.getPerformative() == ACLMessage.INFORM)
                        {
                            // Tour received
                            System.out.println("Received tour from agent: " + reply.getSender().getName());
                            System.out.println("Number of interesting objects: " + bestTourNumberOfInterestingObjects);
                            myAgent.doDelete(); // Don't terminate here ... have to talk to curator now
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
