package artifacts;

public class Painting
{
    private String name;
    private String artist;
    private int centuryPainted;
    private SubjectMatter subjectMatter;
    private PaintingMedium medium;
    private int marketValue;

    public enum SubjectMatter
    {
        Portrait,
        Landscape,
        StillLife,
        Abstract,
        Religious
    }

    public enum PaintingMedium
    {
        Oil,
        Acrylic,
        Pastel,
        Watercolor,
        Fresco
    }

    public Painting(String name, String artist, int centuryPainted, SubjectMatter subjectMatter, PaintingMedium medium, int marketValue)
    {
        this.name = name;
        this.artist = artist;
        this.centuryPainted = centuryPainted;
        this.subjectMatter = subjectMatter;
        this.medium = medium;
        this.marketValue = marketValue;
    }

    public SubjectMatter getSubjectMatter()
    {
        return subjectMatter;
    }

    public PaintingMedium getMedium()
    {
        return medium;
    }

    public String getArtist()
    {
        return artist;
    }

    public String getName()
    {
        return name;
    }

    public int getCenturyPainted()
    {
        return centuryPainted;
    }

    public int getMarketValue()
    {
        return marketValue;
    }

    @Override
    public String toString()
    {
        return "Painting{" +
                "name='" + name + '\'' +
                ", artist='" + artist + '\'' +
                ", centuryPainted=" + centuryPainted +
                ", subjectMatter=" + subjectMatter +
                ", medium=" + medium +
                ", marketValue=" + marketValue +
                '}';
    }
}
