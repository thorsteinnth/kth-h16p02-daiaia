import java.io.Serializable;

/**
 * Class representing an artifact header.
 * The artifact header contains some information about an artifact,
 * but is not the entire artifact.
 * */
public class ArtifactHeader implements Serializable
{
    private int id;
    private String name;

    public ArtifactHeader(int id, String name)
    {
        this.id = id;
        this.name = name;
    }

    public int getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    @Override
    public String toString()
    {
        return "ArtifactHeader{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
