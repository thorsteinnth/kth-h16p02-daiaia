import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.IOException;
import java.util.ArrayList;

public class CuratorAgent extends Agent
{
    protected void setup()
    {
        System.out.println("CuratorAgent " + getAID().getName() + " is ready.");

        this.addBehaviour(new ArtifactsForInterestServer());
    }

    protected void takeDown()
    {
        //Do necessary clean up here
        System.out.println("CuratorAgent " + getAID().getName() + " terminating.");
    }

    //region Behaviours

    private class ArtifactsForInterestServer extends CyclicBehaviour
    {
        public void action()
        {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchConversationId("get-artifacts-for-interest")
            );

            ACLMessage msg = myAgent.receive(mt);

            if (msg != null)
            {
                // Request message received, handle it

                ACLMessage reply = msg.createReply();

                String interest = msg.getContent();
                ArrayList<Artifact> artifacts = getArtifactsForInterest(interest);

                try
                {
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContentObject(artifacts);
                }
                catch (IOException ex)
                {
                    System.err.println("CuratorAgent.ArtifactsForInterestServer.action(): " + ex.toString());
                    reply.setPerformative(ACLMessage.REFUSE);
                    reply.setContent("Error");
                }

                myAgent.send(reply);
            }
            else
            {
                block();
            }
        }
    }

    //endregion

    private ArrayList<Artifact> getArtifactsForInterest(String interest)
    {
        // TODO Different tour guide agents should get different artifacts
        // TODO Should only return artifacts that are valid for this interest

        ArrayList<Artifact> selectedArtifacts = new ArrayList<>();

        selectedArtifacts.add(new Artifact(1, Artifact.ArtifactType.Building, "Cool building"));
        selectedArtifacts.add(new Artifact(2, Artifact.ArtifactType.Item, "Cool item"));
        selectedArtifacts.add(new Artifact(3, Artifact.ArtifactType.Other, "Cool something"));
        selectedArtifacts.add(new Artifact(4, Artifact.ArtifactType.Painting, "Cool painting"));
        selectedArtifacts.add(new Artifact(5, Artifact.ArtifactType.Sculpture, "Cool sculpture"));

        return selectedArtifacts;
    }
}
