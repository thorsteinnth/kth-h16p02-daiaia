import java.io.Serializable;

public class Artifact implements Serializable
{
    private int id;
    private ArtifactType type;
    private String name;
    private String description;

    public enum ArtifactType
    {
        Painting,
        Sculpture,
        Building,
        Item,
        Other
    }

    public Artifact(int id, ArtifactType type, String name, String description)
    {
        this.id = id;
        this.type = type;
        this.name = name;
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

    public String getName()
    {
        return name;
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
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
