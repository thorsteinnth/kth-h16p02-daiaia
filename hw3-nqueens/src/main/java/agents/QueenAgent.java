package agents;

import jade.core.Agent;

public class QueenAgent extends Agent
{
    protected void setup()
    {
        System.out.println("QueenAgent " + getAID().getName() + " is ready.");
    }

    protected void takeDown()
    {
        System.out.println("QueenAgent " + getAID().getName() + " terminating.");
    }
}
