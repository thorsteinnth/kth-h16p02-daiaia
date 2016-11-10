import jade.core.Agent;

public class TourGuideAgent extends Agent
{
    protected void setup()
    {
        System.out.println("TourGuideAgent " + getAID().getName() + " is ready.");
    }

    protected void takeDown()
    {
        //Do necessary clean up here
        System.out.println("TourGuideAgent " + getAID().getName() + " terminating.");
    }
}
