import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// CuratorAgent monitors the gallery/museum
public class CuratorAgent extends Agent
{
    private ArrayList<Artifact> artifacts;

    protected void setup()
    {
        this.artifacts = generateArtifacts();

        this.addBehaviour(new ArtifactsForInterestServer());
        this.addBehaviour(new ArtifactDetailsServer());

        System.out.println("CuratorAgent " + getAID().getName() + " is ready.");
    }

    protected void takeDown()
    {
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

                String interests = msg.getContent();
                ArrayList<Artifact> artifacts = getArtifactsForInterests(interests);

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

    private class ArtifactDetailsServer extends CyclicBehaviour
    {
        public void action()
        {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchConversationId("get-artifact-details")
            );

            ACLMessage msg = myAgent.receive(mt);

            if (msg != null)
            {
                ACLMessage reply = msg.createReply();

                String artifactId = msg.getContent();

                try
                {
                    Artifact artifact = getArtifactById(Integer.parseInt(artifactId));
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContentObject(artifact);
                }
                catch (ArtifactNotFoundException|NumberFormatException|IOException ex)
                {
                    System.err.println("CuratorAgent.ArtifactDetailsServer.action(): " + ex.toString());
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

    private Artifact getArtifactById(int id) throws ArtifactNotFoundException
    {
        List<Artifact> foundArtifacts =
                this.artifacts
                        .stream()
                        .filter(a -> a.getId() == id)
                        .collect(Collectors.toList());

        if (foundArtifacts.size() == 0)
            throw new ArtifactNotFoundException();
        else if (foundArtifacts.size() > 1)
            throw new IllegalStateException("More than one artifact found with ID: " + id);
        else
            return foundArtifacts.get(0);
    }

    private ArrayList<Artifact> getArtifactsForInterests(String interests)
    {
        // Interests string is separated with spaces
        // e.g. "paintings sculptures buildings"
        String[] splitInterests = interests.split("\\s+");

        ArrayList<Artifact.ArtifactType> artifactTypes = mapInterestToArtifactTypes(splitInterests);

        ArrayList<Artifact> foundArtifacts =
                this.artifacts
                        .stream()
                        .filter(a -> artifactTypes.contains(a.getType()))
                        .collect(Collectors.toCollection(ArrayList::new));

        return foundArtifacts;
    }

    private ArrayList<Artifact.ArtifactType> mapInterestToArtifactTypes(String[] interests)
    {
        ArrayList<Artifact.ArtifactType> artifactTypes = new ArrayList<>();

        for (String interest : interests)
        {
            switch (interest)
            {
                case "paintings":
                    if (!artifactTypes.contains(Artifact.ArtifactType.Painting))
                        artifactTypes.add(Artifact.ArtifactType.Painting);
                    break;
                case "sculptures":
                    if (!artifactTypes.contains(Artifact.ArtifactType.Sculpture))
                        artifactTypes.add(Artifact.ArtifactType.Sculpture);
                    break;
                case "buildings":
                    if (!artifactTypes.contains(Artifact.ArtifactType.Building))
                        artifactTypes.add(Artifact.ArtifactType.Building);
                    break;
                case "items":
                    if (!artifactTypes.contains(Artifact.ArtifactType.Item))
                        artifactTypes.add(Artifact.ArtifactType.Item);
                    break;
                case "other":
                    if (!artifactTypes.contains(Artifact.ArtifactType.Other))
                        artifactTypes.add(Artifact.ArtifactType.Other);
                    break;
                default:
                    break;
            }
        }

        return artifactTypes;
    }

    private ArrayList<Artifact> generateArtifacts()
    {
        ArrayList<Artifact> artifacts = new ArrayList<>();

        artifacts.add(new Artifact(1, Artifact.ArtifactType.Building, "Cool building"));
        artifacts.add(new Artifact(2, Artifact.ArtifactType.Item, "Cool item"));
        artifacts.add(new Artifact(3, Artifact.ArtifactType.Other, "Cool something"));
        artifacts.add(new Artifact(4, Artifact.ArtifactType.Painting, "Cool painting"));
        artifacts.add(new Artifact(5, Artifact.ArtifactType.Sculpture, "Cool sculpture"));
        artifacts.add(new Artifact(6, Artifact.ArtifactType.Sculpture, "Cool sculpture2"));

        return artifacts;
    }
}
