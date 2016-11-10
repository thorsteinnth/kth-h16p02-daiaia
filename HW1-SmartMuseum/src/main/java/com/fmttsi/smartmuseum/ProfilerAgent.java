import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;

import java.awt.*;

public class ProfilerAgent extends Agent
{
    private int age;
    private String occupation;
    private String interests;
    //private List<Artifact> visitedItems;

    protected void setup()
    {
        System.out.println("ProfilerAgent " + getAID().getName() + " is ready.");


    }

    protected void takeDown()
    {
        //Do necessary clean up here
        System.out.println("ProfilerAgent " + getAID().getName() + " terminating.");
    }
}
