package DTOs;

import artifacts.Painting;

import java.io.Serializable;

public class BidRequestDTO implements Serializable
{
    public Painting painting;
    public int askingPrice;

    public BidRequestDTO(Painting painting, int askingPrice)
    {
        this.painting = painting;
        this.askingPrice = askingPrice;
    }

    @Override
    public String toString()
    {
        return "BidRequestDTO{" +
                "painting=" + painting +
                ", askingPrice=" + askingPrice +
                '}';
    }
}
