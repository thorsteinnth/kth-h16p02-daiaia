
public class Artifact
{
    private int id;
    private ArtifactType type;
    private String description;

    public enum ArtifactType
    {
        Painting,
        Sculpture,
        Building,
        Item,
        Other
    }

    public Artifact(int id, ArtifactType type, String description)
    {
        this.id = id;
        this.type = type;
        this.description = description;
    }

    public int getId()
    {
        return id;
    }

    public ArtifactType getType()
    {
        return type;
    }

    public String getDescription()
    {
        return description;
    }

    @Override
    public String toString()
    {
        return "Artifact{" +
                "id=" + id +
                ", type=" + type +
                ", description='" + description + '\'' +
                '}';
    }
}
