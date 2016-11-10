import jade.core.Agent;

public class CuratorAgent extends Agent
{
    protected void setup()
    {
        System.out.println("CuratorAgent " + getAID().getName() + " is ready.");
    }

    protected void takeDown()
    {
        //Do necessary clean up here
        System.out.println("CuratorAgent " + getAID().getName() + " terminating.");
    }
}
