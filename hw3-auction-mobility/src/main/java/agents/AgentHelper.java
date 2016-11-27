package agents;

import artifacts.Painting;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;

public class AgentHelper
{
    public static String getAclMessageDisplayString(ACLMessage message)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("Message from ");
        sb.append(message.getSender().getName());
        sb.append(" - [" + ACLMessage.getPerformative(message.getPerformative()) + "] ");
        sb.append("[" + message.getContent() + "]");

        return sb.toString();
    }

    public static ArrayList<Painting> generatePaintings()
    {
        ArrayList<Painting> paintings = new ArrayList<>();

        paintings.add(new Painting("Mona Lisa", "Leonardo da Vinci", 15, Painting.SubjectMatter.Portrait, Painting.PaintingMedium.Oil, 500));
        paintings.add(new Painting("The Scream", "Edvard Munch", 18, Painting.SubjectMatter.Abstract, Painting.PaintingMedium.Pastel, 400));
        paintings.add(new Painting("The Persistence of Memory", "Salvador Dalí", 19, Painting.SubjectMatter.Abstract, Painting.PaintingMedium.Oil, 400));
        paintings.add(new Painting("Wanderer above the Sea of Fog", "Caspar David Friedrich", 18, Painting.SubjectMatter.Landscape, Painting.PaintingMedium.Oil, 400));
        paintings.add(new Painting("The Starry Night", "Vincent van Gogh", 18, Painting.SubjectMatter.Abstract, Painting.PaintingMedium.Oil, 300));
        paintings.add(new Painting("Bouquet", "Jan Brueghel the Elder", 15, Painting.SubjectMatter.StillLife, Painting.PaintingMedium.Oil, 200));
        paintings.add(new Painting("The Creation of Adam", "Michelangelo", 15, Painting.SubjectMatter.Religious, Painting.PaintingMedium.Fresco, 600));
        paintings.add(new Painting("Jedburgh Abbey from the River", "Thomas Girtin", 17, Painting.SubjectMatter.Landscape, Painting.PaintingMedium.Watercolor, 200));
        paintings.add(new Painting("A Bigger Splash", "David Hockney", 19, Painting.SubjectMatter.Landscape, Painting.PaintingMedium.Acrylic, 100));

        return paintings;
    }

    public static ArrayList<String> getAllArtists()
    {
        ArrayList<String> artists = new ArrayList<>();

        ArrayList<Painting> paintings = generatePaintings();

        for (Painting painting : paintings)
        {
            if (!artists.contains(painting.getArtist()))
                artists.add(painting.getArtist());
        }

        return artists;
    }
}
