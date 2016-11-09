import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;

public class HelloAgent extends Agent
{
    protected void setup()
    {
        //addBehaviour(new B1(this));

        addBehaviour(  // -------- Anonymous SimpleBehaviour

                new SimpleBehaviour( this )
                {
                    int n=0;

                    public void action()
                    {
                        System.out.println( "Hello World! My name is " +
                                myAgent.getLocalName() );
                        n++;
                    }

                    public boolean done() {  return n>=3;  }
                }
        );
    }
}

class B1 extends SimpleBehaviour
{
    private boolean finished = false;

    public B1(Agent a)
    {
        super(a);
    }

    public void action()
    {
        System.out.println("Hello world.");
        System.out.println("My name is " + myAgent.getLocalName());
    }

    public boolean done()
    {
        return this.finished;
    }
}
